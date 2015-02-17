package ulm;

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
        new NetworkWriter(readGraph.getStreetNetworkScenario().getNetwork()).write(Consts.STREET_NETWORK_FILE);
        new NetworkWriter(readGraph.getDummyPtScenario().getNetwork()).write(Consts.DUMMY_NETWORK_FILE);
        new TransitScheduleWriter(readGraph.getDummyPtScenario().getTransitSchedule()).writeFile(Consts.TRANSIT_SCHEDULE_FILE);
    }

}
