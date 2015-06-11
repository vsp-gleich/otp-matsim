package otp;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.facilities.Facility;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.PtConstants;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.OnboardEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.vertextype.TransitVertex;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * TODO:
 * 
 * @author gleich
 *
 */
public class OTPRoutingModule implements RoutingModule {

    // Mode used for trips inserted "by hand" (not coming from OTP)
    // to get the agent to/from the beginning/end of the OTP-trip.
    public static final String TELEPORT_BEGIN_END = "teleport_begin_or_end";
    
    // Mode used for trips inserted "by hand" (not coming from OTP)
    // to move the agent within the Transit stop area and between the street network and the transit stop.
    public static final String TELEPORT_TRANSIT_STOP_AREA = "teleport_transit_stop_area";

    // Trips on pt lines according to the TransitSchedule.
    public static final String PT = "pt";

    // Line switches by OTP.
    public static final String TRANSIT_WALK = "walk";
    
    private static Map<TraverseMode, String> otp2MatsimModes = new HashMap<TraverseMode, String>();
    
    private final TimeZone timeZone;

    

    private int nonefound = 0;

	private int npersons = 0;

	private TransitScheduleFactory tsf = new TransitScheduleFactoryImpl();

	private PathService pathservice;

	private TransitSchedule transitSchedule;
	
	private Network matsimNetwork;

	private CoordinateTransformation transitScheduleToPathServiceCt;

	private Date day;
	
	public OTPRoutingModule(PathService pathservice, TransitSchedule transitSchedule, 
			Network matsimNetwork, String dateString, String timeZoneString, CoordinateTransformation ct) {
		this.pathservice = pathservice;
		this.transitSchedule = transitSchedule;
		this.matsimNetwork = matsimNetwork;
		this.transitScheduleToPathServiceCt = ct;
		this.timeZone = TimeZone.getTimeZone(timeZoneString);
		try {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			df.setTimeZone(timeZone);
			this.day = df.parse(dateString);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	    otp2MatsimModes.put(TraverseMode.WALK, "walk");
	    otp2MatsimModes.put(TraverseMode.BICYCLE, "bike");
	}

	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime, Person person) {
        List<Leg> baseTrip = routeLeg(fromFacility, toFacility, departureTime);
        if (baseTrip.isEmpty()) {
            return createTeleportationTrip(fromFacility.getLinkId(), toFacility.getLinkId(), TELEPORT_BEGIN_END);
        } else {
            List<Leg> entireTrip = new ArrayList<>();
            Id<Link> baseTripStartLink = baseTrip.get(0).getRoute().getStartLinkId();
            List<Leg> accessTrip = createTeleportationTrip(fromFacility.getLinkId(), baseTripStartLink, TELEPORT_BEGIN_END);
            Id<Link> baseTripEndLink = baseTrip.get(baseTrip.size()-1).getRoute().getEndLinkId();
            List<Leg> egressTrip = createTeleportationTrip(baseTripEndLink, toFacility.getLinkId(), TELEPORT_BEGIN_END);
            entireTrip.addAll(accessTrip);
            entireTrip.addAll(baseTrip);
            entireTrip.addAll(egressTrip);
            return entireTrip;
        }
	}

    private List<Leg> createTeleportationTrip(Id<Link> startLinkId, Id<Link> endLinkId, String teleport_mode) {
        List<Leg> egressTrip = new ArrayList<>();
        if (!startLinkId.equals(endLinkId)) {
            Leg leg = new LegImpl(teleport_mode);
            GenericRouteImpl route = new GenericRouteImpl(startLinkId, endLinkId);
//            link Coords seem to be not accessible via pathservice
//            set teleport travel time to 0
            route.setTravelTime(0);
            leg.setRoute(route);
            egressTrip.add(leg);
        }
        return egressTrip;
    }

    @Override
	public StageActivityTypes getStageActivityTypes() {
		return new StageActivityTypesImpl( Arrays.asList( PtConstants.TRANSIT_ACTIVITY_TYPE ) );
	}

	private LinkedList<Leg> routeLeg(Facility fromFacility, Facility toFacility, double departureTime) {
		LinkedList<Leg> legs = new LinkedList<Leg>();
		TraverseModeSet modeSet = new TraverseModeSet();
		modeSet.setWalk(true);
		modeSet.setTransit(true);
//		modeSet.setBicycle(true);
		RoutingRequest options = new RoutingRequest(modeSet);
		options.setWalkBoardCost(3 * 60); // override low 2-4 minute values
		options.setBikeBoardCost(3 * 60 * 2);
		options.setOptimize(OptimizeType.QUICK);
		options.setMaxWalkDistance(Double.MAX_VALUE);
		 
		Calendar when = Calendar.getInstance();
		when.setTimeZone(timeZone);
		when.setTime(day);
		when.add(Calendar.SECOND, (int) departureTime);
		options.setDateTime(when.getTime());

		Coord fromCoord = transitScheduleToPathServiceCt.transform(fromFacility.getCoord());
		Coord toCoord = transitScheduleToPathServiceCt.transform(toFacility.getCoord());
		options.from =  new GenericLocation(fromCoord.getY(), fromCoord.getX());
		options.to   =  new GenericLocation(toCoord.getY(), toCoord.getX());
		options.numItineraries = 1;
		System.out.println("--------");
		System.out.println("Path from " + options.from + " to " + options.to + " at " + when);
		System.out.println("\tModes: " + modeSet);
		System.out.println("\tOptions: " + options);

		List<GraphPath> paths = pathservice.getPaths(options);

		if (paths != null) {
			GraphPath path = paths.get(0);
			path.dump();
			String stop = null;
			long time = 0;
            long lastDepartureSec = Long.MIN_VALUE;
			double distance = 0;
			if(!path.states.isEmpty()){
				lastDepartureSec = (path.states.getFirst().getTimeInMillis() - day.getTime())/1000;
			}
			TraverseMode nonTransitMode = null;
			List<Id<Link>> linksTraversedInNonTransitMode = new LinkedList<Id<Link>>();
			for (State state : path.states) {
                Edge backEdge = state.getBackEdge();
				System.out.println(state.getNonTransitMode().toString() + " " + backEdge + " " + state);
                if (backEdge != null) {
                    final long travelTime = state.getElapsedTimeSeconds() - time;
                    /*
                     *  According to a comment in otp class StreetEdge this class can be used as 
                     *  a marker to detect edges in the street layer.
                     */
                    if (backEdge instanceof StreetEdge){
                    	// Add teleport within transit stop area leg if the last leg was a pt leg
                    	if(!legs.isEmpty()) {
                    		if(legs.getLast().getMode().equals(PT)){
                                TransitStopFacility egressFacility = transitSchedule.getFacilities().get(Id.create(stop, TransitStopFacility.class));
                                legs.addAll(createTeleportationTrip(egressFacility.getLinkId(), Id.create(backEdge.getId(), Link.class), 
                                		TELEPORT_TRANSIT_STOP_AREA));
                    		}
                    	}
                    	if(nonTransitMode == null){
                    		nonTransitMode = state.getNonTransitMode();
                    	} else if(linksTraversedInNonTransitMode.isEmpty()){
                    		nonTransitMode = state.getNonTransitMode();
                    	} else if(nonTransitMode != state.getNonTransitMode()){
                    		legs.add(createStreetNetworkNonTransitLeg(otp2MatsimModes.get(nonTransitMode), 
                    				linksTraversedInNonTransitMode, travelTime, lastDepartureSec, distance));
                    		time = state.getElapsedTimeSeconds();
                        	lastDepartureSec = (state.getTimeInMillis() - day.getTime())/1000;
                    		distance = 0;
                    		nonTransitMode = state.getNonTransitMode();
                    		linksTraversedInNonTransitMode.clear();
                    	}
                    	linksTraversedInNonTransitMode.add(Id.create(backEdge.getId(), Link.class));
                    	distance = distance + backEdge.getDistance();
                    }
                    // boarding or alighting at a transit stop
                    else if (backEdge instanceof TransitBoardAlight) {
                    	Trip backTrip = state.getBackTrip();
                    	if (((TransitBoardAlight) backEdge).boarding) {
                    		// boarding
                    		String newStop = ((TransitVertex) state.getVertex()).getStopId().toString();
                    		TransitStopFacility accessFacility = transitSchedule.getFacilities().get(Id.create(newStop, TransitStopFacility.class));
                    		// System.out.println("Arrived at TransitStop: " + newStop + " .Links traversed: " + linksTraversedInNonTransitMode.toString() + "\n");

                    		if(linksTraversedInNonTransitMode.isEmpty()){
                    			/* isEmpty: either transfer between lines or trip started directly at this station */
                    			if(stop != null){
                    				/* trip has involved boarding or alighting at another transit stop before -> this is a transfer */
                    				TransitStopFacility egressFacility = transitSchedule.getFacilities().get(Id.create(stop, TransitStopFacility.class));
                    				legs.addAll(createTeleportationTrip(egressFacility.getLinkId(), accessFacility.getLinkId(), 
                    						TELEPORT_TRANSIT_STOP_AREA));
                    			}
                    		} else {
                    			/* Save the walk leg to the transit stop*/
                    			legs.add(createStreetNetworkNonTransitLeg(otp2MatsimModes.get(nonTransitMode), 
                    					linksTraversedInNonTransitMode, travelTime, lastDepartureSec, distance));
                    			/* Save a teleport leg from the last link on the street layer to the transit stop */
                    			legs.addAll(createTeleportationTrip(linksTraversedInNonTransitMode.get( 
                    					linksTraversedInNonTransitMode.size()-1), accessFacility.getLinkId(), 
                    					TELEPORT_TRANSIT_STOP_AREA));
                    			linksTraversedInNonTransitMode.clear();
                    		}
                    		time = state.getElapsedTimeSeconds();
                    		lastDepartureSec = (state.getTimeInMillis() - day.getTime())/1000;
                    		distance = 0;
                    		stop = newStop;
                    	} else {
                        	// alighting
                            Leg leg = new LegImpl(PT);
                            String newStop = ((TransitVertex) state.getVertex()).getStopId().toString();
                            TransitStopFacility accessFacility = transitSchedule.getFacilities().get(Id.create(stop, TransitStopFacility.class));
                            TransitStopFacility egressFacility = transitSchedule.getFacilities().get(Id.create(newStop, TransitStopFacility.class));
                            final ExperimentalTransitRoute route = new ExperimentalTransitRoute( 
                            		accessFacility, createLine(backTrip), createRoute(backTrip), egressFacility);
                            route.setTravelTime(travelTime);
                            route.setDistance(distance);
                            leg.setRoute(route);
                            leg.setTravelTime(travelTime);
                            leg.setDepartureTime(lastDepartureSec);
                            System.out.println(lastDepartureSec/(60*60) + ":"+ (lastDepartureSec%(60*60))/60 + ":" + lastDepartureSec%60);
                            legs.add(leg);
                            time = state.getElapsedTimeSeconds();
                        	lastDepartureSec = (state.getTimeInMillis() - day.getTime())/1000;
                        	distance = 0;
                            stop = newStop;
                        }
                        // OnboardEdge: interface for all otp edges onboard a pt vehicle
                        // TransitBoardAlight implements OnboardEdge, but always returns 0
                    } else if (backEdge instanceof OnboardEdge) {
                    	System.out.println("OnboardEdge " + backEdge.getId() + " with length " + backEdge.getDistance());
                    	distance = distance + backEdge.getDistance();
                    }
                }

            } if (!linksTraversedInNonTransitMode.isEmpty()){
            	legs.add(createStreetNetworkNonTransitLeg(otp2MatsimModes.get(nonTransitMode), 
    					linksTraversedInNonTransitMode, 
    					path.states.getLast().getElapsedTimeSeconds() - time, lastDepartureSec, distance));
            	linksTraversedInNonTransitMode.clear();
            }
		} else {
			System.out.println("None found " + nonefound++);
		}
		System.out.println("---------" + npersons++);

		return legs;
	}

	private TransitRoute createRoute(Trip backTrip) {
		List<TransitRouteStop> emptyList = Collections.emptyList();
		Id<TransitLine> tlId = Id.create(backTrip.getRoute().getId().toString(), TransitLine.class);
		Id<TransitRoute> trId = Id.create("", TransitRoute.class);
		/*
		 *  For departures on the second day matsim departure ids may differ 
		 *  from the otp trip ids in order to differentiate between the first
		 *  simulated day and the following day when some agents might finish
		 *  their journeys started on the first da.
		 *  In addition to that, some otp trips shortly after midnight are 
		 *  saved for the previous day, so similar id differences might occur.
		 *  See ReadGraph.writeTripTime() method
		 */
		for(TransitRoute tr: transitSchedule.getTransitLines().get(tlId).getRoutes().values()){
			if(tr.getDepartures().containsKey(Id.create(backTrip.getId().toString() + "_0", Departure.class)) ||
					tr.getDepartures().containsKey(Id.create(backTrip.getId().toString() + "_1", Departure.class)) ||
					tr.getDepartures().containsKey(Id.create(backTrip.getId().toString() + "_-1", Departure.class))){
				trId = tr.getId();
				return tsf.createTransitRoute(trId, null , emptyList, null);
			}
		}
		return tsf.createTransitRoute(trId, null , emptyList, null);
	}
	
	private Leg createStreetNetworkNonTransitLeg(String mode, List<Id<Link>> linksTraversed, 
			long travelTime, long departureTime, double distance){
		Leg leg = new LegImpl(mode);
		Route route = RouteUtils.createNetworkRoute(linksTraversed, matsimNetwork);
		route.setTravelTime(travelTime);
		route.setDistance(distance);
		leg.setRoute(route);
		leg.setTravelTime(travelTime);
		leg.setDepartureTime(departureTime);
		return leg;
	}

	private TransitLine createLine(Trip backTrip) {
		return tsf.createTransitLine(Id.create(backTrip.getRoute().getId().toString(), TransitLine.class));
	}

}
