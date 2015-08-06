package run;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.VehicleWriterV1;

import core.OTPTripRouterFactory;
import core.ReadGraph;

public class ExtractNetwork {
	
	private String otpGraphDir;
	private String targetScenarioCoordinateSystem;
	private String date;
	private	String timeZone;
	private int scheduleEndTimeOnFollowingDay;
	private boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork;
	private String networkFile;
	private String transitScheduleFile;
	private String transitVehicleFile;
	
	public ExtractNetwork(String otpGraphDir, String targetScenarioCoordinateSystem, String date, 
			String timeZone, int scheduleEndTimeOnFollowingDay, 
			boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork,
			String networkFile, String transitScheduleFile, String transitVehicleFile){
		this.otpGraphDir = otpGraphDir;
		this.targetScenarioCoordinateSystem = targetScenarioCoordinateSystem;
		this.date = date;
		this.timeZone = timeZone;
		this.scheduleEndTimeOnFollowingDay = scheduleEndTimeOnFollowingDay;
		this.useCreatePseudoNetworkInsteadOfOtpPtNetwork = useCreatePseudoNetworkInsteadOfOtpPtNetwork;
		this.networkFile = networkFile;
		this.transitScheduleFile = transitScheduleFile;
		this.transitVehicleFile = transitVehicleFile;
	}
	
	public void run(){
        ReadGraph readGraph = new ReadGraph(OTPTripRouterFactory.createGraphService(otpGraphDir),
                TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, 
                		targetScenarioCoordinateSystem),
                date,
                timeZone,
                scheduleEndTimeOnFollowingDay,
                useCreatePseudoNetworkInsteadOfOtpPtNetwork);
        readGraph.run();
        
        Network network = readGraph.getScenario().getNetwork();
        new NetworkWriter(network).write(networkFile);

        new TransitScheduleWriter(readGraph.getScenario().getTransitSchedule()).writeFile(transitScheduleFile);
        new VehicleWriterV1(readGraph.getScenario().getTransitVehicles()).writeFile(transitVehicleFile);
	}
}
