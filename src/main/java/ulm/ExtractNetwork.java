package ulm;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import otp.OTPTripRouterFactory;
import otp.ReadGraph;

public class ExtractNetwork {

    public static void main(String[] args) {
        ReadGraph readGraph = new ReadGraph(OTPTripRouterFactory.createGraphService(Consts.BASEDIR + "Graph.obj"),
                TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, Consts.TARGET_SCENARIO_COORDINATE_SYSTEM));
        readGraph.run();
        
        Network network = readGraph.getScenario().getNetwork();
//        MergeNetworks.merge(network, "", readGraph.getDummyPtScenario().getNetwork());
        new NetworkWriter(network).write(Consts.NETWORK_FILE);
//        new NetworkWriter(readGraph.getDummyPtScenario().getNetwork()).write(Consts.DUMMY_NETWORK_FILE);
        // Writes only transitStops
        new TransitScheduleWriter(readGraph.getScenario().getTransitSchedule()).writeFile(Consts.TRANSIT_SCHEDULE_FILE);
    }

}
