package rfc;

import stm.SyntaxCsv;
import stm.TBaseGenerator;
import stm.Utils;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.TimeEvent;
import org.eclipse.uml2.uml.TimeExpression;
import org.eclipse.uml2.uml.State;
import org.eclipse.uml2.uml.FinalState;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Pseudostate;
import org.eclipse.uml2.uml.PseudostateKind;
import org.eclipse.uml2.uml.Transition;
import org.eclipse.uml2.uml.Trigger;
import org.eclipse.uml2.uml.Event;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.OpaqueExpression;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.OpaqueBehavior;
import org.eclipse.uml2.uml.ValueSpecification;
import org.eclipse.uml2.uml.Behavior;
import org.eclipse.uml2.uml.BehavioredClassifier;
import org.eclipse.uml2.uml.CallEvent;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.SignalEvent;
import org.eclipse.uml2.uml.Region;
import org.eclipse.uml2.uml.Signal;
import org.eclipse.uml2.uml.Vertex;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.gmf.runtime.notation.Node;
import org.eclipse.gmf.runtime.notation.Edge;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gmf.runtime.notation.Bounds;
import org.eclipse.gmf.runtime.notation.RelativeBendpoints;

//import org.eclipse.gmf.runtime.notation.RelativeBendpoint;
// ... other imports (java.io.Writer, java.util.*, etc.)
import org.eclipse.gmf.runtime.notation.datatype.RelativeBendpoint;

public class RStmGenerator extends TBaseGenerator {
    private List<StateMachine> m_sortedStmDgrs = new ArrayList<>();
    private StateMachine m_iMainStm = null;
    private StateMachine m_stmRoot = null;
    private Transition m_originTrans = null;
    // Diagram map for geometry, e.g., mapping StateMachine to its Diagram (if available)
    private Map<StateMachine, Diagram> m_diagramMap = new HashMap<>();

    /**
     * Constructor
     * @param stxCsv
     * @param umlClass (BehavioredClassifier, e.g. Class)
     * @param writer
     */
    public RStmGenerator(SyntaxCsv stxCsv, Classifier umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
        // Collect all state machines owned by this class (including submachine diagrams)
        if (umlClass instanceof BehavioredClassifier) {
	        for (Behavior behavior : ((BehavioredClassifier)umlClass).getOwnedBehaviors()) {
	            if (behavior instanceof StateMachine) {
	                m_sortedStmDgrs.add((StateMachine) behavior);
	            }
	        }
        }
        // Sort state machines such that submachine diagrams come before their parent (main) state machine
        Collections.sort(m_sortedStmDgrs, new Comparator<StateMachine>() {
            @Override
            public int compare(StateMachine o1, StateMachine o2) {
                if (isSubmachineOf(o1, getTopVertices(o2))) return -1;
                if (isSubmachineOf(o2, getTopVertices(o1))) return 1;
                return 0;
            }
        });
        if (!m_sortedStmDgrs.isEmpty()) {
            m_iMainStm = m_sortedStmDgrs.get(m_sortedStmDgrs.size() - 1);
        }
    }

    // Helper to get top-level vertices of a StateMachine (vertices in its primary region)
    private Collection<Vertex> getTopVertices(StateMachine stm) {
        if (stm.getRegions().isEmpty()) return Collections.emptyList();
        return stm.getRegions().get(0).getSubvertices();
    }

    /** 
     * Determine if a given state machine is used as a submachine within a collection of vertices 
     */
    private boolean m_bResult = false;
    private boolean isSubmachineOf(StateMachine subStm, Collection<Vertex> vertices) {
        m_bResult = false;
        // Traverse through states to see if any submachine reference matches subStm
        new StateDeepTraverser() {
            protected void checkState(State state, State container, int rgnIndex) {
                if (state.getSubmachine() != null) {
                    if (state.getSubmachine() == subStm) {
                        m_bResult = true;
                    } else {
                        // Recurse into submachine’s top-level vertices
                        StateMachine nestedStm = state.getSubmachine();
                        if (isSubmachineOf(subStm, nestedStm.getRegions().get(0).getSubvertices())) {
                            m_bResult = true;
                        }
                    }
                }
            }
        }.start(vertices);
        return m_bResult;
    }
    
	/**
	 * makeIndent
	 * @param indent
	 * @return
	 */
	private String makeIndent(int indent) {
		indent+=2;
		if (indent > 0) {
			return String.join("", Collections.nCopies(indent * 4, " "));
		}
		return "";
	} 
	
	/**
	 * makeRegionName
	 * @param iState
	 * @param rgnIndex
	 * @return
	 */
	private String makeRgnName(State iState, int rgnIndex) {
		return rgnIndex == 0 ? iState.getName() : iState.getName() + "Rgn" + rgnIndex;
	}
	

    /**
     * Extract the event trigger name of a Transition (first trigger's event or trigger name).
     */
    private String getEventName(Transition trans) {
        String eventName = "";
        if (!trans.getTriggers().isEmpty()) {
            Trigger trigger = trans.getTriggers().get(0);
            Event ev = trigger.getEvent();
            if (ev != null && ev.getName() != null && !ev.getName().isEmpty()) {
                // Use event's own name if defined
                eventName = ev.getName();
            } else if (ev instanceof SignalEvent) {
                SignalEvent sigEv = (SignalEvent) ev;
                if (sigEv.getSignal() != null) {
                    eventName = sigEv.getSignal().getName();
                }
            } else if (ev instanceof CallEvent) {
                CallEvent callEv = (CallEvent) ev;
                if (callEv.getOperation() != null) {
                    eventName = callEv.getOperation().getName();
                }
            } 
            if (eventName.isEmpty() && trigger.getName() != null) {
                // Fallback to trigger name if event has no name
                eventName = trigger.getName();
            }
        }
        return eventName.trim();
    }

    /**
     * Extract the guard condition text for a Transition.
     */
    private String getGuardText(Transition trans) {
        String guardText = "";
        Constraint guard = trans.getGuard();
        if (guard != null && guard.getSpecification() != null) {
            ValueSpecification spec = guard.getSpecification();
            if (spec instanceof OpaqueExpression) {
                OpaqueExpression expr = (OpaqueExpression) spec;
                if (!expr.getBodies().isEmpty()) {
                    guardText = expr.getBodies().get(0);
                }
            } else {
                // For simplicity, handle LiteralString or other specifications
                try {
                    guardText = spec.stringValue();
                } catch (Exception e) {
                    guardText = "";
                }
            }
        }
        return guardText.trim();
    }

    /**
     * Extract the effect action text for a Transition.
     */
    private String getActionText(Transition trans) {
        String actionText = "";
        if (trans.getEffect() instanceof OpaqueBehavior) {
            OpaqueBehavior effect = (OpaqueBehavior) trans.getEffect();
            if (effect != null && !effect.getBodies().isEmpty()) {
                actionText = effect.getBodies().get(0);
            }
        }
        // If the effect is another kind of behavior, we could handle accordingly (not needed if not present).
        return actionText.trim();
    }

    /**
     * Get internal transitions of a State (those that do not exit the state). 
     * In UML2, these are transitions with kind == INTERNAL.
     */
    private Collection<Transition> getInternalTransitions(State state) {
        List<Transition> internals = new ArrayList<>();
        // A state’s internal transitions are not explicitly separated in UML model; 
        // we identify them as self-transitions with kind INTERNAL
        for (Transition t : state.getOutgoings()) {
            if (t.getKind() == org.eclipse.uml2.uml.TransitionKind.INTERNAL_LITERAL) {
                internals.add(t);
            }
        }
        return internals;
    }

    // Inner class for depth-first traversal of region 0 (single-region) state hierarchy
    private class StateDeepTraverserRgn0 {
        protected int m_level = 0;
        protected void checkPseudostate(Pseudostate pseudostate, State container) {}
        protected void checkStateBfr(State state, State container) {}
        protected void checkState(State state, State container) {}
        protected void traverse(Vertex vtx, State container) {
            m_level++;
            if (vtx instanceof State) {
                State state = (State) vtx;
                try {
                    checkStateBfr(state, container);
                    // traverse region 0 of this composite state
                    if (!state.getRegions().isEmpty() && !state.getRegions().get(0).getSubvertices().isEmpty()) {
                        for (Vertex subVtx : state.getRegions().get(0).getSubvertices()) {
                            traverse(subVtx, state);
                        }
                    }
                    checkState(state, container);
                } catch (Exception e) {
                    // Handle exceptions if needed (none expected in UML2 traversal)
                }
            } else if (vtx instanceof Pseudostate) {
                checkPseudostate((Pseudostate) vtx, container);
            } else {
                System.out.println("★★★ERROR★★★ Traverse to an unknown vertex type");
            }
            m_level--;
        }
        public StateDeepTraverserRgn0() {}
        public void start(Collection<Vertex> vertices) {
            for (Vertex v : vertices) {
                traverse(v, null);
            }
        }
    }

    // Inner class for depth-first traversal handling multiple regions (for orthogonal states)
    private class StateDeepTraverser {
        protected int m_level = 0;
        protected void checkPseudostate(Pseudostate pseudostate, State container, int rgnIndex) {}
        protected void checkRegionBfr(State state, int subRgnIndex, State container, int rgnIndex) {}
        protected void checkRegion(State state, int subRgnIndex, State container, int rgnIndex) {}
        protected void checkState(State state, State container, int rgnIndex) {}
        protected void checkStateBfr(State state, State container, int rgnIndex) {}
        protected void traverse(Vertex vtx, State container, int rgnIndex) {
            m_level++;
            if (vtx instanceof State) {
                State state = (State) vtx;
                // Loop through all regions of this state
                for (int subRgnIdx = 0; subRgnIdx < state.getRegions().size(); subRgnIdx++) {
                    try {
                        if (subRgnIdx == 0) {
                            checkStateBfr(state, container, rgnIndex);
                        }
                        Region subRegion = state.getRegions().get(subRgnIdx);
                        if (!subRegion.getSubvertices().isEmpty()) {
                            if (subRgnIdx > 0) {
                                checkRegionBfr(state, subRgnIdx, container, rgnIndex);
                            }
                            for (Vertex subVtx : subRegion.getSubvertices()) {
                                traverse(subVtx, state, subRgnIdx);
                            }
                            if (subRgnIdx > 0) {
                                checkRegion(state, subRgnIdx, container, rgnIndex);
                            }
                        }
                        if (subRgnIdx == 0) {
                            checkState(state, container, rgnIndex);
                        }
                    } catch (Exception e) {
                        // Break out if region index out of bounds (not expected in for-loop)
                        break;
                    }
                }
            } else if (vtx instanceof Pseudostate) {
                checkPseudostate((Pseudostate) vtx, container, rgnIndex);
            } else {
                System.out.println("★★★ERROR★★★ Traverse to an unknown vertex type");
            }
            m_level--;
        }
        public void start(Collection<Vertex> vertices) {
            for (Vertex v : vertices) {
                traverse(v, null, 0);
            }
        }
    }

    // ... (Other inner classes like event traversers, omitted for brevity)

    public StateMachine getMainStm() {
        return m_iMainStm;
    }

    // Example EventDeepTraverser using UML2 triggers
    private class EventDeepTraverser extends StateDeepTraverser {
        protected void checkState(State state, State container, int rgnIndex) {
            // If submachine state, recursively traverse its submachine’s vertices
            if (state.getSubmachine() != null) {
                StateMachine subMachine = state.getSubmachine();
                new EventDeepTraverser().start(getTopVertices(subMachine));
                // Collect events from submachine’s transitions
                for (Transition t : subMachine.getRegions().get(0).getTransitions()) {
                    String evName = getEventName(t);
                    if (!evName.isEmpty() && !uniqueSortedEvents.contains(evName)) {
                        uniqueSortedEvents.add(evName);
                    }
                }
            }
            // Collect events from internal transitions of this state
            for (Transition t : getInternalTransitions(state)) {
                String evName = getEventName(t);
                if (!evName.isEmpty() && !uniqueSortedEvents.contains(evName)) {
                    uniqueSortedEvents.add(evName);
                }
            }
        }
    }

    private List<String> uniqueSortedEvents = new ArrayList<>();
    public void printEventDecl() throws IOException, Exception {
        // Print event declaration (enum)
        m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "name"),
                                 m_iClass.getName(),
                                 m_iClass.getName(),
                                 m_iMainStm != null ? m_iMainStm.getName() : "" ));
        // Traverse submachines and states to gather events
        new EventDeepTraverser().start(getTopVertices(m_iMainStm));
        // Also collect events of main state machine transitions
        if (m_iMainStm != null && !m_iMainStm.getRegions().isEmpty()) {
            for (Transition t : m_iMainStm.getRegions().get(0).getTransitions()) {
                String evName = getEventName(t);
                if (!evName.isEmpty() && !uniqueSortedEvents.contains(evName)) {
                    uniqueSortedEvents.add(evName);
                }
            }
        }
        Collections.sort(uniqueSortedEvents);
        System.out.println(makeIndent(indent) + "class Events(Enum):");
        indent++;
        String path = m_stxCsv.get(indent, "event_decl", "ext1st");
        for (String key : uniqueSortedEvents) {
            System.out.println(makeIndent(indent) + key + " = auto()");
            m_writer.write(Utils.get(path, key, m_iClass.getName(), m_iClass.getName(), "", "", ""));
            path = m_stxCsv.get(indent, "event_decl", "extnxt");
        }
        indent--;
        m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "end"),
                                 m_iClass.getName(),
                                 m_iClass.getName(),
                                 m_iMainStm != null ? m_iMainStm.getName() : "" ));
    }

    public void printStmInitialization() throws IOException, Exception {
        m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "extnxt"),
                                 m_iMainStm.getName(),
                                 m_iMainStm.getName(), "", "", "", ""));
    }

    public void printMainStmDeclaration() throws IOException, Exception {
        m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "ext1st"),
                                 m_iMainStm.getName(),
                                 m_iMainStm.getName(), "", "", "", ""));
    }

    // ... (Other methods for printing state machine implementations, states, etc., similarly adapted)

    /**
     * Determine if a transition is external (exits the least common ancestor state).
     * Uses GMF Notation to check if transition points lie outside the LCA state's bounds.
     */
    private boolean checkIfExternalTrans(Transition lastTrans) {
        Vertex targetVertex = lastTrans.getTarget();
        try {
            Vertex commonStateVertex = null;
            // Determine lowest common ancestor (LCA) state between m_originTrans.getSource() and targetVertex
            Vertex sourceVertex = m_originTrans.getSource();
            // Climb up from target to source
            Vertex traverse = targetVertex;
            while (traverse != sourceVertex) {
                Region region = traverse.getContainer(); // region containing this vertex
                if (region != null && region.getState() != null) {
                    traverse = region.getState();
                    if (traverse == sourceVertex) {
                        commonStateVertex = traverse;
                        break;
                    }
                } else {
                    break; // reached top without finding source
                }
            }
            // Climb up from source to target
            traverse = sourceVertex;
            while (traverse != targetVertex) {
                Region region = traverse.getContainer();
                if (region != null && region.getState() != null) {
                    traverse = region.getState();
                    if (traverse == targetVertex) {
                        commonStateVertex = traverse;
                        break;
                    }
                } else {
                    break;
                }
            }
            if (commonStateVertex != null && commonStateVertex instanceof State) {
                State commonState = (State) commonStateVertex;
                // Retrieve diagram and shapes for geometry calculation
                Diagram diag = m_diagramMap.get(commonState.containingStateMachine());
                if (diag != null) {
                    // Find notation Node for commonState and Edge for transitions
                    Node stateNode = findDiagramNode(commonState, diag);
                    Edge originEdge = findDiagramEdge(m_originTrans, diag);
                    Edge targetEdge = findDiagramEdge(lastTrans, diag);
                    if (stateNode != null && originEdge != null && targetEdge != null) {
                        Bounds bounds = (Bounds) stateNode.getLayoutConstraint();
                        double minX = bounds.getX();
                        double maxX = bounds.getX() + bounds.getWidth();
                        double minY = bounds.getY();
                        double maxY = bounds.getY() + bounds.getHeight();
                        // Check if any bendpoint of either transition lies outside commonState bounds
                        if (isEdgeOutsideBounds(originEdge, minX, maxX, minY, maxY) 
                                || isEdgeOutsideBounds(targetEdge, minX, maxX, minY, maxY)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // Helper to find a Node in the diagram corresponding to a given element
    private Node findDiagramNode(org.eclipse.uml2.uml.Element element, Diagram diagram) {
        for (Object obj : diagram.getChildren()) {
            if (obj instanceof Node) {
                Node node = (Node) obj;
                if (node.getElement() == element) return node;
            }
        }
        return null;
    }
    // Helper to find an Edge in the diagram for a given transition
    private Edge findDiagramEdge(Transition transition, Diagram diagram) {
        for (Object obj : diagram.getEdges()) {
            if (obj instanceof Edge) {
                Edge edge = (Edge) obj;
                if (edge.getElement() == transition) return edge;
            }
        }
        return null;
    }
    // Helper to check if any points of an edge lie outside given bounds
    private boolean isEdgeOutsideBounds(Edge edge, double minX, double maxX, double minY, double maxY) {
        if (edge.getBendpoints() instanceof RelativeBendpoints) {
            RelativeBendpoints bp = (RelativeBendpoints) edge.getBendpoints();
            for (Object obj : bp.getPoints()) {
            	if (!(obj instanceof RelativeBendpoint))
            		continue;
            	RelativeBendpoint rb = (RelativeBendpoint)obj;
                // Compute absolute coordinates roughly (approximate source and target anchors as edge ends):
                int absX = rb.getSourceX();
                int absY = rb.getSourceY();
                // Here we might add anchor location, but assume relative small offsets suffice:
                if (absX < minX || absX > maxX || absY < minY || absY > maxY) {
                    return true;
                }
                // Also consider target offsets:
                absX = rb.getTargetX();
                absY = rb.getTargetY();
                if (absX < minX || absX > maxX || absY < minY || absY > maxY) {
                    return true;
                }
            }
        }
        return false;
    }

    public void printStmImpls() throws IOException, Exception {
        // Iterate over each StateMachine owned by the class (BehavioredClassifier)
        BehavioredClassifier classifier = (BehavioredClassifier) m_iClass;
        for (Behavior behavior : classifier.getOwnedBehaviors()) {
            if (!(behavior instanceof StateMachine)) continue;
            StateMachine stateMachine = (StateMachine) behavior;
            m_stmRoot = stateMachine;  // set current state machine context

            // Prepare names and definition similar to Astah version
            String rgnName = stateMachine.getName();
            String rgnDgrName = stateMachine.getName();  // using stateMachine name as diagram name
            String rgnDefinition = getElementDocumentation(stateMachine);  // retrieve documentation if any

            // Get top-level region vertices (states/pseudostates in the state machine)
            Region topRegion = stateMachine.getRegions().isEmpty() ? null : stateMachine.getRegions().get(0);
            List<Vertex> rgnVertices = (topRegion != null) ? topRegion.getSubvertices() : Collections.emptyList();

            System.out.println(makeIndent(indent) + "# Statemachine sub-class");
            System.out.println(makeIndent(indent) + "class " + rgnDgrName + "(ParallelStatemachine):");

            // Capture state definitions for all regions (including nested regions) into a temporary writer
            StringWriter tempWriter = new StringWriter();
            Writer originalWriter = m_writer;
            m_writer = tempWriter;
            // Traverse all composite states to print their nested state definitions
            for (Vertex v : rgnVertices) {
                traverseStateDefinitions(v);
            }
            // Also include top-level states definitions in the main region
            printStatesDefinition(rgnName, rgnDgrName, rgnDefinition, rgnVertices);
            m_writer = originalWriter;  // restore original writer

            // Write the beginning of the state machine class using template (statemachine.begin)
            m_writer.write(Utils.get(
                m_stxCsv.get(indent, "statemachine", "begin"),
                rgnDgrName,                      // name (state machine class name)
                m_iClass.getName(),              // type (context class name)
                rgnName + "Hsm",                 // container (state machine instance name, e.g., with HSM suffix)
                tempWriter.toString(),           // value (state definitions block)
                "", "", 
                stateMachine.getName()           // scope (using stateMachine name as scope)
            ));
            indent++;

            // Print sub-state-machine and region declarations/initializations (if any parallel regions)
            String subStmAndRgnInitStr = printSubStmAndRgnDecls(rgnName, rgnDgrName, rgnDefinition, rgnVertices);

            // Generate nested Region classes for each composite state's regions (sub-regions)
            for (Vertex v : rgnVertices) {
                traverseRegionClasses(v, stateMachine);
            }
            // Also generate the class for the top-level region of this state machine
            System.out.println(makeIndent(indent) + "# Region sub-class");
            System.out.println(makeIndent(indent) + "class _" + rgnName + "Hsm(Statemachine):");
            indent++;
            printStmImpl(stateMachine, rgnName, rgnDgrName, rgnDefinition, rgnVertices);
            indent--;

            // Print state-machine API methods (event declarations, etc.) if needed
            printStmAPIs(stateMachine);

            indent--;
            // Write the end of the state machine class using template (statemachine.end)
            m_writer.write(Utils.get(
                m_stxCsv.get(indent, "statemachine", "end"),
                rgnDgrName,                // name
                m_iClass.getName(),        // type
                rgnName,                   // container
                subStmAndRgnInitStr,       // value (initialization strings for sub state machines/regions)
                "", "", 
                stateMachine.getName()     // scope
            ));
        }
    }

    /**
     * Helper to retrieve the "definition" or documentation of a UML element.
     * For EMF UML, we use the first owned comment as the definition text (if exists).
     */
    private String getElementDocumentation(Element element) {
        if (element == null) return "";
        if (!element.getOwnedComments().isEmpty()) {
            String doc = element.getOwnedComments().get(0).getBody();
            return (doc != null) ? doc : "";
        }
        return "";
    }

    /**
     * Recursively traverse vertices to collect and print state definitions for nested regions.
     * (This replaces the Astah StateDeepTraverser for state definitions.)
     */
    private void traverseStateDefinitions(Vertex vertex) throws IOException, Exception {
        if (vertex instanceof State) {
            State state = (State) vertex;
            // If this state has substates (i.e., is a composite state with one or more Regions)
            if (!state.getRegions().isEmpty()) {
                // Iterate each region in the composite state
                for (int subRgnIndex = 0; subRgnIndex < state.getRegions().size(); subRgnIndex++) {
                    Region subRegion = state.getRegions().get(subRgnIndex);
                    String subRgnName = makeRgnName(state, subRgnIndex);
                    String subRgnClassName = subRgnName + "Hsm";
                    String subRgnDef = getElementDocumentation(state);
                    List<Vertex> subVertices = subRegion.getSubvertices();
                    // Print state definitions for this sub-region
                    printStatesDefinition(subRgnName, subRgnClassName, subRgnDef, subVertices);
                    // Recurse into deeper nested regions
                    for (Vertex subV : subVertices) {
                        traverseStateDefinitions(subV);
                    }
                }
            }
        }
    }

    /**
     * Recursively traverse vertices to generate region sub-class implementations for composite states.
     * (Replaces the Astah StateDeepTraverser usage for region classes and transitions.)
     */
    private void traverseRegionClasses(Vertex vertex, StateMachine rootStm) throws IOException, Exception {
        if (vertex instanceof State) {
            State state = (State) vertex;
            if (!state.getRegions().isEmpty()) {
                // For each orthogonal region inside this composite state, generate a region class
                for (int subRgnIndex = 0; subRgnIndex < state.getRegions().size(); subRgnIndex++) {
                    Region subRegion = state.getRegions().get(subRgnIndex);
                    String subRgnName = makeRgnName(state, subRgnIndex);
                    String subRgnClassName = "_" + subRgnName + "Hsm";  // prefix with "_" for region class
                    String subRgnDef = getElementDocumentation(state);
                    List<Vertex> subVertices = subRegion.getSubvertices();
                    System.out.println(makeIndent(indent) + "# Region sub-class");
                    System.out.println(makeIndent(indent) + "class " + subRgnClassName + "(Statemachine):");
                    indent++;
                    // Generate the implementation (states, transitions) for this sub-region
                    printStmImpl(rootStm, subRgnName, subRgnName + "Hsm", subRgnDef, subVertices);
                    indent--;
                    // Recurse deeper into any nested composite states within this region
                    for (Vertex subV : subVertices) {
                        traverseRegionClasses(subV, rootStm);
                    }
                }
            }
        }
    }
    
    public Region findRegionByName(StateMachine rootStateMachine, String targetName) {
    	if (rootStateMachine == null) {
    		return null;
    	}
        // Search top-level regions of the state machine
        for (Region region : rootStateMachine.getRegions()) {
            if (targetName.equals(region.getName()) || targetName.equals(region.getLabel())) {
                return region;
            }
        }

        // Recursively search composite states for matching sub-regions
        for (Region region : rootStateMachine.getRegions()) {
            for (Vertex vertex : region.getSubvertices()) {
                if (vertex instanceof State) {
                    State compositeState = (State) vertex;
                    for (Region subRegion : compositeState.getRegions()) {
                        if (targetName.equals(subRegion.getName()) || targetName.equals(subRegion.getLabel())) {
                            return subRegion;
                        }
                        // Optionally go deeper (recursive) if needed
                        Region found = findRegionByName(compositeState.getSubmachine(), targetName);
                        if (found != null) return found;
                    }
                }
            }
        }

        // Not found
        return null;
    }    

    /**
     * Generate the implementation of a state machine region: transitions, state handlers, etc.
     * This corresponds to the original printStmImpl, adapted for UML2 API.
     */
    private void printStmImpl(StateMachine stmRoot, String rgnName, String rgnClassName,
                              String rgnDefinition, List<Vertex> vertices) throws IOException, Exception {
        // Loop through each transition originating from any vertex in this region
        Region currentRegion = null;
        if (stmRoot.getRegions().size() > 0 && stmRoot.getRegions().get(0).getSubvertices().equals(vertices)) {
            // If vertices list is the top region's vertices
            currentRegion = stmRoot.getRegions().get(0);
        } else {
            // Otherwise, find which region these vertices belong to (by name or context)
            // (For simplicity, we assume rgnName identifies the region within stmRoot or a composite state)
            currentRegion = findRegionByName(stmRoot, rgnName);
        }
        if (currentRegion == null) {
            return; // no region to process
        }

        // Iterate all transitions in this region
        for (Transition transition : currentRegion.getTransitions()) {
            Vertex source = transition.getSource();
            Vertex target = transition.getTarget();
            String guardStr = "";
            String eventName = "";
            String actionCode = "";

            // Determine event trigger name (if any)
            if (!transition.getTriggers().isEmpty()) {
                Trigger trigger = transition.getTriggers().get(0);
                Event event = trigger.getEvent();
                if (event instanceof CallEvent) {
                    // For CallEvents, use the operation name as event name
                    CallEvent callEvent = (CallEvent) event;
                    if (callEvent.getOperation() != null) {
                        eventName = callEvent.getOperation().getName();
                    }
                } else if (event instanceof SignalEvent) {
                    // For SignalEvents, use the signal name
                    SignalEvent sigEvent = (SignalEvent) event;
                    if (sigEvent.getSignal() != null) {
                        eventName = sigEvent.getSignal().getName();
                    }
                } else if (event != null && event.getName() != null) {
                    // Other event types (TimeEvent, ChangeEvent, etc.) or named triggers
                    eventName = event.getName();
                } else if (trigger.getName() != null) {
                    // If the trigger itself is named (fallback)
                    eventName = trigger.getName();
                }
            }
            // Determine guard condition string (if any)
            Constraint guard = transition.getGuard();
            if (guard != null && guard.getSpecification() != null) {
                ValueSpecification spec = guard.getSpecification();
                if (spec instanceof OpaqueExpression) {
                    OpaqueExpression expr = (OpaqueExpression) spec;
                    if (!expr.getBodies().isEmpty()) {
                        guardStr = expr.getBodies().get(0).trim();
                    }
                } else if (spec.stringValue() != null) {
                    guardStr = spec.stringValue().trim();
                }
            }
            // Determine effect action code (if any)
            Behavior effect = transition.getEffect();
            if (effect instanceof OpaqueBehavior) {
                OpaqueBehavior ob = (OpaqueBehavior) effect;
                if (!ob.getBodies().isEmpty()) {
                    actionCode = ob.getBodies().get(0).trim();
                }
            } else if (effect != null) {
                // If effect is some other Behavior, use its name as a placeholder
                actionCode = (effect.getName() != null) ? effect.getName() : "";
            }

            // Now generate code for this transition similar to original
            if (target instanceof State) {
                // Target is a state
                if (!guardStr.isEmpty()) {
                    // If a guard is present, print an if/else block
                    System.out.println(makeIndent(indent) + (guardStr.equalsIgnoreCase("else") ? "else:" : ("if " + guardStr + ":")));
                    // Write the opening of the branch (guard condition) using templates
                    m_writer.write(Utils.get(
                        m_stxCsv.get(indent, "branch", guardStr.equalsIgnoreCase("else") ? "begin" : "ext1st"),
                        guardStr,                     // name (guard condition or "else")
                        m_iClass.getName(),           // type
                        "",                          // container
                        guardStr,                     // value (reuse guard as value)
                        collectActions(indent, actionCode),  // modifier (actions code)
                        "", 
                        stmRoot.getName()             // scope (state machine name)
                    ));
                    indent++;
                    // Recurse or handle nested transitions (e.g., if target has outgoing transitions or pseudostates)
                    TraverseTransition(stmRoot, rgnName, vertices, transition);
                    indent--;
                } else {
                    // No guard: directly handle transition
                    TraverseTransition(stmRoot, rgnName, vertices, transition);
                }
            } else {
                // Target is a pseudostate (e.g., Choice, Junction, Join, etc.)
                if (!guardStr.isEmpty()) {
                    // Guard present for pseudostate transition
                    System.out.println(makeIndent(indent) + (guardStr.equalsIgnoreCase("else") ? "else:" : ("if " + guardStr + ":")));
                    m_writer.write(Utils.get(
                        m_stxCsv.get(indent, "branch", guardStr.equalsIgnoreCase("else") ? "begin" : "ext1st"),
                        guardStr, 
                        m_iClass.getName(),
                        "",
                        guardStr,
                        collectActions(indent, actionCode),
                        "",
                        stmRoot.getName()
                    ));
                    indent++;
                }
                // Special handling for joins: if this transition's source is a Join pseudostate, combine conditions
                if (source instanceof Pseudostate && ((Pseudostate) source).getKind() == PseudostateKind.JOIN_LITERAL) {
                    Pseudostate joinPseudo = (Pseudostate) source;
                    System.out.println(makeIndent(indent) + "# begin joining");
                    boolean firstCond = true;
                    StringBuilder isInConditions = new StringBuilder();
                    // Iterate all incoming transitions to the join pseudostate
                    for (Transition incoming : joinPseudo.getIncomings()) {
                        if (incoming == transition) continue;  // skip the current transition itself
                        // Only consider completion transitions from States (no trigger events on incoming)
                        if (incoming.getSource() instanceof State) {
                            // Ensure incoming has no event trigger
                            if (incoming.getTriggers().isEmpty()) {
                                State sourceState = (State) incoming.getSource();
                                // Find which region's state machine contains this source state
                                String targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot, sourceState);
                                String targetMachineRef;
                                if (targetMachineName.equals(rgnName + "Hsm")) {
                                    targetMachineRef = "self";
                                } else {
                                    targetMachineRef = "self.main." + targetMachineName;
                                }
                                if (firstCond) {
                                    System.out.println(makeIndent(indent) + "if IsIn(" + targetMachineRef + ".currentState, " 
                                                       + stmRoot.getName() + "." + sourceState.getName() + ") \\");
                                    // Start of combined condition (ext1st template)
                                    isInConditions.append(Utils.get(
                                        m_stxCsv.get(indent, "trans_action", "ext1st"),
                                        sourceState.getName(),
                                        m_iClass.getName(),
                                        targetMachineName,
                                        sourceState.getName(),
                                        targetMachineName,
                                        transition.getName(),  // description (using transition name or definition)
                                        stmRoot.getName()
                                    ));
                                    firstCond = false;
                                } else {
                                    System.out.println(makeIndent(indent) + " and IsIn(" + targetMachineRef + ".currentState, " 
                                                       + stmRoot.getName() + "." + sourceState.getName() + ") \\");
                                    // Subsequent condition (extnxt template)
                                    isInConditions.append(Utils.get(
                                        m_stxCsv.get(indent, "trans_action", "extnxt"),
                                        sourceState.getName(),
                                        m_iClass.getName(),
                                        targetMachineName,
                                        sourceState.getName(),
                                        targetMachineName,
                                        transition.getName(),
                                        stmRoot.getName()
                                    ));
                                }
                            } else {
                                System.out.println("★★★ERROR★★★: Joining from other regions cannot have an event trigger.");
                            }
                        }
                    }
                    // Once combined conditions are built, output the join transition branch
                    if (joinPseudo.getOutgoings().size() == 1) {  // join should have exactly one outgoing transition
                        System.out.println(makeIndent(indent) + ":");
                        // Write the combined condition branch to output (branch.ext1st template)
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "branch", "ext1st"),
                            isInConditions.toString(),
                            m_iClass.getName(),
                            "",
                            isInConditions.toString(),
                            collectActions(indent, actionCode),
                            "",
                            stmRoot.getName()
                        ));
                        indent++;
                        // Print the single outgoing transition of the join pseudostate
                        Transition outgoingTrans = joinPseudo.getOutgoings().get(0);
                        printTransition(stmRoot, rgnName, vertices, outgoingTrans);
                        indent--;
                        // Close the branch (branch.end template) unless it's an "else" branch of a choice with no guard
                        if (!(transition.getSource() instanceof Pseudostate &&
                              ((Pseudostate) transition.getSource()).getKind() == PseudostateKind.CHOICE_LITERAL &&
                              guardStr.isEmpty())) {
                            m_writer.write(Utils.get(
                                m_stxCsv.get(indent, "branch", "end"),
                                isInConditions.toString(),
                                m_iClass.getName(),
                                "",
                                isInConditions.toString(),
                                collectActions(indent, actionCode),
                                "",
                                stmRoot.getName()
                            ));
                        }
                    }
                } else {
                    // For non-join pseudostate transitions or after join handling, simply print the transition
                    printTransition(stmRoot, rgnName, vertices, transition);
                }
                if (!guardStr.isEmpty()) {
                    // If we opened a guard branch for pseudostate, close it (branch.end)
                    indent--;
                    m_writer.write(Utils.get(
                        m_stxCsv.get(indent, "branch", "end"),
                        guardStr,
                        m_iClass.getName(),
                        "",
                        guardStr,
                        collectActions(indent, actionCode),
                        "",
                        stmRoot.getName()
                    ));
                }
            }
        }
    }
    
    public void printStatesDefinition(
            String rgnName, String rgnDgrName, String rgnDefinition,
            java.util.List<Vertex> rgnVertices
        ) throws IOException, Exception {
        // Print debug header
        System.out.println(makeIndent(indent) + "# States definitions");

        // Prepare a temporary writer to collect state definitions
        StringWriter tempWriter = new StringWriter();
        Writer originalWriter = m_writer;
        m_writer = tempWriter;
        AtomicInteger autoId = new AtomicInteger(0);

        // Helper to process (possibly composite) states recursively
        java.util.function.BiConsumer<State,String> processState = new java.util.function.BiConsumer<State,String>() {
            public void accept(State state, String containerName) {
                try {
                    // Collect comments as description
                    StringBuilder commentBody = new StringBuilder();
                    for (Comment cmt : state.getOwnedComments()) {
                        if (cmt.getBody() != null) {
                            commentBody.append(cmt.getBody());
                        }
                    }
                    // Leaf state (no nested regions)
                    if (state.getRegions().isEmpty()) {
                        System.out.println(makeIndent(indent) 
                                           + state.getName() + " = MakeState(" + autoId + ")");
                        // Write a state_decl "name" entry with name, container, id, kind, description
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "state_decl", "name"),
                            state.getName(),
                            m_iClass.getName(),
                            containerName,
                            String.format("%2d", autoId.getOpaque()),
                            (state instanceof FinalState) ? "Final" : "Normal",
                            commentBody.toString(),
                            getStateMachineDiagram(m_stmRoot).getName()
                        ));
                        autoId.getAndIncrement();
                    }
                    // Composite state (has sub-vertices)
                    else {
                        // Collect substate names
                        String subStateNames = null;
                        for (Vertex sub : state.getRegions().get(0).getSubvertices()) {
                            if (sub instanceof State || sub instanceof Pseudostate) {
                                String subName = ((NamedElement)sub).getName();
                                if (subStateNames == null) {
                                    subStateNames = subName;
                                    m_writer.write(Utils.get(
                                        m_stxCsv.get(indent, "state_decl", "ext1st"),
                                        state.getName(),
                                        m_iClass.getName(),
                                        containerName,
                                        subName,
                                        String.format("%2d", autoId.getOpaque()),
                                        "",
                                        getStateMachineDiagram(m_stmRoot).getName()
                                    		));
                                } else {
                                    subStateNames += " | " + subName;
                                    m_writer.write(Utils.get(
                                        m_stxCsv.get(indent, "state_decl", "extnxt"),
                                        state.getName(),
                                        m_iClass.getName(),
                                        containerName,
                                        subName,
                                        String.format("%2d", autoId.getOpaque()),
                                        "",
                                        getStateMachineDiagram(m_stmRoot).getName()
                                    ));
                                }
                            }
                        }
                        // End of composite state's sub-vertex list
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "state_decl", "end"),
                            state.getName(),
                            m_iClass.getName(),
                            containerName,
                            "",
                            state.getName(),
                            rgnDefinition,
                            getStateMachineDiagram(m_stmRoot).getName()
                        ));
                        System.out.println(makeIndent(indent) 
                                           + state.getName() + " = " + subStateNames);
                        // Recurse into substates
                        for (Vertex sub : state.getRegions().get(0).getSubvertices()) {
                            if (sub instanceof State) {
                                accept((State)sub, state.getName());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        // Process each top-level vertex in the region
        for (Vertex vert : rgnVertices) {
            // Pseudostate (initial, history, fork, etc.)
            if (vert instanceof Pseudostate) {
                Pseudostate ps = (Pseudostate) vert;
                System.out.println(makeIndent(indent) 
                                   + ps.getName() + " = MakeState(" + autoId + ")");
                StringBuilder commentBody = new StringBuilder();
                for (Comment cmt : ps.getOwnedComments()) {
                    if (cmt.getBody() != null) {
                        commentBody.append(cmt.getBody());
                    }
                }
                // Use UML2 Pseudostate.getKind()
                String kind = ps.getKind().getLiteral();  // e.g. "Initial", "Fork", "History", etc.:contentReference[oaicite:2]{index=2}
                m_writer.write(Utils.get(
                    m_stxCsv.get(indent, "state_decl", "name"),
                    ps.getName(),
                    m_iClass.getName(),
                    rgnName,
                    String.format("%2d", autoId.getOpaque()),
                    kind,
                    commentBody.toString(),
                    getStateMachineDiagram(m_stmRoot).getName()
                ));
                autoId.getAndIncrement();
            }
            // State (could be composite or leaf; includes FinalState)
            else if (vert instanceof State) {
                State st = (State) vert;
                // Top-level states have the region name as container
                processState.accept(st, rgnName);
            }
        }

        // Restore original writer and write the region's 'begin' entry
        m_writer = originalWriter;
        m_writer.write(Utils.get(
            m_stxCsv.get(indent, "state_decl", "begin"),
            rgnName,
            m_iClass.getName(),
            rgnName + "Hsm",
            tempWriter.toString(),
            "",
            "",
            getStateMachineDiagram(m_stmRoot).getName()
        ));
    }
    
    public void printStmAPIs(StateMachine stateMachine) throws IOException {
        // Map each event name to a list of Events (or Transitions) with that name
        Map<String, List<Event>> eventsByName = new LinkedHashMap<>();
        if (!stateMachine.getRegions().isEmpty()) {
            // Get all transitions from the first region
            for (Transition transition : stateMachine.getRegions().get(0).getTransitions()) {
                for (Trigger trigger : transition.getTriggers()) {
                    Event event = trigger.getEvent();
                    String name;
                    // Derive a name based on Event type
                    if (event instanceof CallEvent) {
                        Operation op = ((CallEvent) event).getOperation();
                        name = (op != null) ? op.getName() : event.getName();
                    } else if (event instanceof SignalEvent) {
                        Signal sig = ((SignalEvent) event).getSignal();
                        name = (sig != null) ? sig.getName() : event.getName();
                    } else if (event instanceof TimeEvent) {
                        TimeExpression when = ((TimeEvent) event).getWhen();
                        if (when != null && when.getExpr() != null) {
                            // If the TimeEvent has an expression, use its value or string
                            ValueSpecification expr = when.getExpr();
                            name = expr.stringValue(); 
                            if (name == null || name.isEmpty()) {
                                name = event.getName();
                            }
                        } else {
                            name = event.getName();
                        }
                    } else {
                        name = event.getName();
                    }
                    // Group events by the derived name
                    eventsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(event);
                }
            }
        }
        // For each distinct event name, write an API block
        for (String eventName : eventsByName.keySet()) {
            // Example: write a template block for this event
            // (Utils.get might refer to a template key and parameters)
            try {
				m_writer.write(Utils.get("stmApiTemplate", eventName));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
    
    /**
     * Translated printSubStmAndRgnDecls method using UML2 (EMF) API.
     */
    public String printSubStmAndRgnDecls(
            String rgnName,
            String rgnDgrName,
            String rgnDefinition,
            List<Vertex> rgnVertices
    ) throws IOException, Exception {
        // Result string builder for extended entries
        StringBuilder result = new StringBuilder();

        // Print __init__ and parent constructor call
        System.out.println(makeIndent(indent) + "def __init__(self, _main, _parent):");
        indent++;
        System.out.println(makeIndent(indent) + "super().__init__(_main, _parent)");

        // Declare sub-machine and regions field for this region
        System.out.println(makeIndent(indent) + "# sub-machine and regions declaration");
        System.out.println(makeIndent(indent) + "self." + rgnName + "Hsm = self._" + rgnName + "Hsm(self, self.parent)");
        try {
            // Write region declaration (name and ext1st entries)
            m_writer.write(Utils.get(
                m_stxCsv.get(indent, "region", "name"),
                rgnName + "Hsm",            // name
                m_iClass.getName(),         // type
                rgnName + "Hsm",            // container
                "",                         // value
                rgnDefinition,              // modifier/description
                m_stmRoot.getName()         // scope (using state machine name)
            ));
            result.append(Utils.get(
                m_stxCsv.get(indent, "region", "ext1st"),
                rgnName + "Hsm",            // name
                m_iClass.getName(),         // type
                rgnName + "Hsm",            // container
                "",                         // value
                rgnDefinition,              // modifier/description
                m_stmRoot.getName()         // scope
            ));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Traverse vertices in this region
        for (Vertex vertex : rgnVertices) {
            // 1. Handle history pseudostates
            if (vertex instanceof Pseudostate) {
                Pseudostate pseudo = (Pseudostate) vertex;
                PseudostateKind kind = pseudo.getKind();
                if (kind == PseudostateKind.SHALLOW_HISTORY_LITERAL || kind == PseudostateKind.DEEP_HISTORY_LITERAL) {
                    System.out.println(makeIndent(indent) + "self." + pseudo.getName() + " = 0");
                    try {
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "history", "name"),
                            pseudo.getName(),        // name
                            m_iClass.getName(),      // type
                            "",                      // container
                            "",                      // value
                            rgnDefinition,           // modifier/description
                            m_stmRoot.getName()      // scope
                        ));
                        result.append(Utils.get(
                            m_stxCsv.get(indent, "history", "ext1st"),
                            pseudo.getName(),        // name
                            m_iClass.getName(),      // type
                            "",                      // container
                            "",                      // value
                            rgnDefinition,           // modifier/description
                            m_stmRoot.getName()      // scope
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            // 2. Handle regions within states (composite/parallel regions)
            if (vertex instanceof State) {
                State state = (State) vertex;
                List<Region> regions = state.getRegions();
                // For each subregion of the composite state
                for (int subRgnIdx = 0; subRgnIdx < regions.size(); subRgnIdx++) {
                    String baseName = (subRgnIdx == 0) 
                        ? state.getName() 
                        : state.getName() + "Rgn" + subRgnIdx;
                    System.out.println(makeIndent(indent) + 
                        "self." + baseName + "Hsm = self._" + baseName + "Hsm(self, self.parent)");
                    try {
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "region", "name"),
                            baseName + "Hsm",       // name
                            m_iClass.getName(),     // type
                            baseName + "Hsm",       // container
                            "",                     // value
                            rgnDefinition,          // modifier/description
                            m_stmRoot.getName()     // scope
                        ));
                        result.append(Utils.get(
                            m_stxCsv.get(indent, "region", "ext1st"),
                            baseName + "Hsm",       // name
                            m_iClass.getName(),     // type
                            baseName + "Hsm",       // container
                            "",                     // value
                            rgnDefinition,          // modifier/description
                            m_stmRoot.getName()     // scope
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // 3. Handle submachine states
        for (Vertex vertex : rgnVertices) {
            if (vertex instanceof State) {
                State state = (State) vertex;
                StateMachine subStm = state.getSubmachine();
                if (subStm != null) {
                    String subStmName = subStm.getName();
                    // Determine the parent region Hsm name for this submachine
                    String targetMachineName = findTargetMachineName(rgnName, m_stmRoot, state);
                    if (targetMachineName == null) {
                        targetMachineName = rgnName + "Hsm";
                    }
                    System.out.println(makeIndent(indent) + 
                        "self." + state.getName() + "Hsm = " 
                        + subStmName + "(self, self." + targetMachineName + ")");
                    try {
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "substm", "name"),
                            state.getName() + "Hsm",    // name
                            m_iClass.getName(),         // type
                            subStmName,                 // container (sub-machine class name)
                            "",                         // value
                            targetMachineName,          // modifier (parent region Hsm)
                            rgnDefinition,              // description
                            m_stmRoot.getName()         // scope
                        ));
                        result.append(Utils.get(
                            m_stxCsv.get(indent, "substm", "ext1st"),
                            state.getName() + "Hsm",    // name
                            m_iClass.getName(),         // type
                            subStmName,                 // container
                            "",                         // value
                            targetMachineName,          // modifier
                            rgnDefinition,              // description
                            m_stmRoot.getName()         // scope
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        indent--;
        return result.toString();
    }
    
    
    /**
     * TraverseTransition
     * @param stmRoot
     * @param rgnName
     * @param vertices
     * @param transition
     */
    private void TraverseTransition(StateMachine stmRoot, String rgnName, List<Vertex> vertices, Transition transition) {
        // Get the target vertex of the transition
        Vertex target = transition.getTarget();
        if (target == null) {
            return; // Nothing to process if there is no target
        }

        // Case 1: target is a State
        if (target instanceof State) {
            State state = (State) target;
            // If the state is composite (has subregions), recursively process its regions
            if (!state.getRegions().isEmpty()) {
                for (Region region : state.getRegions()) {
                    // Recursively traverse transitions within this subregion
                    for (Transition subTrans : region.getTransitions()) {
                        TraverseTransition(stmRoot, region.getName(), region.getSubvertices(), subTrans);
                    }
                }
            }
            // If the state is simple (no regions), no further recursion is needed
        }
        // Case 2: target is a Pseudostate
        else if (target instanceof Pseudostate) {
            Pseudostate ps = (Pseudostate) target;
            PseudostateKind kind = ps.getKind();
            // For choice or junction pseudostates (and others), follow all outgoing transitions
            for (Transition outgoing : ps.getOutgoings()) {
                TraverseTransition(stmRoot, rgnName, vertices, outgoing);
            }
        }
        // Other target types (e.g. final states) can be ignored or handled as needed
    }
    
 // Public-facing method — matches existing 3-argument call
    private String findTargetMachineName(String currentRegionName, StateMachine rootStm, State targetState) {
        if (rootStm.getRegions().isEmpty()) return null;
        List<Vertex> topLevelVertices = rootStm.getRegions().get(0).getSubvertices();
        return findTargetMachineNameRecursive(currentRegionName, topLevelVertices, targetState, null);
    }

    // Internal recursive helper
    private String findTargetMachineNameRecursive(String currentRegionName, List<Vertex> vertices, State targetState, State parentState) {
        for (Vertex v : vertices) {
            if (v instanceof State) {
                State state = (State) v;
                if (state.equals(targetState)) {
                    return currentRegionName + "Hsm";
                }
                if (state.getSubmachine() != null) {
                    for (Region subRegion : state.getSubmachine().getRegions()) {
                        String result = findTargetMachineNameRecursive(
                            state.getName() + "@" + state.getSubmachine().getName(),
                            subRegion.getSubvertices(),
                            targetState,
                            state
                        );
                        if (result != null) return result;
                    }
                }
                if (state.isComposite()) {
                    List<Region> regions = state.getRegions();
                    for (int i = 0; i < regions.size(); i++) {
                        Region region = regions.get(i);
                        String regionPrefix = (i == 0)
                            ? state.getName()
                            : state.getName() + "Rgn" + i;
                        for (Vertex subV : region.getSubvertices()) {
                            if (subV.equals(targetState)) {
                                return regionPrefix + "Hsm";
                            }
                        }
                        String result = findTargetMachineNameRecursive(regionPrefix, region.getSubvertices(), targetState, state);
                        if (result != null) return result;
                    }
                }
            }
        }
        return null;
    }
    
    private void printTransition(StateMachine stmRoot, String rgnName, List<Vertex> vertices, Transition transition) throws Exception {
        // Get source and target of the transition
        Vertex src = transition.getSource();
        Vertex tgt = transition.getTarget();

        // Handle triggers (use the Event name as condition)
        for (Trigger trigger : transition.getTriggers()) {
            Event event = trigger.getEvent();
            if (event != null) {
                String eventName = event.getName();
                if (eventName != null && !eventName.isEmpty()) {
                    try {
                        // Write a transition line for this event (first trigger)
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "transition", "ext1st"),
                            eventName,          // name (the event name)
                            "",                 // type (class name, if any; left blank here)
                            rgnName,            // container (region or state name)
                            "",                 // value
                            "",                 // modifier
                            "",                 // description
                            stmRoot.getName()   // scope (state machine name)
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // (If multiple triggers exist, you could loop and use "extnxt" for subsequent triggers)
                    break; // only use the first trigger as condition
                }
            }
        }

        // Handle guard condition (if present)
        Constraint guard = transition.getGuard();
        if (guard != null) {
            ValueSpecification spec = guard.getSpecification();
            if (spec instanceof OpaqueExpression) {
                OpaqueExpression expr = (OpaqueExpression) spec;
                if (!expr.getBodies().isEmpty()) {
                    String guardCondition = expr.getBodies().get(0);
                    if (guardCondition != null && !guardCondition.isEmpty()) {
                        try {
                            // Write a branch line for the guard condition
                            m_writer.write(Utils.get(
                                m_stxCsv.get(indent, "branch", "ext1st"),
                                guardCondition,  // name (guard text)
                                "",              // type
                                "",              // container
                                guardCondition,  // value (guard text again)
                                "",              // modifier
                                "",              // description
                                stmRoot.getName()// scope
                            ));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        // Handle effect actions (if present)
        Behavior effect = transition.getEffect();
        if (effect instanceof OpaqueBehavior) {
            OpaqueBehavior ob = (OpaqueBehavior) effect;
            List<String> bodies = ob.getBodies();
            if (!bodies.isEmpty()) {
                String action = bodies.get(0);
                if (action != null && !action.isEmpty()) {
                    // Determine target state name for the action (if target is a State or Pseudostate)
                    String targetName = (tgt instanceof NamedElement) ? ((NamedElement) tgt).getName() : "";
                    try {
                        // Write a transition action (begin) line
                        m_writer.write(Utils.get(
                            m_stxCsv.get(indent, "trans_action", "begin"),
                            targetName,      // name (target state name)
                            "",              // type
                            rgnName,         // container
                            "",              // value
                            action,          // modifier (use action text)
                            "",              // description
                            stmRoot.getName()// scope
                        ));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    private Region getStateMachineDiagram(StateMachine stm) {
        Iterator<EObject> contents = stm.eAllContents();
        while (contents.hasNext()) {
            EObject obj = contents.next();
            // Check if this EObject is a GMF Diagram and has type StateMachine
            if (obj instanceof Region) {
            	return (Region)obj;
            }
        }
        return null;
    }    
    // ... Additional code for printing transitions and states (printTransition, TraverseTransition, etc.), 
    // converting Astah API calls to UML2 as shown above.
}
