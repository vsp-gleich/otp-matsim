package otp;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opentripplanner.routing.edgetype.OnboardEdge;
import org.opentripplanner.routing.edgetype.PatternHop;
import org.opentripplanner.routing.edgetype.PreAlightEdge;
import org.opentripplanner.routing.edgetype.PreBoardEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTransitLink;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ReadGraph implements Runnable {

    private GraphService graphService;
    private CoordinateTransformation ct;

    public Scenario getScenario() {
        return scenario;
    }

    private Scenario scenario;

    public ReadGraph(GraphService graphService, CoordinateTransformation ct) {
        this.graphService = graphService;
        this.ct = ct;
    }

    public void run() {
        Config ptConfig = ConfigUtils.createConfig();
        ptConfig.scenario().setUseTransit(true);
        ptConfig.scenario().setUseVehicles(true);
        
        scenario = ScenarioUtils.createScenario(ptConfig);
        extractStreetNetwork(scenario);
        extractPtNetwork(scenario);
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

    private void extractPtNetwork(Scenario scenario){
        Network network = scenario.getNetwork();
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
        		TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(
        				Id.create(stopId, TransitStopFacility.class),
        				coord,
        				true);
        		transitStopFacility.setLinkId(linkId);
        		scenario.getTransitSchedule().addStopFacility(transitStopFacility);
        	}
        }
        for(Vertex v: graphService.getGraph().getVertices()){
        	if(v instanceof TransitStop){
        		TransitStop departureStop = (TransitStop) v;
        		for(Edge e: v.getOutgoing()){
        			/* skip edges between the Transit stop vertex and the departure vertex of a pt line (PatternStopVertex) */
        			if(e instanceof PreBoardEdge){
        				Vertex allLinesDepartVertex = e.getToVertex();
        				for(Edge departureEdge: allLinesDepartVertex.getOutgoing()){
//        					should be always true, but otp's network model might change or might be less strict than it appears
        					if(departureEdge instanceof TransitBoardAlight && 
        							departureEdge.getToVertex().getOutgoing().size() == 1){
        						Edge patternHop = departureEdge.getToVertex().getOutgoing().iterator().next();
//            					should be always true, but otp's network model might change or might be less strict than it appears
        						if(patternHop instanceof PatternHop){
            						Vertex lineArrivalVertex = departureEdge.getToVertex().getOutgoing().iterator().next().getToVertex();
            						for(Edge arrivalTransitBoardAlight: lineArrivalVertex.getOutgoing()){        							
            							if(arrivalTransitBoardAlight instanceof TransitBoardAlight){
            								Vertex allLinesArrivalVertex = arrivalTransitBoardAlight.getToVertex();
//            	        					should be always true, but otp's network model might change or might be less strict than it appears
            	        					if(allLinesArrivalVertex.getOutgoing().size() == 1){
            	        						Edge prealight = allLinesArrivalVertex.getOutgoing().iterator().next();
            	        						if(prealight instanceof PreAlightEdge){
            	        							Vertex arrivalStopVertex = prealight.getToVertex();
            	        							if(arrivalStopVertex instanceof TransitStop){
            	        								TransitStop arrivalStop = (TransitStop) arrivalStopVertex;
            	        		        				System.out.println("Transit link " + patternHop.getId() + " from " + departureStop.getStopId().getId() + " to " + arrivalStop.getStopId().getId());
            	        		                        Node fromNode = network.getNodes().get(Id.create(departureStop.getStopId().getId(), Node.class));
            	        		                        Node toNode = network.getNodes().get(Id.create(arrivalStop.getStopId().getId(), Node.class));
            	        		                        Link l = network.getFactory().createLink(Id.create(patternHop.getId(), Link.class), fromNode, toNode);
            	        		                        Set<String> allowedModes = new HashSet<String>();
            	        		                        allowedModes.add("bus");
            	        		                        allowedModes.add("train");
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

}
