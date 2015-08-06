package run;

import org.opentripplanner.standalone.OTPMain;
import org.opentripplanner.visualizer.GraphVisualizer;

import core.OTPTripRouterFactory;

public class RunOTP {
	
	public static void runGraphBuilder(String baseDir){
		OTPMain.main(new String[]{"--build", baseDir});
	}
	
    public static void runGraphVisualizer(String baseDir) {
        GraphVisualizer graphVisualizer = new GraphVisualizer(OTPTripRouterFactory.createGraphService(baseDir).getRouter());
        graphVisualizer.setVisible(true);
    }
}
