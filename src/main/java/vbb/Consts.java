package vbb;

public class Consts {
	/**
	 * BASEDIR should include Openstreetmap data in a file named *.osm or *.pbf (here http://download.geofabrik.de/europe/germany/berlin.html)
	 * and a zip file with GTFS data (here http://daten.berlin.de/datensaetze/vbb-fahrplandaten-juni-2015-bis-dezember-2015)
	 */
    public static final String BASEDIR = "output/vbb/";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_SCHEDULE_FILE = "output/vbb/extracted-transitschedule.xml";
    /** Created by ExtractNetwork*/
    public static final String TRANSIT_VEHICLE_FILE = "output/vbb/extracted-transitvehicles.xml";
    /** Created by ExtractNetwork*/
    public static final String NETWORK_FILE = "output/vbb/extracted-network.xml";
    
    public static final String OTP_GRAPH_DIR = "output/vbb";
    public static final String POPULATION_FILE = "output/vbb/population.xml";

    // Set this as you like - scenario is created from scratch.
    public static final String TARGET_SCENARIO_COORDINATE_SYSTEM = "EPSG:3857";
    
    public static final String TIME_ZONE = "Europe/Berlin";
    public static final String DATE = "2015-08-27"; // Thursday
    public static final double SCHEDULE_END_TIME_ON_FOLLOWING_DATE = 4*60*60;
    
    public static final boolean USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK = true;
}
