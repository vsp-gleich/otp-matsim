package core;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.PatternHop;
import org.opentripplanner.routing.edgetype.PreAlightEdge;
import org.opentripplanner.routing.edgetype.PreBoardEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTransitLink;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * TODO: 
 * -CreatePseudoNetwork sets the freespeed to 30km/h which is far too low e.g.
 * for interurban bus lines, so these lines are delayed in the simulation
 * -otp trips which depart at their first stop before the simulation starts 
 * (that means usually departures before midnight) are simulated to depart at 
 * the first stop at the simulation begin -> huge delays that can be avoided
 * by starting the simulation earlier
 * -otp trips saved as frequency cannot be handled yet (however we do not have
 * any example for a gtfs data set using frequencies)
 */
public class ReadGraph implements Runnable {

    private GraphService graphService;
    private CoordinateTransformation ct;
    private Set<AgencyAndId> serviceIdsOnDate;
    private Set<AgencyAndId> serviceIdsOnFollowingDate;
    private final int scheduleEndTimeOnFollowingDate;
    private Set<AgencyAndId> serviceIdsOnPreviousDate;
    private Scenario scenario;
    private Set<String> patternCodesProcessed = new HashSet<String>();
    private Set<String> otpTransitTraverseModes = new HashSet<String>();
    private final boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork;

    public Scenario getScenario() {
        return scenario;
    }

    /**
     * Usually otp trips on two consecutive days are saved, because for some 
     * agents their journey started on the first day might not finish before
     * the following day, so some pt trips on the second day should be saved in
     * order to provide scheduled pt trips for all agents.
     * 
     * Only otp trips assigned to the date set in the dateString and otp trips 
     * assigned to the following day that depart at their first stop before 
     * scheduleEndTimeOnFollowingDate are written into the TransitSchedule.
     * Especially night bus lines can be assigned to the day before their first
     * departure, so setting scheduleEndTimeOnFollowingDate to 0 does not
     * mean that no departures on the following are saved.
     * 
     * In addition to that, all otp trips assigned to the day before the date
     * set in dateString are saved, if their last departure on the second to 
     * last stop is before midnight. This includes especially night bus lines.
     */
    public ReadGraph(GraphService graphService, CoordinateTransformation ct, String dateString, 
    		String timeZoneString, int scheduleEndTimeOnFollowingDate, 
    		boolean useCreatePseudoNetworkInsteadOfOtpPtNetwork) {
        this.graphService = graphService;
        this.ct = ct;
        this.scheduleEndTimeOnFollowingDate = scheduleEndTimeOnFollowingDate;
        this.useCreatePseudoNetworkInsteadOfOtpPtNetwork = useCreatePseudoNetworkInsteadOfOtpPtNetwork;
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
        Date dateDate;
        Calendar dateCalendar = Calendar.getInstance(timeZone);
        Calendar followingDateCalendar = Calendar.getInstance(timeZone);
        Calendar previousDateCalendar = Calendar.getInstance(timeZone);
		try {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			df.setTimeZone(timeZone);
			dateDate = df.parse(dateString);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		dateCalendar.setTime(dateDate);
		serviceIdsOnDate = graphService.getRouter().graph.getCalendarService().getServiceIdsOnDate(new ServiceDate(dateCalendar));
		followingDateCalendar.setTime(dateDate);
		followingDateCalendar.add(Calendar.DAY_OF_MONTH, 1);
		serviceIdsOnFollowingDate = graphService.getRouter().graph.getCalendarService().getServiceIdsOnDate(new ServiceDate(followingDateCalendar));
		previousDateCalendar.setTime(dateDate);
		previousDateCalendar.add(Calendar.DAY_OF_MONTH, -1);
		serviceIdsOnPreviousDate = graphService.getRouter().graph.getCalendarService().getServiceIdsOnDate(new ServiceDate(followingDateCalendar));
    }

    public void run() {
        Config ptConfig = ConfigUtils.createConfig();
        ptConfig.transit().setUseTransit(true);

        scenario = ScenarioUtils.createScenario(ptConfig);
    	initialize();
    	
        extractStreetNetwork();
        extractPtNetworkAndTransitStops();
        extractPtSchedule();
        
		if(useCreatePseudoNetworkInsteadOfOtpPtNetwork){
			CreatePseudoNetwork ptNetCreator = new CreatePseudoNetwork(scenario.getTransitSchedule(), 
					scenario.getNetwork(), "PseudoPtNetwork");
			ptNetCreator.createNetwork();
		}
    }

    private void initialize() {
    	for(TraverseMode mode: TraverseMode.values()){
    		if(mode.isTransit()){
        		otpTransitTraverseModes.add(mode.name());
            	VehicleType vehType = scenario.getTransitVehicles().getFactory().createVehicleType(
            			Id.create(mode.name() + " Default Vehicle", VehicleType.class));
                VehicleCapacity vehCapacity = scenario.getTransitVehicles().getFactory().createVehicleCapacity();
                vehCapacity.setSeats(50);
                vehCapacity.setStandingRoom(50);
            	vehType.setCapacity(vehCapacity);
            	scenario.getTransitVehicles().addVehicleType(vehType);
    		}
    	}
	}

	private void extractStreetNetwork() {
        Network network = scenario.getNetwork();
        for (Vertex v : graphService.getRouter().graph.getVertices()) {
            if (v instanceof IntersectionVertex) {
                // Can be an OSM node, but can also be a split OSM way to insert a transit stop.
                Node n = network.getFactory().createNode(Id.create(v.getIndex(), Node.class), ct.transform(CoordUtils.createCoord(v.getX(), v.getY())));
                network.addNode(n);
            }
        }
        for (Vertex v : graphService.getRouter().graph.getVertices()) {
            if (v instanceof IntersectionVertex) {
                for (Edge e : v.getOutgoing()) {
                    if (e instanceof StreetEdge) {
                        Node fromNode = network.getNodes().get(Id.create(e.getFromVertex().getIndex(), Node.class));
                        Node toNode = network.getNodes().get(Id.create(e.getToVertex().getIndex(), Node.class));
                        Link l = network.getFactory().createLink(Id.create(e.getId(), Link.class), fromNode, toNode);
                        l.setFreespeed(((StreetEdge) e).getCarSpeed());
                        l.setLength(e.getDistance());
//                        System.out.println("link " + l.getId() + " StreetClass " + ((StreetEdge) e).getStreetClass());
                        // set default capacity (independent from the road type because the road type is unknown)
                        l.getCapacity(2000);
                        
                        network.addLink(l);
                    } else if (e instanceof StreetTransitLink) {
                        // Found a street transit link
                    }
                }
            }
        }
    }

    private void extractPtNetworkAndTransitStops(){
        Network network = scenario.getNetwork();
        /* Extract TransitStops */
        for (Vertex v : graphService.getRouter().graph.getVertices()) {
        	if (v instanceof TransitStop) {
        		TransitStop transitStop = (TransitStop) v;
        		String stopId = transitStop.getStopId().toString();
        		Coord coord = ct.transform(CoordUtils.createCoord(transitStop.getX(), transitStop.getY()));
        		/* isBlocking set to false because several lines each stopping at a different stop in otp are mapped to one matsim stop */
        		TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(
        				Id.create(stopId, TransitStopFacility.class),
        				coord,
        				false);
        		if(useCreatePseudoNetworkInsteadOfOtpPtNetwork){
            		scenario.getTransitSchedule().addStopFacility(transitStopFacility);
        		} else {
            		Node node = network.getFactory().createNode(
            				Id.createNodeId(stopId),
            				coord);
            		network.addNode(node);
            		Id<Link> linkId = Id.createLinkId(stopId);
            		Link link = network.getFactory().createLink(linkId, node, node);
            		// Different lines with different modes can use the same stop -> allow all pt modes
            		link.setAllowedModes(otpTransitTraverseModes);
            		// Increase flow and storage capacity in order to avoid pt vehicles blocking each other
            		link.setCapacity(1000000);
            		link.setFreespeed(1000000);
            		link.setLength(1000000);
            		network.addLink(link);
            		transitStopFacility.setLinkId(linkId);
            		scenario.getTransitSchedule().addStopFacility(transitStopFacility);
        		}
        	}
        }
        /* Extract links between TransitStops */
		if(!useCreatePseudoNetworkInsteadOfOtpPtNetwork){
			for(Vertex v: graphService.getRouter().graph.getVertices()){
				if(v instanceof TransitStop){
					TransitStop departureStop = (TransitStop) v;
					for(Edge e: v.getOutgoing()){
						/* skip edges between the Transit stop vertex and the PatternStopVertex (departure vertex of a pt line) */
						if(e instanceof PreBoardEdge){
							Vertex allLinesDepartVertex = e.getToVertex();
							for(Edge departureEdge: allLinesDepartVertex.getOutgoing()){
								// should be always true, but otp's network model might change or might be less strict than it appears
								if(departureEdge instanceof TransitBoardAlight && 
										departureEdge.getToVertex().getOutgoing().size() == 1){
									Edge probablyPatternHop = departureEdge.getToVertex().getOutgoing().iterator().next();
									// should be always true, but otp's network model might change or might be less strict than it appears
									if(probablyPatternHop instanceof PatternHop){
										PatternHop patternHop = (PatternHop) probablyPatternHop;
										Vertex lineArrivalVertex = patternHop.getToVertex();
										for(Edge arrivalTransitBoardAlight: lineArrivalVertex.getOutgoing()){        							
											if(arrivalTransitBoardAlight instanceof TransitBoardAlight){
												Vertex allLinesArrivalVertex = arrivalTransitBoardAlight.getToVertex();
												// should be always true, but otp's network model might change or might be less strict than it appears
												if(allLinesArrivalVertex.getOutgoing().size() == 1){
													Edge prealight = allLinesArrivalVertex.getOutgoing().iterator().next();
													if(prealight instanceof PreAlightEdge){
														Vertex arrivalStopVertex = prealight.getToVertex();
														if(arrivalStopVertex instanceof TransitStop){
															TransitStop arrivalStop = (TransitStop) arrivalStopVertex;
															Node fromNode = network.getNodes().get(Id.create(departureStop.getStopId().toString(), Node.class));
															Node toNode = network.getNodes().get(Id.create(arrivalStop.getStopId().toString(), Node.class));
															Link l = network.getFactory().createLink(Id.create(patternHop.getId(), Link.class), fromNode, toNode);
															Set<String> allowedModes = new HashSet<String>();
															allowedModes.add(patternHop.getMode().toString());
															l.setAllowedModes(allowedModes);
															l.setLength(patternHop.getDistance());
															// Increase capacity and freespeed in order to avoid pt trips hindering each other
															l.setCapacity(1000000);
															l.setFreespeed(20);
															network.addLink(l);
														}
													}        	        						
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
    }

    private void extractPtSchedule(){
        for(Vertex v: graphService.getRouter().graph.getVertices()){
        	if(v instanceof TransitStop){
        		for(Edge e: v.getOutgoing()){
        			/* skip edges between the Transit stop vertex and the PatternStopVertex (departure vertex of a pt line) */
        			if(e instanceof PreBoardEdge){
        				Vertex allLinesDepartVertex = e.getToVertex();
        				for(Edge departureEdge: allLinesDepartVertex.getOutgoing()){
        					// should be always true, but otp's network model might change or might be less strict than it appears
        					if(departureEdge instanceof TransitBoardAlight && 
        							departureEdge.getToVertex().getOutgoing().size() == 1){
        						Edge probablyPatternHop = departureEdge.getToVertex().getOutgoing().iterator().next();
            					// should be always true, but otp's network model might change or might be less strict than it appears
        						if(probablyPatternHop instanceof PatternHop){
        							PatternHop patternHop = (PatternHop) probablyPatternHop;
        							writeTripPattern(patternHop.getPattern());
        						}
        					}
        				}
        			}
        		}
        	}
        }
    }
    
	private void writeTripPattern(TripPattern pattern) {
		Id<TransitLine> lineId = Id.create(pattern.route.getId().toString(), TransitLine.class);
		if(!patternCodesProcessed.contains(pattern.code)){
			if(!scenario.getTransitSchedule().getTransitLines().containsKey(lineId)){
//				System.out.println("Line " + lineId.toString() + ", pattern.name: " + pattern.name);
				TransitLine transitLine = scenario.getTransitSchedule().getFactory().createTransitLine(lineId);
				transitLine.setName(pattern.route.getShortName() + ": " + pattern.route.getLongName());
				scenario.getTransitSchedule().addTransitLine(transitLine);
			}
//			System.out.println("Line " + lineId.toString() + ", pattern.name: " + pattern.name + ", code: " + pattern.code + ", route: " + pattern.route);
			Id<Link> startLinkId = Id.create(pattern.getPatternHops().get(0).getBeginStop().getId().toString(), Link.class);
			Id<Link> endLinkId = Id.create(pattern.getPatternHops().get(pattern.getPatternHops().size()-1).getEndStop().getId().toString(), Link.class);
			if(pattern.getPatternHops().size() > 0){
				NetworkRoute netRoute = (NetworkRoute) (new LinkNetworkRouteFactory()).createRoute(startLinkId, endLinkId);
				List<Id<Link>> linksBetweenStartAndEnd = new LinkedList<Id<Link>>();
				for(int i = 0; i <= pattern.getPatternHops().size() - 2; i++){
					linksBetweenStartAndEnd.add(Id.create(pattern.getPatternHops().get(i).getId(), Link.class));
					linksBetweenStartAndEnd.add(Id.create(pattern.getPatternHops().get(i).getEndStop().getId().toString(), Link.class));
				}
				// Add last patternhop without adding the terminus stop
				linksBetweenStartAndEnd.add(Id.create(pattern.getPatternHops().get(pattern.getPatternHops().size() - 1).getId(), Link.class));
				netRoute.setLinkIds(startLinkId, linksBetweenStartAndEnd, endLinkId);
				if(pattern.getStops().size() > 1){
					/* Iterate over all trips (arrival and departure times saved in one TripTimes
					 * object per Trip) and group them into TransitRoutes with equal 
					 * arrival and departure offsets
					 */
					for(TripTimes tripTimes: pattern.scheduledTimetable.tripTimes){
						// save pt trips operating on the day to be simulated
						if(serviceIdsOnDate.contains(tripTimes.trip.getServiceId())){
							writeTripTime(pattern, lineId, netRoute, tripTimes, 0);
						}
						if(serviceIdsOnFollowingDate.contains(tripTimes.trip.getServiceId()) && 
								tripTimes.getDepartureTime(0) < scheduleEndTimeOnFollowingDate){
							writeTripTime(pattern, lineId, netRoute, tripTimes, 1);
						}
						if(serviceIdsOnPreviousDate.contains(tripTimes.trip.getServiceId()) && 
								tripTimes.getDepartureTime(tripTimes.getNumStops() - 2) > 24*60*60){
							writeTripTime(pattern, lineId, netRoute, tripTimes, -1);
						}
					}
				} else {
					System.err.println("Only one TransitStop for TransitRoute (code) " + pattern.code);
				}
			} else {
				System.err.println("No PatternHop for TransitRoute (code) " + pattern.code);
			}
			patternCodesProcessed.add(pattern.code);
		}
	}

	/*
	 * For matsim departure ids and matsim vehicle ids "_" add a number is 
	 * added to the otp trip id in order to avoid duplicate ids by 
	 * differentiating between trips on the first simulated day ("_0") and 
	 * trips on the previous ("_-1") or the following day ("_1")
	 */
	private void writeTripTime(TripPattern pattern, Id<TransitLine> lineId,
			NetworkRoute netRoute, TripTimes tripTimes, int day) {
		List<TransitRouteStop> transitRouteStops = new LinkedList<TransitRouteStop>();
		for(int i = 0; i < tripTimes.getNumStops(); i++){
			/* Assuming that org.onebusaway.gtfs.model.Stop and 
			 * org.opentripplanner.routing.vertextype.TransitStop use the same ids
			 */
			Id<TransitStopFacility> stopId = Id.create(pattern.getStops().get(i).getId().toString(), TransitStopFacility.class);
			TransitStopFacility stopFacility = scenario.getTransitSchedule().getFacilities().get(stopId);
			double arrivalDelay = tripTimes.getScheduledArrivalTime(i) - tripTimes.getScheduledArrivalTime(0);
			double departureDelay = tripTimes.getScheduledDepartureTime(i) - tripTimes.getScheduledArrivalTime(0);
			TransitRouteStop routeStop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(stopFacility, arrivalDelay, departureDelay);
			routeStop.setAwaitDepartureTime(true);
			transitRouteStops.add(routeStop);
		}

		Id<Departure> tripId;
		Id<Vehicle> vehicleId;
		Departure departure;
		
		// differentiate otp trips repeated on different days to be simulated
		// -> add id addition and move departure time by number indicated in variable day
		tripId = Id.create(tripTimes.trip.getId().toString() + "_" + day, Departure.class);
		vehicleId = Id.create(tripTimes.trip.getId().toString() + "_" + day, Vehicle.class);
		departure = scenario.getTransitSchedule().getFactory().createDeparture(
				tripId, tripTimes.getScheduledArrivalTime(0) + day*24*60*60);

		departure.setVehicleId(vehicleId);
		VehicleType vehType= scenario.getTransitVehicles().getVehicleTypes().get(
				Id.create(pattern.mode.toString() + " Default Vehicle", VehicleType.class));
		Vehicle veh = scenario.getTransitVehicles().getFactory().createVehicle(vehicleId, vehType);
		scenario.getTransitVehicles().addVehicle(veh);

		// Check if a TransitRoute with the same arrival and departure offsets already exists
		boolean existingTransitRouteFound = false;
		for(TransitRoute transitRoute: scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().values()){
			if(transitRoute.getStops().equals(transitRouteStops)){
				transitRoute.addDeparture(departure);
				existingTransitRouteFound = true;
			}
		}
		if(!existingTransitRouteFound){
			Id<TransitRoute> routeId = Id.create(tripTimes.trip.getId().toString(), TransitRoute.class);
			TransitRoute transitRoute = scenario.getTransitSchedule().getFactory().createTransitRoute(
					routeId, netRoute, transitRouteStops, pattern.mode.toString());
			transitRoute.setDescription("Code: " + pattern.code + ", Name: " + pattern.name);
			transitRoute.addDeparture(departure);
			scenario.getTransitSchedule().getTransitLines().get(lineId).addRoute(transitRoute);
		}
	}
}
