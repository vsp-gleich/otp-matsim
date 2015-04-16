package ulm;

import java.io.File;
import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.population.PopulationReaderMatsimV5;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.vehicles.VehicleReaderV1;

import otp.OTPTripRouterFactory;

public class RunScenarioBasedOnOtpOutput {
	
	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		config.scenario().setUseVehicles(true);
		config.scenario().setUseTransit(true);
		config.controler().setMobsim("qsim");
		config.controler().setLastIteration(0);
		config.qsim().setSnapshotStyle("queue");
		config.qsim().setSnapshotPeriod(1);
		config.qsim().setRemoveStuckVehicles(false);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(1.0);
		
		config.network().setInputFile(Consts.GTFS2MATSIM_NETWORK_FILE);
		config.transit().setTransitScheduleFile(Consts.GTFS2MATSIM_TRANSIT_SCHEDULE_FILE);
		config.transit().setVehiclesFile(Consts.GTFS2MATSIM_TRANSIT_VEHICLE_FILE);
		config.plans().setInputFile(Consts.POPULATION_FILE);
		config.controler().setOutputDirectory(Consts.BASEDIR + "testOneIteration");
		
		/*
		 *  The modes "transit_walk" and "teleport" are used by OTPRoutingModule, but are
		 *  not included in the default modes in the March 2015 version of PlansCalcRouteConfigGroup.
		 *  Adding these modes is a temporary solution. Maybe OTPRoutingModule should only
		 *  use default modes instead.
		 *  addModeRoutingParams() removes the default modes, so they have to be reinserted
		 */
		Map<String, ModeRoutingParams> defaultModes = config.plansCalcRoute().getModeRoutingParams();
		final ModeRoutingParams transit_walk = new ModeRoutingParams( TransportMode.transit_walk ) ;
		transit_walk.setTeleportedModeSpeed( 3.0 / 3.6 ); // 3.0 km/h --> m/s
		config.plansCalcRoute().addModeRoutingParams( transit_walk );
		final ModeRoutingParams teleport = new ModeRoutingParams( "teleport" ) ;
		teleport.setTeleportedModeSpeed( 3.0 / 3.6 ); // 3.0 km/h --> m/s
		config.plansCalcRoute().addModeRoutingParams( teleport );
		for(ModeRoutingParams params: defaultModes.values()){
			config.plansCalcRoute().addModeRoutingParams(params);
		}
		
		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(12*60*60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8*60*60);
		config.planCalcScore().addActivityParams(work);
		config.planCalcScore().setWriteExperiencedPlans(true);
		
		Scenario scenario = ScenarioUtils.createScenario(config);
		
		new MatsimNetworkReader(scenario).readFile(config.network().getInputFile());
		new VehicleReaderV1(((ScenarioImpl) scenario).getTransitVehicles()).readFile(config.transit().getVehiclesFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
		new PopulationReaderMatsimV5(scenario).readFile(config.plans().getInputFile());
		
		
		Controler controler = new Controler(scenario);
		org.matsim.core.utils.io.IOUtils.deleteDirectory(new File(Consts.BASEDIR + "testOneIteration"));
//		controler.setOverwriteFiles(true);
		controler.setTransitRouterFactory(new TransitRouterFactory() {

			@Override
			public TransitRouter createTransitRouter() {
				throw new RuntimeException();
			}
			
		});
		controler.setTripRouterFactory(new OTPTripRouterFactory(scenario.getTransitSchedule(),
                TransformationFactory.getCoordinateTransformation(Consts.TARGET_SCENARIO_COORDINATE_SYSTEM, TransformationFactory.WGS84),
                "2014-02-10",
                Consts.OTP_GRAPH_FILE));
		
		config.controler().setWriteEventsInterval(1);		
		config.controler().setLastIteration(10);

		controler.run();

	}

}
