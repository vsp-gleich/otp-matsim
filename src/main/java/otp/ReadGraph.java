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
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTransitLink;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.TransitStop;

import java.util.ArrayList;
import java.util.Collection;

public class ReadGraph implements Runnable {

    private GraphService graphService;
    private CoordinateTransformation ct;

    public Scenario getStreetNetworkScenario() {
        return streetNetworkScenario;
    }

    public Scenario getDummyPtScenario() {
        return dummyPtScenario;
    }

    private Scenario streetNetworkScenario;
    private Scenario dummyPtScenario;

    public ReadGraph(GraphService graphService, CoordinateTransformation ct) {
        this.graphService = graphService;
        this.ct = ct;
    }

    public void run() {
        streetNetworkScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        extractStreetNetwork(streetNetworkScenario);

        Config ptConfig = ConfigUtils.createConfig();
        ptConfig.scenario().setUseTransit(true);
        ptConfig.scenario().setUseVehicles(true);
        dummyPtScenario = ScenarioUtils.createScenario(ptConfig);
        Collection<TransitStop> transitStops = getTransitStops();
        for (TransitStop transitStop : transitStops) {
            String stopId = transitStop.getStopId().getId();
            Coord coord = ct.transform(new CoordImpl(transitStop.getX(), transitStop.getY()));
            Node node = dummyPtScenario.getNetwork().getFactory().createNode(
                    Id.createNodeId(stopId),
                    coord);
            dummyPtScenario.getNetwork().addNode(node);
            Id<Link> linkId = Id.createLinkId(stopId);
            dummyPtScenario.getNetwork().addLink(dummyPtScenario.getNetwork().getFactory().createLink(
                    linkId,
                    node,
                    node));
            TransitStopFacility transitStopFacility = dummyPtScenario.getTransitSchedule().getFactory().createTransitStopFacility(
                    Id.create(stopId, TransitStopFacility.class),
                    coord,
                    true);
            transitStopFacility.setLinkId(linkId);
            dummyPtScenario.getTransitSchedule().addStopFacility(transitStopFacility);
        }
    }

    private void extractStreetNetwork(Scenario scenario) {
        Network network = scenario.getNetwork();
        for (Vertex v : graphService.getGraph().getVertices()) {
            if (v instanceof IntersectionVertex) {
                // Can be an OSM node, but can also be a split OSM way to insert a transit stop.
                Node n = network.getFactory().createNode(Id.create(v.getIndex(), Node.class), ct.transform(new CoordImpl(v.getX(), v.getY())));
                network.addNode(n);
            }
            System.out.println(v + v.getClass().toString());
        }
        int i = 0;
        for (Vertex v : graphService.getGraph().getVertices()) {
            if (v instanceof IntersectionVertex) {
                for (Edge e : v.getOutgoing()) {
                    if (e instanceof StreetEdge) {
                        Node fromNode = network.getNodes().get(Id.create(e.getFromVertex().getIndex(), Node.class));
                        Node toNode = network.getNodes().get(Id.create(e.getToVertex().getIndex(), Node.class));
                        Link l = network.getFactory().createLink(Id.create(e.getFromVertex().getIndex() + "_" + e.getToVertex().getIndex()+ "_" + i++, Link.class), fromNode, toNode);
                        network.addLink(l);
                    } else if (e instanceof StreetTransitLink) {
                        // Found a street transit link
                    }
                }
            }
        }
    }

    private Collection<TransitStop> getTransitStops() {
        Collection<TransitStop> transitStops = new ArrayList<>();
        for (Vertex v : graphService.getGraph().getVertices()) {
            if (v instanceof TransitStop) {
                transitStops.add(((TransitStop) v));
            }
        }
        return transitStops;
    }

}
