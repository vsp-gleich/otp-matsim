package ulm;

public class Consts {
	/**
	 * BASEDIR should include Openstreetmap data in a file named *.osm or *.pbf
	 * and a zip file with GTFS data
	 */
    public static final String BASEDIR = "output/";
    /** Created by ExtractNetwork, does not contain routes */
    public static final String TRANSIT_SCHEDULE_FILE = "output/extracted-transitschedule.xml";
    /** Created with playground.mzilske.gtfs.GtfsConverter, contains routes */
    public static final String GTFS2MATSIM_TRANSIT_SCHEDULE_FILE = "output/gtfs2matsim/transit-schedule.xml";
    /** Created with playground.mzilske.gtfs.GtfsConverter, contains routes */
    public static final String GTFS2MATSIM_TRANSIT_VEHICLE_FILE = "output/gtfs2matsim/transit-vehicles.xml";
    /** Created by ExtractNetwork */
    public static final String DUMMY_NETWORK_FILE = "output/extracted-dummy-pt-network.xml";
    /** Created by ExtractNetwork */
    public static final String STREET_NETWORK_FILE = "output/extracted-street-network.xml";
    /** Created with playground.mzilske.gtfs.GtfsConverter has more links than the dummy network */
    public static final String GTFS2MATSIM_NETWORK_FILE = "output/gtfs2matsim/network.xml";
    public static final String OTP_GRAPH_FILE = "output/Graph.obj";
    public static final String POPULATION_FILE = "output/population.xml";

    // Set this as you like - scenario is created from scratch.
    public static final String TARGET_SCENARIO_COORDINATE_SYSTEM = "EPSG:3857";
}
