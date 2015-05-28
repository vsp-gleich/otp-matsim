package ulm;

public class Consts {
	/**
	 * BASEDIR should include Openstreetmap data in a file named *.osm or *.pbf
	 * and a zip file with GTFS data
	 */
    public static final String BASEDIR = "output/";
    /** Created by ExtractNetwork, does not contain routes */
    public static final String TRANSIT_SCHEDULE_FILE = "output/extracted-transitschedule.xml";
    /** Created by ExtractNetwork */
    public static final String DUMMY_NETWORK_FILE = "output/extracted-dummy-pt-network.xml";
    /** Created by ExtractNetwork */
    public static final String NETWORK_FILE = "output/extracted-network.xml";
    public static final String OTP_GRAPH_FILE = "output/Graph.obj";
    public static final String POPULATION_FILE = "output/population.xml";

    // Set this as you like - scenario is created from scratch.
    public static final String TARGET_SCENARIO_COORDINATE_SYSTEM = "EPSG:3857";
}
