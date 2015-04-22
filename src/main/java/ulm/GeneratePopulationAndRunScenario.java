package ulm;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.VehicleReaderV1;

import otp.OTPTripRouterFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                TransformationFactory.getCoordinateTransformation(Consts.TARGET_SCENARIO_COORDINATE_SYSTEM, TransformationFactory.WGS84),
                "2014-02-10",
                Consts.OTP_GRAPH_FILE);
        
        generatePopulation();

		Controler controler = new Controler(scenario);
		org.matsim.core.utils.io.IOUtils.deleteDirectory(new File(Consts.BASEDIR + "testOneIteration"));
		controler.setTransitRouterFactory(new TransitRouterFactory() {

			@Override
			public TransitRouter createTransitRouter() {
				throw new RuntimeException();
			}
			
		});
		controler.setTripRouterFactory(trf);

		controler.run();

	}

	private void generatePopulation() {
		for (int i=0; i<10; ++i) {
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
