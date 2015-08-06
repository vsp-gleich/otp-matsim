package core;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.core.*;
import org.opentripplanner.routing.edgetype.OnboardEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.TransitVertex;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.log4j.Logger;

/**
 * TODO: -consider schedule export for multiple days. Especially in rural areas otp sometimes returns pt journeys which arrive several days after the departure time.)
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
    
    // Mode used for pt trips replaced "by hand"
    // because the trip recommended by otp is not included in the Matsim transitschedule
    public static final String TELEPORT_MISSING_MATSIM_DEPARTURE = "teleport_missing_matsim_departure";

    // Trips on pt lines according to the TransitSchedule.
    public static final String PT = "pt";

    // Line switches by OTP.
    public static final String TRANSIT_WALK = "walk";
    
    private static Map<TraverseMode, String> otp2MatsimModes = new HashMap<TraverseMode, String>();
    
    private final TimeZone timeZone;

    

    private int nonefound = 0;

	private int npersons = 0;

	private TransitScheduleFactory tsf = new TransitScheduleFactoryImpl();

	private GraphService pathservice;

	private TransitSchedule transitSchedule;
	
	private Network matsimNetwork;

	private CoordinateTransformation transitScheduleToPathServiceCt;

	private Date day;
	
	/**
	 * In order to return different itineraries, let otp calculate an itinerary for a specific
	 * otp parameter setting which is chosen by random from the OtpParameterProfile enum
	 */
	private boolean chooseRandomlyAnOtpParameterProfile;
	
	/**
	 * In order to return different itineraries, let otp calculate multiple alternatives for the
	 * parameter settings and choose one of them randomly
	 */
	private int numOfAlternativeItinerariesToChooseFromRandomly;
	
    private final boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork;
	private final static Logger log = Logger.getLogger(OTPRoutingModule.class);
	private boolean noTransitRouteFoundForAtLeastOneTrip = false;
	
	public OTPRoutingModule(GraphService pathservice, TransitSchedule transitSchedule,
			Network matsimNetwork, String dateString, String timeZoneString, 
			CoordinateTransformation ct, boolean chooseRandomlyAnOtpParameterProfile, 
			int numOfAlternativeItinerariesToChooseFromRandomly, 
			boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork) {
		this.pathservice = pathservice;
		this.transitSchedule = transitSchedule;
		this.matsimNetwork = matsimNetwork;
		this.transitScheduleToPathServiceCt = ct;
		this.timeZone = TimeZone.getTimeZone(timeZoneString);
		this.chooseRandomlyAnOtpParameterProfile = chooseRandomlyAnOtpParameterProfile;
		this.numOfAlternativeItinerariesToChooseFromRandomly = numOfAlternativeItinerariesToChooseFromRandomly;
		this.useCreatePseudoNetworkInsteadOfOtpPtNetwork = useCreatePseudoNetworkInsteadOfOtpPtNetwork;
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
		modeSet.setTransit(true);
		OtpParameterProfile profile = OtpParameterProfile.values()[(int) (Math.random()*OtpParameterProfile.values().length)];
		if(chooseRandomlyAnOtpParameterProfile){
			modeSet.setWalk(profile.walkAllowed);
			modeSet.setBicycle(profile.bikeAllowed);
		} else {
			modeSet.setWalk(true);
			modeSet.setBicycle(true);
		}
		
		int chosenItineraryAlternative = (int) (Math.random() * numOfAlternativeItinerariesToChooseFromRandomly);
		
		RoutingRequest options = new RoutingRequest(modeSet);
		options.setMaxWalkDistance(Double.MAX_VALUE);
		options.setWalkBoardCost(3 * 60); // override low 2-4 minute values
		options.setBikeBoardCost(3 * 60 * 2);
		options.setOptimize(OptimizeType.QUICK);
		 
		Calendar when = Calendar.getInstance();
		when.setTimeZone(timeZone);
		when.setTime(day);
		when.add(Calendar.SECOND, (int) departureTime);
		options.setDateTime(when.getTime());

		Coord fromCoord = transitScheduleToPathServiceCt.transform(fromFacility.getCoord());
		Coord toCoord = transitScheduleToPathServiceCt.transform(toFacility.getCoord());
		options.from =  new GenericLocation(fromCoord.getY(), fromCoord.getX());
		options.to   =  new GenericLocation(toCoord.getY(), toCoord.getX());
		
		options.numItineraries = chosenItineraryAlternative + 1;
		System.out.println("--------");
		System.out.println("Path from " + options.from + " to " + options.to + " at " + when);
		System.out.println("\tModes: " + modeSet);
		System.out.println("\tOptions: " + options);
		options.setRoutingContext(pathservice.getRouter().graph);

		ShortestPathTree shortestPathTree = new AStar().getShortestPathTree(options);
		List<GraphPath> paths = shortestPathTree.getPaths();

		if (paths != null && paths.size() > 0) {
			// At times otp provides less paths than set in options.numItineraries
			GraphPath path = paths.get(Math.min(chosenItineraryAlternative, paths.size() - 1));
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
//				System.out.println(state.getNonTransitMode().toString() + " " + backEdge + " " + state);
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
                    	
                    } else if (backEdge instanceof TransitBoardAlight) {
                    // boarding or alighting at a transit stop
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
                            TransitRoute transitRoute = createRoute(backTrip);
                    		if(!transitRoute.getId().toString().equals("")){
                    			// A TransitRoute could be found for the otp trip
                                final ExperimentalTransitRoute route = new ExperimentalTransitRoute( 
                                		accessFacility, createLine(backTrip), transitRoute, egressFacility);
                                route.setTravelTime(travelTime);
                                route.setDistance(distance);
                                leg.setRoute(route);
                                leg.setTravelTime(travelTime);
                                leg.setDepartureTime(lastDepartureSec);
                                legs.add(leg);
                    		} else if(state.getTimeInMillis() > day.getTime() + 24*60*60*1000) {
                    			/* No TransitRoute could be found for the otp trip
                    			 * 
                    			 * If the route includes boarding a pt vehicle after the day to be simulated has ended,
                    			 * it is highly probable that no TransitRoute for this otp trip could be found 
                    			 * because it was not exported into the extracted Matsim transit schedule. Furthermore,
                    			 * the route supplied by otp implies probably a very late arrival at the destination,
                    			 * e.g. the last bus on Friday evening is already gone and as there is no bus service
                    			 * on weekends, otp recommends to wait for the next departure on Monday. This is not a
                    			 * realistic result, because most people would choose another means of transport or
                    			 * another destination or would stay where they are. Therefore exporting the transit
                    			 * schedule for an even longer time span of multiple days does not seem to be a good
                    			 * solution, as the agent will eventually choose another plan anyway. In order to avoid
                    			 * crashes due to otp trips without corresponding TransitRoutes, these are replaced
                    			 * with teleport legs.
                    			 */
                				legs.addAll(createTeleportationTrip(accessFacility.getLinkId(), egressFacility.getLinkId(),
                						TELEPORT_MISSING_MATSIM_DEPARTURE));
                				log.info("Pt leg with otp trip id \"" + backTrip.getId().toString() + "\" of TransitLine \"" +
                						backTrip.getRoute().getId().toString() + " was replaced by a teleport leg.");
                    		} else {
                    			// This should nether happen under normal circumstances.
                    			log.warn("No Matsim TransitRoute found for otp trip id \"" + 
                    					backTrip.getId().toString() + "\" of TransitLine \"" + backTrip.getRoute().getId().toString() +
                    					"\" although the otp trip was boarded on the day to be simulated.");
                    		}
                            time = state.getElapsedTimeSeconds();
                        	lastDepartureSec = (state.getTimeInMillis() - day.getTime())/1000;
                        	distance = 0;
                            stop = newStop;
                        }
                        // OnboardEdge: interface for all otp edges onboard a pt vehicle
                        // TransitBoardAlight implements OnboardEdge, but always returns 0
                    } else if (backEdge instanceof OnboardEdge) {
//                    	System.out.println("OnboardEdge " + backEdge.getId() + " with length " + backEdge.getDistance());
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
			log.info("No route found for " + ++nonefound + " calcRoute() requests.");
		}
		System.out.println("---------" + npersons++);

		if(useCreatePseudoNetworkInsteadOfOtpPtNetwork){
			adjustLinkAndStopFacilityIdsForCreatePseudoNetwork(legs);
			legs = addTeleportsBetweenDifferingStopFacilityIds(legs);
		}
		return legs;
	}

	/** Replace all TransitStopFacility ids extracted from the otp route 
	 * with the appropriate ids created by CreatePseudoNetwork
	 * and replace end link ids of legs before a pt leg and replace
	 * start link ids of legs after a pt leg
	 * 
	 * The replacement takes place as a separate step in order to move this 
	 * method later to the qsim. 
	 * The idea behind this, is that the code in routeLeg produces legs from a 
	 * passenger perspective where one stop can be a set of several 
	 * neighbouring bus stops created by CreatePseudoNetwork. Only the 
	 * qsim needs to know which individual bus stop is meant. Therefore the 
	 * code in routeLeg keeps the stopFacilityIds of otp (which equal the gtfs
	 * stop ids, e.g. "SWU_900135311") and this method replaces them with the
	 * stopId created by CreatePseudoNetwork (e.g. "SWU_900135311" or 
	 * "SWU_900135311.1")
	 * 
	 * The TELEPORT_BEGIN_END before and after the trip are added later in 
	 * calcRoute(), so if all link ids of the PT legs are corrected here the
	 * TELEPORT_BEGIN_END legs will be automatically created with the correct
	 * link ids.
	 * 
	 * mzilske/gleich 2015-07-16
	 */
	private void adjustLinkAndStopFacilityIdsForCreatePseudoNetwork(LinkedList<Leg> legs) {
		for(int i = 0; i < legs.size(); i++){
			Leg leg = legs.get(i);
			if(leg.getMode().equals(PT)){
				if(leg.getRoute() instanceof ExperimentalTransitRoute){
					ExperimentalTransitRoute route = (ExperimentalTransitRoute) leg.getRoute();
					Id<TransitStopFacility> pseudoNetworkAccessStopId = getPseudoNetworkTransitStopFacilityId(route.getAccessStopId(), route.getLineId(), route.getRouteId());
					Id<TransitStopFacility> pseudoNetworkEgressStopId = getPseudoNetworkTransitStopFacilityId(route.getEgressStopId(), route.getLineId(), route.getRouteId());
					ExperimentalTransitRoute correctedRoute = new ExperimentalTransitRoute(
							transitSchedule.getFacilities().get(pseudoNetworkAccessStopId), 
							transitSchedule.getFacilities().get(pseudoNetworkEgressStopId),
							route.getLineId(), route.getRouteId());
					correctedRoute.setTravelTime(route.getTravelTime());
					correctedRoute.setDistance(route.getDistance());
					leg.setRoute(correctedRoute);
					if(i > 0){
						Leg legBefore = legs.get(i-1);
						if(legBefore.getMode().equals(TELEPORT_TRANSIT_STOP_AREA)){
							legBefore.getRoute().setEndLinkId(transitSchedule.getFacilities().get(pseudoNetworkAccessStopId).getLinkId());
						}
					}
					if(i + 1 < legs.size()){
						Leg legAfter = legs.get(i+1);
						if(legAfter.getMode().equals(TELEPORT_TRANSIT_STOP_AREA)){
							legAfter.getRoute().setStartLinkId(transitSchedule.getFacilities().get(pseudoNetworkEgressStopId).getLinkId());
						}
					}
				}
			}
		}
	}

	private Id<TransitStopFacility> getPseudoNetworkTransitStopFacilityId(Id<TransitStopFacility> stopFacilityId, Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		if(transitSchedule.getTransitLines().get(transitLineId).getRoutes().get(transitRouteId).
				getStop(transitSchedule.getFacilities().get(stopFacilityId)) != null){
			return stopFacilityId;
		} else {
			int j = 0;
			Id<TransitStopFacility> stopFacilityIdToBeTested;
			do{
				j++;
				stopFacilityIdToBeTested = Id.create(stopFacilityId.toString() + "." + j, TransitStopFacility.class);
			} while(transitSchedule.getTransitLines().get(transitLineId).getRoutes().get(transitRouteId).getStop(transitSchedule.getFacilities().get(stopFacilityIdToBeTested)) == null);
			return stopFacilityIdToBeTested;
		}
	}
	
	/**
	 * CreatePseudoNetwork splits some TransitStopFacilities
	 * -> what used to be a change between lines at the same stop can now be
	 * a change between lines at two separate stops
	 * 
	 * @param legs
	 */
	private LinkedList<Leg> addTeleportsBetweenDifferingStopFacilityIds(LinkedList<Leg> legs) {
		LinkedList<Leg> legsWithAddedTeleports = (LinkedList<Leg>) legs.clone();
		int offsetBetweenInputAndOutputListIndex = 0;
		for(int i = 0; i < legs.size() - 1; i++){
			Leg leg = legs.get(i);
			Leg nextLeg = legs.get(i+1);
			if(leg.getMode().equals(PT) && nextLeg.getMode().equals(PT)){
				if(!leg.getRoute().getEndLinkId().equals(nextLeg.getRoute().getStartLinkId())){
					offsetBetweenInputAndOutputListIndex++;
					legsWithAddedTeleports.addAll(i + offsetBetweenInputAndOutputListIndex, 
							createTeleportationTrip(leg.getRoute().getEndLinkId(), nextLeg.getRoute().getStartLinkId(), 
    						TELEPORT_TRANSIT_STOP_AREA));
				}
			}						
		}
		return legsWithAddedTeleports;
	}

	private TransitRoute createRoute(Trip backTrip) {
		List<TransitRouteStop> emptyList = Collections.emptyList();
		Id<TransitLine> tlId = Id.create(backTrip.getRoute().getId().toString(), TransitLine.class);
		Id<TransitRoute> trId = Id.create("", TransitRoute.class);
		/*
		 *  For departures on the second day matsim departure ids may differ 
		 *  from the otp trip ids in order to differentiate between the first
		 *  simulated day and the following day when some agents might finish
		 *  their journeys started on the first day.
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
		log.info("No Matsim TransitRoute found for otp trip id \"" + 
				backTrip.getId().toString() + "\" of TransitLine \"" + backTrip.getRoute().getId().toString() + "\". \nMaybe SCHEDULE_END_TIME_ON_FOLLOWING_DATE is too early, so the trip is not extracted into the Matsim transit schedule.");
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
