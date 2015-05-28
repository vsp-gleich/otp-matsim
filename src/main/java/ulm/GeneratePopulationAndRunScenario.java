package ulm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Provider;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import otp.OTPTripRouterFactory;

/**
 * 
 * @author gleich
 *
 */
public class GeneratePopulationAndRunScenario {

    private Scenario scenario;
    private ArrayList<TransitStopFacility> facs;

    public static void main(String[] args) throws IOException, ClassNotFoundException {
		new GeneratePopulationAndRunScenario().run();
	}

	private void run() {
		Config config = ConfigUtils.createConfig();
		config.scenario().setUseVehicles(true);
		config.scenario().setUseTransit(true);
		config.transit().setTransitScheduleFile(Consts.TRANSIT_SCHEDULE_FILE);
		config.network().setInputFile(Consts.NETWORK_FILE);
		config.vehicles().setVehiclesFile(Consts.TRANSIT_VEHICLE_FILE);
		
		config.controler().setMobsim("qsim");
		config.controler().setLastIteration(0);
		config.qsim().setSnapshotStyle("queue");
		config.qsim().setSnapshotPeriod(1);
		config.qsim().setRemoveStuckVehicles(false);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(1.0);
		config.controler().setOutputDirectory(Consts.BASEDIR + "testOneIteration");
		
		config.controler().setWriteEventsInterval(1);		
		config.controler().setLastIteration(0);
		config.controler().setWritePlansInterval(1);
		
		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(12*60*60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8*60*60);
		config.planCalcScore().addActivityParams(work);
		config.planCalcScore().setWriteExperiencedPlans(true);

		
//		StrategySettings stratSets = new StrategySettings(Id.create("1", StrategySettings.class));
//		stratSets.setStrategyName("ReRoute");
//		stratSets.setWeight(0.2);
//		stratSets.setDisableAfter(8);
		StrategySettings expBeta = new StrategySettings(Id.create("2", StrategySettings.class));
		expBeta.setStrategyName("ChangeExpBeta");
		expBeta.setWeight(0.6);
		
		config.strategy().addStrategySettings(expBeta);
//		config.strategy().addStrategySettings(stratSets);

        scenario = ScenarioUtils.createScenario(config);

		new MatsimNetworkReader(scenario).readFile(config.network().getInputFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
		// new VehicleReaderV1(((ScenarioImpl) scenario).getTransitVehicles()).readFile(config.transit().getVehiclesFile());
		
        facs = new ArrayList<>(scenario.getTransitSchedule().getFacilities().values());
        System.out.println("Scenario has " + scenario.getNetwork().getLinks().size() + " links.");

		final OTPTripRouterFactory trf = new OTPTripRouterFactory(scenario.getTransitSchedule(),
				scenario.getNetwork(), TransformationFactory.getCoordinateTransformation( 
						Consts.TARGET_SCENARIO_COORDINATE_SYSTEM, TransformationFactory.WGS84),
                "2014-02-10",
                Consts.OTP_GRAPH_FILE);
        
        generatePopulation();
        
        new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).writeV5("output/population_vor_Simulation.xml");

		Controler controler = new Controler(scenario);
		org.matsim.core.utils.io.IOUtils.deleteDirectory(new File(Consts.BASEDIR + "testOneIteration"));
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
		for (int i=0; i<1; ++i) {
//			Coord source = randomCoord();
//			Coord sink = randomCoord();
			// Walk only legs
//			Coord source = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, Consts.TARGET_SCENARIO_COORDINATE_SYSTEM).transform(new CoordImpl(10.0285, 48.4359));
//			Coord sink =TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, Consts.TARGET_SCENARIO_COORDINATE_SYSTEM).transform(new CoordImpl(10.0278, 48.4357));
			// walk+pt legs
			Coord source = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, Consts.TARGET_SCENARIO_COORDINATE_SYSTEM).transform(new CoordImpl(10.0310, 48.4339));
			Coord sink =TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, Consts.TARGET_SCENARIO_COORDINATE_SYSTEM).transform(new CoordImpl(10.0026, 48.4190));
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
