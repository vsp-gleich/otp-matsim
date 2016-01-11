package run;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.VehicleReaderV1;

import core.OTPTripRouterFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates an example population with home->work->home trips. All home and
 * work activities are located in the vicinity of TransitStopFacilities which
 * are chosen by random. The outcome population is simulated a single iteration.
 * 
 * @author gleich
 *
 */
public class GeneratePopulationAndRunScenario {

    private Scenario scenario;
    private ArrayList<TransitStopFacility> facs;
	private String otpGraphDir;
	private String targetScenarioCoordinateSystem;
	private String date;
	private	String timeZone;
	private boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork;
	private String networkFile;
	private String transitScheduleFile;
	private String transitVehicleFile;
	private String populationFile;
	private String outputDir;
	private int populationSize;
	private int lastIteration;

    public GeneratePopulationAndRunScenario(String otpGraphDir, String targetScenarioCoordinateSystem, String date, 
			String timeZone, boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork,
			String networkFile, String transitScheduleFile, String transitVehicleFile, 
			String populationFile, String outputDir, int populationSize, int lastIteration){
		this.otpGraphDir = otpGraphDir;
		this.targetScenarioCoordinateSystem = targetScenarioCoordinateSystem;
		this.date = date;
		this.timeZone = timeZone;
		this.useCreatePseudoNetworkInsteadOfOtpPtNetwork = useCreatePseudoNetworkInsteadOfOtpPtNetwork;
		this.networkFile = networkFile;
		this.transitScheduleFile = transitScheduleFile;
		this.transitVehicleFile = transitVehicleFile;
		this.populationFile = populationFile;
		this.outputDir = outputDir;
		this.populationSize = populationSize;
		this.lastIteration = lastIteration;
    }
    
	public void run() {
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		config.transit().setTransitScheduleFile(transitScheduleFile);
		config.transit().setVehiclesFile(transitVehicleFile);
		config.network().setInputFile(networkFile);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setMobsim("qsim");
		config.qsim().setSnapshotStyle(QSimConfigGroup.SnapshotStyle.queue);
		config.qsim().setSnapshotPeriod(1);
		config.qsim().setRemoveStuckVehicles(false);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(1.0);
		config.controler().setOutputDirectory(outputDir);
		
		config.controler().setWriteEventsInterval(1);		
		config.controler().setLastIteration(lastIteration);
		config.controler().setWritePlansInterval(1);
		config.qsim().setEndTime(30*60*60);
		
		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(12*60*60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8*60*60);
		config.planCalcScore().addActivityParams(work);
		config.planCalcScore().setWriteExperiencedPlans(true);
		config.strategy().setMaxAgentPlanMemorySize(5);
		
		StrategySettings reRoute = new StrategySettings(Id.create("1", StrategySettings.class));
		reRoute.setStrategyName("ReRoute");
		reRoute.setWeight(0.2);
		reRoute.setDisableAfter(40);
		StrategySettings expBeta = new StrategySettings(Id.create("2", StrategySettings.class));
		expBeta.setStrategyName("ChangeExpBeta");
		expBeta.setWeight(0.6);
		
		config.strategy().addStrategySettings(expBeta);
		config.strategy().addStrategySettings(reRoute);

        scenario = ScenarioUtils.createScenario(config);

		new MatsimNetworkReader(scenario.getNetwork()).readFile(config.network().getInputFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
		new VehicleReaderV1(scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());
		
        facs = new ArrayList<>(scenario.getTransitSchedule().getFacilities().values());
        System.out.println("Scenario has " + scenario.getNetwork().getLinks().size() + " links.");

		final OTPTripRouterFactory trf = new OTPTripRouterFactory(scenario.getTransitSchedule(),
				scenario.getNetwork(), TransformationFactory.getCoordinateTransformation( 
						targetScenarioCoordinateSystem, TransformationFactory.WGS84),
				date,
				timeZone,
                otpGraphDir,
                true, 3, 
                useCreatePseudoNetworkInsteadOfOtpPtNetwork);
        
        generatePopulation();
        
        new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).writeV5(populationFile);

		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new AbstractModule() {

			@Override
			public void install() {
				bind(TransitRouter.class).to(DummyTransitRouter.class);
			}
			
		});
		controler.setTripRouterFactory(trf);

		controler.run();

	}
	
	static class DummyTransitRouter implements TransitRouter {
		@Override
		public List<Leg> calcRoute(Coord fromCoord, Coord toCoord, double departureTime, Person person) {
			throw new RuntimeException();
		}
		
	}

	private void generatePopulation() {
		for (int i=0; i<populationSize; ++i) {
			Coord source = randomCoord();
			Coord sink = randomCoord();
			Person person = scenario.getPopulation().getFactory().createPerson(Id.create(Integer.toString(i), Person.class));
			Plan plan = scenario.getPopulation().getFactory().createPlan();
			plan.addActivity(createHomeStart(source));
			List<Leg> homeWork = createLeg();
			for (Leg leg : homeWork) {
				plan.addLeg(leg);
			}
			plan.addActivity(createWork(sink));
			List<Leg> workHome = createLeg();
			for (Leg leg : workHome) {
				plan.addLeg(leg);
			}
			plan.addActivity(createHomeEnd(source));
			person.addPlan(plan);
			scenario.getPopulation().addPerson(person);
		}
	}

	private List<Leg> createLeg() {
		Leg leg = scenario.getPopulation().getFactory().createLeg(TransportMode.pt);
		return Arrays.asList(leg);
	}

    private Coord randomCoord() {
        int nFac = (int) (facs.size() * Math.random());
        Coord coordsOfATransitStop = facs.get(nFac).getCoord();
        coordsOfATransitStop.setXY(coordsOfATransitStop.getX() + Math.random() * 1000 - 500, coordsOfATransitStop.getY() + Math.random() * 1000 - 500);
        // People live within 1 km of transit stops. :-)
		return coordsOfATransitStop;
    }

	private Activity createWork(Coord workLocation) {
		Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("work", workLocation);
		activity.setEndTime(17*60*60);
		return activity;
	}

	private Activity createHomeStart(Coord homeLocation) {
		Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("home", homeLocation);
		activity.setEndTime(9*60*60);
		return activity;
	}
	
	private Activity createHomeEnd(Coord homeLocation) {
		Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("home", homeLocation);
		activity.setEndTime(Double.POSITIVE_INFINITY);
		return activity;
	}

}
