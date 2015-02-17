package ulm;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.RoutingContext;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.ParallelPersonAlgorithmRunner;
import org.matsim.population.algorithms.PersonPrepareForSim;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import otp.OTPTripRouterFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * 
 * @author mzilske
 * @author gleich
 *
 */
public class GenerateAndRoutePopulation {

    private Scenario scenario;
    private ArrayList<TransitStopFacility> facs;

    public static void main(String[] args) throws IOException, ClassNotFoundException {
		new GenerateAndRoutePopulation().run();
	}

	private void run() {
		Config config = ConfigUtils.createConfig();
		config.scenario().setUseVehicles(true);
		config.scenario().setUseTransit(true);
		config.transit().setTransitScheduleFile(Consts.TRANSIT_SCHEDULE_FILE);
		config.network().setInputFile(Consts.DUMMY_NETWORK_FILE);
        scenario = ScenarioUtils.createScenario(config);

		new MatsimNetworkReader(scenario).readFile(config.network().getInputFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
        facs = new ArrayList<>(scenario.getTransitSchedule().getFacilities().values());
        System.out.println("Scenario has " + scenario.getNetwork().getLinks().size() + " links.");

        for (int i=0; i<100; ++i) {
			Coord source = randomCoord();
			Coord sink = randomCoord();
			Person person = scenario.getPopulation().getFactory().createPerson(Id.create(Integer.toString(i), Person.class));
			Plan plan = scenario.getPopulation().getFactory().createPlan();
			plan.addActivity(createHome(source));
			List<Leg> homeWork = createLeg();
			for (Leg leg : homeWork) {
				plan.addLeg(leg);
			}
			plan.addActivity(createWork(sink));
			List<Leg> workHome = createLeg();
			for (Leg leg : workHome) {
				plan.addLeg(leg);
			}
			plan.addActivity(createHome(source));
			person.addPlan(plan);
			scenario.getPopulation().addPerson(person);
		}

		final OTPTripRouterFactory trf = new OTPTripRouterFactory(scenario.getTransitSchedule(),
                TransformationFactory.getCoordinateTransformation(Consts.TARGET_SCENARIO_COORDINATE_SYSTEM, TransformationFactory.WGS84),
                "2014-05-01",
                "/Users/michaelzilske/gtfs-ulm/Graph.obj");

		// make sure all routes are calculated.
		ParallelPersonAlgorithmRunner.run(scenario.getPopulation(), config.global().getNumberOfThreads(),
				new ParallelPersonAlgorithmRunner.PersonAlgorithmProvider() {
			@Override
			public AbstractPersonAlgorithm getPersonAlgorithm() {
				return new PersonPrepareForSim(new PlanRouter(
						trf.instantiateAndConfigureTripRouter(new RoutingContext() {

							@Override
							public TravelDisutility getTravelDisutility() {
								// TODO Auto-generated method stub
								return null;
							}

							@Override
							public TravelTime getTravelTime() {
								// TODO Auto-generated method stub
								return null;
							}
							
						}),
						scenario.getActivityFacilities()), scenario);
			}
		});

		new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).writeV5("output/population.xml");

	}

	private List<Leg> createLeg() {
		Leg leg = scenario.getPopulation().getFactory().createLeg(TransportMode.pt);
		return Arrays.asList(leg);
	}

    private Coord randomCoord() {
        int nFac = (int) (facs.size() * Math.random());
        return facs.get(nFac).getCoord();
    }

	private Activity createWork(Coord workLocation) {
		Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("work", workLocation);
		activity.setEndTime(17*60*60);
        ((ActivityImpl) activity).setLinkId(((NetworkImpl) scenario.getNetwork()).getNearestLinkExactly(workLocation).getId());
		return activity;
	}

	private Activity createHome(Coord homeLocation) {
		Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("home", homeLocation);
		activity.setEndTime(9*60*60);
        Link link = ((NetworkImpl) scenario.getNetwork()).getNearestLinkExactly(homeLocation);
		((ActivityImpl) activity).setLinkId(link.getId());
		return activity;
	}

}
