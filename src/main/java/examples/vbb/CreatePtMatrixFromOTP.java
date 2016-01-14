package examples.vbb;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.InputStreamGraphSource;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.scripting.api.*;
import org.opentripplanner.standalone.CommandLineParameters;
import org.opentripplanner.standalone.OTPMain;
import org.opentripplanner.standalone.OTPServer;
import org.opentripplanner.standalone.Router;

import java.io.File;
import java.io.IOException;
import java.lang.String;import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class CreatePtMatrixFromOTP {

	public static void main(String[] args) throws IOException {

		double minX=13.1949;
		double maxX=13.5657;
		double minY=52.3926;
		double maxY=52.6341;

		String outputRoot = "output/vbb/otp-graph";

//		OTPMain.main(new String[]{"--build", outputRoot});

		GraphService graphService = new GraphService();
		graphService.registerGraph("", InputStreamGraphSource.newFileGraphSource("", new File(outputRoot), Graph.LoadLevel.FULL));
		OTPServer otpServer = new OTPServer(new CommandLineParameters(), graphService);
		final OtpsEntryPoint otp = new OtpsEntryPoint(otpServer);
		final OtpsCsvOutput csvOutput = otp.createCSVOutput();
		long t0 = System.currentTimeMillis();
		final OtpsPopulation gridPopulation = otp.createGridPopulation(maxY, minY, minX, maxX, 5, 5);
		final Calendar calendar = Calendar.getInstance();
		calendar.set(2015, 8, 15);
		StreamSupport.stream(gridPopulation.spliterator(), true).forEach(otpsIndividual -> {
			OtpsRoutingRequest request = otp.createRequest();
			request.setDateTime(calendar.getTime());
			request.setOrigin(otpsIndividual);
			OtpsSPT spt = otp.getRouter().plan(request);
			if (spt != null) {
				List<OtpsEvaluatedIndividual> evaluatedIndividual = spt.eval(gridPopulation);
				for (OtpsEvaluatedIndividual otpsEvaluatedIndividual : evaluatedIndividual) {
					csvOutput.addRow(new String[]{
							otpsIndividual.getLocation().toString(),
							otpsEvaluatedIndividual.getIndividual().getLocation().toString(),
							otpsEvaluatedIndividual.getTime().toString()
					});
				}
			}
		});
		long t1 = System.currentTimeMillis();
		System.out.printf("Time: %d\n", t1-t0);
		csvOutput.save("output/travelTimeMatrix.csv");
	}

}
