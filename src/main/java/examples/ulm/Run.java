package examples.ulm;

import run.ExtractNetwork;
import run.GeneratePopulationAndRunScenario;
import run.RunOTP;

public class Run {
    public static final String BASEDIR = "/Users/gleich/Documents/otp-matsim-praes/Ulm/";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_SCHEDULE_FILE = BASEDIR + "extracted-transitschedule_withoutCreatePseudoNetwork.xml";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_VEHICLE_FILE = BASEDIR + "extracted-transitvehicles_withoutCreatePseudoNetwork.xml";
    /** Created by ExtractNetwork*/
    public static final String NETWORK_FILE = BASEDIR + "extracted-network_withoutCreatePseudoNetwork.xml";
    
    /**
     * OTP_GRAPH_DIR should include Openstreetmap data in a file named *.osm or *.pbf
     * and a zip file with GTFS data
     */
    public static final String OTP_GRAPH_DIR = BASEDIR + "otp-graph/";
    public static final String POPULATION_FILE = BASEDIR + "population.xml";
    public static final String OUTPUT_DIR = BASEDIR + "testOneIteration_withoutCreatePseudoNetwork";

    // Set this as you like - scenario is created from scratch.
    public static final String TARGET_SCENARIO_COORDINATE_SYSTEM = "EPSG:3857";
    
    public static final String TIME_ZONE = "Europe/Berlin";
    public static final String DATE = "2014-02-14"; // Friday
    public static final int SCHEDULE_END_TIME_ON_FOLLOWING_DATE = 4*60*60;
    
    public static final boolean USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK = true;
    public static final int POPULATION_SIZE = 10;
    public static final int LAST_ITERATION = 0;
    
    public static void main(String[] args){
    	RunOTP.runGraphBuilder(OTP_GRAPH_DIR);
    	RunOTP.runGraphVisualizer(OTP_GRAPH_DIR);
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
