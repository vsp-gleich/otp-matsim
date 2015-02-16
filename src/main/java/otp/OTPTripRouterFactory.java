package otp;

import java.io.File;
import java.io.IOException;

import org.matsim.core.router.TripRouterFactory;
import org.matsim.core.router.RoutingContext;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.opentripplanner.routing.algorithm.GenericAStar;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.GenericAStarFactory;
import org.opentripplanner.routing.impl.GraphServiceImpl;
import org.opentripplanner.routing.impl.RetryingPathServiceImpl;
import org.opentripplanner.routing.impl.SPTServiceFactory;

public final class OTPTripRouterFactory implements
		TripRouterFactory {
	// TripRouterFactory: Matsim interface for routers
	
	private Graph graph;
	private CoordinateTransformation ct;
	private String day;
	private RetryingPathServiceImpl pathservice;
//	private GenericAStar sptService = new GenericAStar();
	private SPTServiceFactory sptService = new GenericAStarFactory();
	private TransitSchedule transitSchedule;
	

	public OTPTripRouterFactory(TransitSchedule transitSchedule, CoordinateTransformation ct, String day, String graphFile) {
		File path = new File(graphFile);
		try {
			graph = Graph.load(path, Graph.LoadLevel.FULL);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException();
		}
		pathservice = new RetryingPathServiceImpl(graphservice, sptService);
		this.transitSchedule = transitSchedule;
		this.ct = ct;
		this.day = day;
	}

	private GraphServiceImpl graphservice = new GraphServiceImpl() {
		public Graph getGraph(String routerId) { return graph; }
	};


	
	@Override
	public TripRouter instantiateAndConfigureTripRouter(RoutingContext iterationContext) {
		TripRouter tripRouter = new TripRouter();
		tripRouter.setRoutingModule("pt", new OTPRoutingModule(pathservice, transitSchedule, day, ct));
		return tripRouter;
	}
	
}