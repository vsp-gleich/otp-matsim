package examples.vbb;

import run.RunOTP;
import run.ExtractNetwork;
import run.GeneratePopulationAndRunScenario;

/**
 * TODO: Vbb data: 3 vehicles in endless simulation:
 * continuous bus trips are represented in independent segments in vbb gtfs data, e.g. bus 245 from Zoo to Nordbahnhof is cut at Lesser-Ury-Weg, maybe because the headsign changes, otp webserver router knows that and delivers "stay onboard" message -> not only circular lines are splitted; in gtfs transfer.txt 120s min transfer but column stop sequence is continuous; does not work at Grumbkowstr. (Berlin) (VBB:9131002) where circular bus line 250 continues its trip, otp recommends to alight, wait 14 min and board the following bus
 * data has transfer times in transfer.txt
 * 
 * -for useCreatePseudoNetwork = false: 2015-08-11 19:13:05,403  WARN QNode:230 The link id 947832 is not available in the simulation network, but vehicle VBB_110063_-1 plans to travel on that link from link VBB_9210003
 */
public class Run {
	/**
	 * BASEDIR should include Openstreetmap data in a file named *.osm or *.pbf (here http://download.geofabrik.de/europe/germany/berlin.html)
	 * and a zip file with GTFS data (here http://daten.berlin.de/datensaetze/vbb-fahrplandaten-juni-2015-bis-dezember-2015)
	 */
    public static final String BASEDIR = "output/VBB/";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_SCHEDULE_FILE = BASEDIR + "extracted-transitschedule_withoutCreatePseudoNetwork.xml";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_VEHICLE_FILE = BASEDIR + "extracted-transitvehicles_withoutCreatePseudoNetwork.xml";
    /** Created by ExtractNetwork*/
    public static final String NETWORK_FILE = BASEDIR + "extracted-network_withoutCreatePseudoNetwork.xml";
    
    public static final String OTP_GRAPH_DIR = BASEDIR + "otp-graph/";
    public static final String POPULATION_FILE = BASEDIR + "population.xml";
    public static final String OUTPUT_DIR = BASEDIR + "testOneIteration_withoutCreatePseudoNetwork";

    // Set this as you like - scenario is created from scratch.
    public static final String TARGET_SCENARIO_COORDINATE_SYSTEM = "EPSG:3857";
    
    public static final String TIME_ZONE = "Europe/Berlin";
    public static final String DATE = "2015-08-27"; // Thursday
    public static final int SCHEDULE_END_TIME_ON_FOLLOWING_DATE = 4*60*60;
    
    public static final boolean USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK = false;
    public static final int POPULATION_SIZE = 10;
    public static final int LAST_ITERATION = 0;
    
    public static void main(String[] args){
//    	RunOTP.runGraphBuilder(OTP_GRAPH_DIR);
//    	RunOTP.runGraphVisualizer(OTP_GRAPH_DIR);
    	runExtractNetwork();
    	runGeneratePopulationAndScenario();
    }

	private static void runExtractNetwork() {
		ExtractNetwork extractNetwork = new ExtractNetwork(OTP_GRAPH_DIR, TARGET_SCENARIO_COORDINATE_SYSTEM, DATE, TIME_ZONE,
                		SCHEDULE_END_TIME_ON_FOLLOWING_DATE, USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK, 
                		NETWORK_FILE, TRANSIT_SCHEDULE_FILE, TRANSIT_VEHICLE_FILE);
		extractNetwork.run();
	}
	
	private static void runGeneratePopulationAndScenario() {
		GeneratePopulationAndRunScenario generatePopulationAndScenario = new GeneratePopulationAndRunScenario(
				OTP_GRAPH_DIR, TARGET_SCENARIO_COORDINATE_SYSTEM, DATE, TIME_ZONE, USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK, 
        		NETWORK_FILE, TRANSIT_SCHEDULE_FILE, TRANSIT_VEHICLE_FILE, POPULATION_FILE, OUTPUT_DIR, POPULATION_SIZE, LAST_ITERATION);
		generatePopulationAndScenario.run();		
	}
}
