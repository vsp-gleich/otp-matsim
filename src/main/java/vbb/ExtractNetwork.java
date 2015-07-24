package vbb;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.VehicleWriterV1;

import otp.OTPTripRouterFactory;
import otp.ReadGraph;

public class ExtractNetwork {

    public static void main(String[] args) {
        ReadGraph readGraph = new ReadGraph(OTPTripRouterFactory.createGraphService(Consts.BASEDIR),
                TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, 
                		Consts.TARGET_SCENARIO_COORDINATE_SYSTEM),
                		Consts.DATE,
                		Consts.TIME_ZONE,
                		Consts.SCHEDULE_END_TIME_ON_FOLLOWING_DATE,
                		Consts.USE_CREATE_PSEUDO_NETWORK_INSTEAD_OF_OTP_PT_NETWORK);
        readGraph.run();
        
        Network network = readGraph.getScenario().getNetwork();
        new NetworkWriter(network).write(Consts.NETWORK_FILE);

        new TransitScheduleWriter(readGraph.getScenario().getTransitSchedule()).writeFile(Consts.TRANSIT_SCHEDULE_FILE);
        new VehicleWriterV1(readGraph.getScenario().getTransitVehicles()).writeFile(Consts.TRANSIT_VEHICLE_FILE);
    }

}
