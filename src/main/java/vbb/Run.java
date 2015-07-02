package vbb;


import java.io.File;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.population.PopulationReaderMatsimV5;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.vehicles.VehicleReaderV1;
import otp.OTPTripRouterFactory;

public class Run {
	
	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		config.scenario().setUseVehicles(true);
		config.scenario().setUseTransit(true);
		config.controler().setMobsim("qsim");
		config.controler().setLastIteration(0);
		config.qsim().setSnapshotStyle(SnapshotStyle.queue);
		config.qsim().setSnapshotPeriod(1);
		config.qsim().setRemoveStuckVehicles(false);
//		ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.GROUP_NAME, OTFVisConfigGroup.class).setColoringScheme(ColoringScheme.gtfs);
//		ConfigUtils.addOrGetModule(config, OTFVisConfigGroup.GROUP_NAME, OTFVisConfigGroup.class).setDrawTransitFacilities(false);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(1.0);
		
		config.network().setInputFile("/Users/zilske/gtfs-bvg/network.xml");
		config.plans().setInputFile("/Users/zilske/gtfs-bvg/population.xml");
		config.transit().setTransitScheduleFile("/Users/zilske/gtfs-bvg/transit-schedule.xml");
		config.transit().setVehiclesFile("/Users/zilske/gtfs-bvg/transit-vehicles.xml");
		
		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(12*60*60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8*60*60);
		config.planCalcScore().addActivityParams(work);
		config.planCalcScore().setWriteExperiencedPlans(true);
		
		Scenario scenario = ScenarioUtils.createScenario(config);
		
		new MatsimNetworkReader(scenario).readFile(config.network().getInputFile());
		new VehicleReaderV1(((ScenarioImpl) scenario).getVehicles()).readFile(config.transit().getVehiclesFile());
		new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
		new PopulationReaderMatsimV5(scenario).readFile(config.plans().getInputFile());
		
		
		Controler controler = new Controler(scenario);
		org.matsim.core.utils.io.IOUtils.deleteDirectory(new File("./output"));
//		controler.setOverwriteFiles(true);
		controler.addOverridingModule(new AbstractModule() {

			@Override
			public void install() {
				bind(TransitRouter.class).to(DummyTransitRouter.class);
			}
			
		});
		controler.setTripRouterFactory(new OTPTripRouterFactory(scenario.getTransitSchedule(), 
				scenario.getNetwork(), new IdentityTransformation(), "2013-08-24", "Europe/Berlin", 
				"/Users/michaelzilske/gtfs-ulm/Graph.obj", false, 1));
		
		controler.run();


		//		EventsManager events = EventsUtils.createEventsManager();
		//		QSim qSim = (QSim) new QSimFactory().createMobsim(scenario, events);
		//		OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(scenario.getConfig(), scenario, events, qSim);
		//		OTFClientLive.run(scenario.getConfig(), server);
		//		qSim.run();


	}

	static class DummyTransitRouter implements TransitRouter {
		@Override
		public List<Leg> calcRoute(Coord fromCoord, Coord toCoord, double departureTime, Person person) {
			throw new RuntimeException();
		}
		
	}
	
}
