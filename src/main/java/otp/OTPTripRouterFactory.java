package otp;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripRouterFactory;
import org.matsim.core.router.RoutingContext;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.GenericAStarFactory;
import org.opentripplanner.routing.impl.GraphServiceImpl;
import org.opentripplanner.routing.impl.RetryingPathServiceImpl;
import org.opentripplanner.routing.impl.SPTServiceFactory;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PathService;

public final class OTPTripRouterFactory implements
		TripRouterFactory {
	// TripRouterFactory: Matsim interface for routers
	
	private CoordinateTransformation ct;
	private String day;
	private String timeZone;
	private PathService pathservice;
    private TransitSchedule transitSchedule;
	private Network matsimNetwork;
	private boolean chooseRandomlyAnOtpParameterProfile;
	private int numOfAlternativeItinerariesToChooseFromRandomly;

	public OTPTripRouterFactory(TransitSchedule transitSchedule, Network matsimNetwork, 
			CoordinateTransformation ct, String day, String timeZone, String graphFile,
			boolean chooseRandomlyAnOtpParameterProfile, 
			int numOfAlternativeItinerariesToChooseFromRandomly) {
        GraphService graphservice = createGraphService(graphFile);
        SPTServiceFactory sptService = new GenericAStarFactory();
        pathservice = new RetryingPathServiceImpl(graphservice, sptService);
		this.transitSchedule = transitSchedule;
		this.matsimNetwork = matsimNetwork;
		this.ct = ct;
		this.day = day;
		this.timeZone = timeZone;
		this.chooseRandomlyAnOtpParameterProfile = chooseRandomlyAnOtpParameterProfile;
		this.numOfAlternativeItinerariesToChooseFromRandomly = numOfAlternativeItinerariesToChooseFromRandomly;
	}

    public static GraphService createGraphService(String graphFile) {
        try {
            final Graph graph = Graph.load(new File(graphFile), Graph.LoadLevel.FULL);
            return new GraphServiceImpl() {
                public Graph getGraph(String routerId) {
                    return graph;
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException();
        }
    }


    @Override
	public TripRouter instantiateAndConfigureTripRouter(RoutingContext iterationContext) {
		TripRouter tripRouter = new TripRouter();
		// OtpRoutingModule uses the modes teleport_begin_or_end and teleport_transit_stop_area
		// -> new modes whose main mode is unknown
		// -> return main mode pt
		tripRouter.setMainModeIdentifier(new MainModeIdentifier(){

			@Override
			public String identifyMainMode(List<PlanElement> tripElements) {
				String mode = ((Leg) tripElements.get( 0 )).getMode();
				if(mode.equals(TransportMode.transit_walk) || 
						mode.equals(OTPRoutingModule.TELEPORT_BEGIN_END) || 
						mode.equals(OTPRoutingModule.TELEPORT_TRANSIT_STOP_AREA)
								// add walk and bike because they should be routed using otp, too
								|| mode.equals(TransportMode.walk)
								|| mode.equals(TransportMode.bike)
								){
					return TransportMode.pt;
				} else {
					return mode;
				}
			}
			
		});
		tripRouter.setRoutingModule("pt", new OTPRoutingModule(pathservice, transitSchedule, 
				matsimNetwork, day, timeZone, ct, chooseRandomlyAnOtpParameterProfile,
				numOfAlternativeItinerariesToChooseFromRandomly));
		return tripRouter;
	}
	
}