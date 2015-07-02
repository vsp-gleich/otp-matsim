package ulm;

import org.opentripplanner.visualizer.GraphVisualizer;
import otp.OTPTripRouterFactory;

public class ShowGraph {

    // Das funktioniert zwar, das Programm scheint aber nicht gepflegt zu sein. Ich kann damit gar nichts anfangen. mz
    public static void main(String[] args) {
        GraphVisualizer graphVisualizer = new GraphVisualizer(OTPTripRouterFactory.createGraphService(Consts.BASEDIR + "Graph.obj").getRouter());
        graphVisualizer.setVisible(true);
    }

}
