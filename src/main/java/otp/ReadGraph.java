package otp;

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
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.onebusaway.gtfs.model.Stop;
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
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * TODO: 
 * -TransitRoute arrival, departure delays
 * -otp trips -> matsim departures
 * -check otp2matsim transport modes
 */
public class ReadGraph implements Runnable {

    private GraphService graphService;
    private CoordinateTransformation ct;
    private final Map<String, String> otp2matsimTransportModes = new HashMap<String, String>();

    public Scenario getScenario() {
        return scenario;
    }

    private Scenario scenario;

    public ReadGraph(GraphService graphService, CoordinateTransformation ct) {
        this.graphService = graphService;
        this.ct = ct;
    }

    public void run() {
    	initialize();
        Config ptConfig = ConfigUtils.createConfig();
        ptConfig.scenario().setUseTransit(true);
        ptConfig.scenario().setUseVehicles(true);
        
        scenario = ScenarioUtils.createScenario(ptConfig);
        extractStreetNetwork(scenario);
        extractPtNetworkAndTransitStops(scenario);
        extractPtSchedule(scenario);
    }

    private void initialize() {
        otp2matsimTransportModes.put("BUS", "bus");
        otp2matsimTransportModes.put("BUSISH", "bus");
        otp2matsimTransportModes.put("FERRY", "ship");
        otp2matsimTransportModes.put("TRAM", "train");
        otp2matsimTransportModes.put("SUBWAY", "train");
        otp2matsimTransportModes.put("RAIL", "train");
        otp2matsimTransportModes.put("CABLE_CAR", "train");
        otp2matsimTransportModes.put("GONDOLA", "train");
        otp2matsimTransportModes.put("FUNICULAR", "train");
        otp2matsimTransportModes.put("TRANSIT", "train");
        otp2matsimTransportModes.put("TRAINISH", "train");
	}

	private void extractStreetNetwork(Scenario scenario) {
        Network network = scenario.getNetwork();
        for (Vertex v : graphService.getGraph().getVertices()) {
            if (v instanceof IntersectionVertex) {
                // Can be an OSM node, but can also be a split OSM way to insert a transit stop.
                Node n = network.getFactory().createNode(Id.create(v.getIndex(), Node.class), ct.transform(new CoordImpl(v.getX(), v.getY())));
                network.addNode(n);
            }
//            System.out.println(v + v.getClass().toString());
        }
        int i = 0;
        for (Vertex v : graphService.getGraph().getVertices()) {
            if (v instanceof IntersectionVertex) {
                for (Edge e : v.getOutgoing()) {
                    if (e instanceof StreetEdge) {
                        Node fromNode = network.getNodes().get(Id.create(e.getFromVertex().getIndex(), Node.class));
                        Node toNode = network.getNodes().get(Id.create(e.getToVertex().getIndex(), Node.class));
                        Link l = network.getFactory().createLink(Id.create(e.getId(), Link.class), fromNode, toNode);
                        network.addLink(l);
                    } else if (e instanceof StreetTransitLink) {
                        // Found a street transit link
                    }
                }
            }
        }
    }

    private void extractPtNetworkAndTransitStops(Scenario scenario){
        Network network = scenario.getNetwork();
        /* Extract TransitStops */
        for (Vertex v : graphService.getGraph().getVertices()) {
        	if (v instanceof TransitStop) {
        		TransitStop transitStop = (TransitStop) v;
        		String stopId = transitStop.getStopId().getId();
        		Coord coord = ct.transform(new CoordImpl(transitStop.getX(), transitStop.getY()));
        		Node node = network.getFactory().createNode(
        				Id.createNodeId(stopId),
        				coord);
        		network.addNode(node);
        		Id<Link> linkId = Id.createLinkId(stopId);
        		network.addLink(network.getFactory().createLink(
        				linkId,
        				node,
        				node));
        		/* isBlocking set to false because several lines each stopping at a different stop in otp are mapped to one matsim stop */
        		TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(
        				Id.create(stopId, TransitStopFacility.class),
        				coord,
        				false);
        		transitStopFacility.setLinkId(linkId);
        		scenario.getTransitSchedule().addStopFacility(transitStopFacility);
        	}
        }
        /* Extract links between TransitStops */
        for(Vertex v: graphService.getGraph().getVertices()){
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
//            	        		        				 System.out.println("Transit link " + patternHop.getId() + " from " + departureStop.getStopId().getId() + " to " + arrivalStop.getStopId().getId());
            	        		                        Node fromNode = network.getNodes().get(Id.create(departureStop.getStopId().getId(), Node.class));
            	        		                        Node toNode = network.getNodes().get(Id.create(arrivalStop.getStopId().getId(), Node.class));
            	        		                        Link l = network.getFactory().createLink(Id.create(patternHop.getId(), Link.class), fromNode, toNode);
            	        		                        Set<String> allowedModes = new HashSet<String>();
            	        		                        allowedModes.add(otp2matsimTransportModes.get(patternHop.getMode()));
            	        		                        l.setAllowedModes(allowedModes);
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

    private void extractPtSchedule(Scenario scenario){
        for(Vertex v: graphService.getGraph().getVertices()){
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
//		!!! id in OTPRoutingModule: backTrip.getRoute().getId().getId()+ "_"+backTrip.getRoute().getShortName()
		Id<TransitLine> lineId = Id.create(pattern.route.getId().getId(), TransitLine.class);
		Id<TransitRoute> routeId = Id.create(pattern.code, TransitRoute.class);
		if(!scenario.getTransitSchedule().getTransitLines().containsKey(lineId)){
			System.out.println("Line " + lineId.toString() + ", pattern.name: " + pattern.name);
			TransitLine transitLine = scenario.getTransitSchedule().getFactory().createTransitLine(lineId);
			transitLine.setName(pattern.route.getShortName() + ": " + pattern.route.getLongName());
			scenario.getTransitSchedule().addTransitLine(transitLine);
		}
		if(!scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().containsKey(routeId)){
			System.out.println("Line " + lineId.toString() + ", pattern.name: " + pattern.name + ", code: " + pattern.code + ", route: " + pattern.route);
			Id<Link> startLinkId = Id.create(pattern.getPatternHops().get(0).getId(), Link.class);
			Id<Link> endLinkId = Id.create(pattern.getPatternHops().get(pattern.getPatternHops().size()-1).getId(), Link.class);
			if(pattern.getPatternHops().size() > 0){
				NetworkRoute netRoute = (NetworkRoute) (new LinkNetworkRouteFactory()).createRoute(startLinkId, endLinkId);
				List<Id<Link>> linksBetweenStartAndEnd = new LinkedList<Id<Link>>();
				for(int i = 1; i <= pattern.getPatternHops().size() - 2; i++){
					linksBetweenStartAndEnd.add(Id.create(pattern.getPatternHops().get(i).getId(), Link.class));
				}
				netRoute.setLinkIds(startLinkId, linksBetweenStartAndEnd, endLinkId);
				if(pattern.stopPattern.stops.length > 1){
					List<TransitRouteStop> transitRouteStops = new LinkedList<TransitRouteStop>();
					/* Assumes that org.onebusaway.gtfs.model.Stop and org.opentripplanner.routing.vertextype.TransitStop
					 * use the same ids which appears to be true
					 */
					for(Stop stop: pattern.getStops()){
						TransitStopFacility stopFacility = scenario.getTransitSchedule().getFacilities().get(Id.create(stop.getId().getId(), TransitStopFacility.class));
						// Dummy arrivalDelay and departureDelay values
						double arrivalDelay = 1;
						double departureDelay = 1;
						TransitRouteStop routeStop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(stopFacility, arrivalDelay, departureDelay);
						routeStop.setAwaitDepartureTime(true);
						transitRouteStops.add(routeStop);
//						System.out.println("TransitRoute (code) "+ pattern.code + " Stop nr " + stop.getId().getId());
					}
					TransitRoute transitRoute = scenario.getTransitSchedule().getFactory().createTransitRoute(routeId, netRoute, transitRouteStops, pattern.mode.toString());
					transitRoute.setDescription("Code: " + pattern.code + ". Name: " + pattern.name);
					scenario.getTransitSchedule().getTransitLines().get(lineId).addRoute(transitRoute);
				} else {
					System.err.println("Only one TransitStop for TransitRoute (code) " + pattern.code);
				}

			} else {
				System.err.println("No PatternHop for TransitRoute (code) " + pattern.code);
			}
		}
	}
}
