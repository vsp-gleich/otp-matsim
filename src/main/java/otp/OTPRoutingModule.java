package otp;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.facilities.Facility;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.GenericRouteImpl;
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
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.FreeEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.TransferEdge;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.location.StreetLocation;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitVertex;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class OTPRoutingModule implements RoutingModule {

    // Mode used for trips inserted "by hand" (not coming from OTP)
    // to get the agent to/from the beginning/end of the OTP-trip.
    public static final String TELEPORT = "teleport";

    // Trips on pt lines according to the TransitSchedule.
    public static final String PT = "pseudo_pt";

    // Line switches by OTP.
    public static final String TRANSIT_WALK = "walk";

    private int nonefound = 0;

	private int npersons = 0;

	private TransitScheduleFactory tsf = new TransitScheduleFactoryImpl();

	private PathService pathservice;

	private TransitSchedule transitSchedule;

	private CoordinateTransformation transitScheduleToPathServiceCt;

	private Date day;
	
	public OTPRoutingModule(PathService pathservice, TransitSchedule transitSchedule, String date, CoordinateTransformation ct) {
		this.pathservice = pathservice;
		this.transitSchedule = transitSchedule;
		this.transitScheduleToPathServiceCt = ct;
		try {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			df.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
			this.day = df.parse(date);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime, Person person) {
        List<Leg> baseTrip = routeLeg(fromFacility, toFacility, departureTime);
        if (baseTrip.isEmpty()) {
            return createTeleportationTrip(fromFacility.getLinkId(), toFacility.getLinkId());
        } else {
            List<Leg> entireTrip = new ArrayList<>();
            Id<Link> baseTripStartLink = baseTrip.get(0).getRoute().getStartLinkId();
            List<Leg> accessTrip = createTeleportationTrip(fromFacility.getLinkId(), baseTripStartLink);
            Id<Link> baseTripEndLink = baseTrip.get(baseTrip.size()-1).getRoute().getEndLinkId();
            List<Leg> egressTrip = createTeleportationTrip(baseTripEndLink, toFacility.getLinkId());
            entireTrip.addAll(accessTrip);
            entireTrip.addAll(baseTrip);
            entireTrip.addAll(egressTrip);
            return entireTrip;
        }
	}

    private List<Leg> createTeleportationTrip(Id<Link> startLinkId, Id<Link> endLinkId) {
        List<Leg> egressTrip = new ArrayList<>();
        if (!startLinkId.equals(endLinkId)) {
            Leg leg = new LegImpl(TELEPORT);
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
		RoutingRequest options = new RoutingRequest(modeSet);
		options.setWalkBoardCost(3 * 60); // override low 2-4 minute values
		options.setBikeBoardCost(3 * 60 * 2);
		options.setOptimize(OptimizeType.QUICK);
		options.setMaxWalkDistance(Double.MAX_VALUE);
		 
		Calendar when = Calendar.getInstance();
		when.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
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

		Id<Link> currentLinkId = null;
		if (paths != null) {
			GraphPath path = paths.get(0);
			path.dump();
			boolean onBoard = false;
			String stop = null;
			long time = 0;
			for (State state : path.states) {
				System.out.println(state);
                Edge backEdge = state.getBackEdge();
                if (backEdge != null) {
                    final long travelTime = state.getElapsedTimeSeconds() - time;
                    if (backEdge instanceof TransitBoardAlight) {
                        Trip backTrip = state.getBackTrip();
                        if (!onBoard) {
                            stop = ((TransitVertex) state.getVertex()).getStopId().getId();
                            onBoard = true;
                            time = state.getElapsedTimeSeconds();
                            TransitStopFacility accessFacility = transitSchedule.getFacilities().get(Id.create(stop, TransitStopFacility.class));
                            if (!currentLinkId.equals(accessFacility.getLinkId())) {
                                throw new RuntimeException();
                            }
                        } else {
                            Leg leg = new LegImpl(PT);
                            String newStop = ((TransitVertex) state.getVertex()).getStopId().getId();
                            TransitStopFacility accessFacility = transitSchedule.getFacilities().get(Id.create(stop, TransitStopFacility.class));
                            TransitStopFacility egressFacility = transitSchedule.getFacilities().get(Id.create(newStop, TransitStopFacility.class));
                            final ExperimentalTransitRoute route = new ExperimentalTransitRoute( 
                            		accessFacility, createLine(backTrip), createRoute(backTrip), egressFacility);
                            route.setTravelTime(travelTime);
                            leg.setRoute(route);
                            leg.setTravelTime(travelTime);
                            legs.add(leg);
                            onBoard = false;
                            time = state.getElapsedTimeSeconds();
                            stop = newStop;
                            currentLinkId = egressFacility.getLinkId();
                        }
                    } else if (backEdge instanceof FreeEdge || backEdge instanceof TransferEdge || backEdge instanceof StreetEdge) {
                        Leg leg = new LegImpl(TRANSIT_WALK);
                        if (state.getVertex() instanceof TransitVertex) {
                            String newStop = ((TransitVertex) state.getVertex()).getStopId().getId();
                            if (!newStop.equals(stop)) {
                                Id<Link> startLinkId = currentLinkId;
                                Id<Link> endLinkId = transitSchedule.getFacilities().get(Id.create(newStop, TransitStopFacility.class)).getLinkId();
                                if (currentLinkId != null) {
                                    GenericRouteImpl route = new GenericRouteImpl(startLinkId, endLinkId);
                                    route.setTravelTime(travelTime);
                                    route.setDistance(state.getWalkSinceLastTransit());
                                    leg.setRoute(route);
                                    leg.setTravelTime(travelTime);
                                    legs.add(leg);
                                }
                                time = state.getElapsedTimeSeconds();
                                stop = newStop;
                                currentLinkId = endLinkId;
                            }
                        }
                    }
                }

            }
		} else {
			System.out.println("None found " + nonefound++);
		}
		System.out.println("---------" + npersons++);

		return legs;
	}

	private TransitRoute createRoute(Trip backTrip) {
		List<TransitRouteStop> emptyList = Collections.emptyList();
		Id<TransitLine> tlId = Id.create(backTrip.getRoute().getId().getId()+ "_"+backTrip.getRoute().getShortName(), TransitLine.class);
		Id<TransitRoute> trId = Id.create("", TransitRoute.class);
//		for(TransitRoute tr: transitSchedule.getTransitLines().get(tlId).getRoutes().values()){
//			if(tr.getDepartures().containsKey(Id.create(backTrip.getId().getId(), Departure.class))){
//				trId = tr.getId();
//				break;
//			}
//		}
		return tsf.createTransitRoute(trId, null , emptyList, null);
	}

	private TransitLine createLine(Trip backTrip) {
		return tsf.createTransitLine(Id.create(backTrip.getRoute().getId().getId()+ "_"+backTrip.getRoute().getShortName(), TransitLine.class));
	}

}
