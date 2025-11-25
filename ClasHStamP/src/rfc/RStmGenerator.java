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
import org.eclipse.uml2.uml.TransitionKind;
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
import org.eclipse.gmf.runtime.notation.Connector;
import org.eclipse.gmf.runtime.notation.RelativeBendpoints;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.gmf.runtime.notation.datatype.RelativeBendpoint;
import org.eclipse.gmf.runtime.notation.NotationPackage;
import org.eclipse.gmf.runtime.notation.LayoutConstraint;

public class RStmGenerator extends TBaseGenerator {
    private List<StateMachine> m_sortedStmDgrs = new ArrayList<>();
    private StateMachine m_iMainStm = null;
    private StateMachine m_stmRoot = null;
    private Transition m_originTrans = null;

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
				if (isSubmachineOf(o1, getVertexes(o2))) 
					return -1;
				if (isSubmachineOf(o2, getVertexes(o1))) 
					return 1;
                return 0;
            }
        });
        if (!m_sortedStmDgrs.isEmpty()) {
            m_iMainStm = m_sortedStmDgrs.get(m_sortedStmDgrs.size() - 1);
        }
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
                checkStateBfr(state, container);
                try {
                    // traverse region 0 of this composite state
                    if (!state.getRegions().isEmpty() && !state.getRegions().get(0).getSubvertices().isEmpty()) {
                        for (Vertex subVtx : state.getRegions().get(0).getSubvertices()) {
                            traverse(subVtx, state);
                        }
                    }
                } catch (Exception e) {
                    // Handle exceptions if needed (none expected in UML2 traversal)
                }
                checkState(state, container);
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
                checkStateBfr(state, container, rgnIndex);
                for (int subRgnIdx = 0; subRgnIdx < state.getRegions().size(); subRgnIdx++) {
                    try {
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
                    } catch (Exception e) {
                        // Break out if region index out of bounds (not expected in for-loop)
                        break;
                    }
                }
                checkState(state, container, rgnIndex);
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
    
    /**
     * getContainer
     * @param iVtx
     * @return
     */
    private State getContainer(Vertex iVtx) {
    	if (iVtx.getContainer() != null) {
    		return iVtx.getContainer().getState();
    	}
    	if (iVtx instanceof Pseudostate) {
    		Pseudostate iPstate = (Pseudostate)iVtx;
    		return iPstate.getState();
    	}    	
    	return null;
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
	
	private Transition[] toArray(Collection<Transition> collection) {
		return collection.toArray(new Transition[0]);
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
     * Determine if a transition is external (exits the least common ancestor state).
     * Uses GMF Notation to check if transition points lie outside the LCA state's bounds.
     */
	private boolean checkIfExternalTrans(Transition lastTrans) {
	    try {
	        Vertex commonState = null;
	        Transition originTrans = m_originTrans;
	        Vertex targetState = lastTrans.getTarget();
	        StateMachine stmRoot = m_stmRoot;

			Vertex traversingVertex = targetState;
			// find least common ancestor
			while (traversingVertex != m_originTrans.getSource()) {
				if (getContainer(traversingVertex) != null) {
					traversingVertex = (State)getContainer(traversingVertex);
					if (traversingVertex == m_originTrans.getSource()) {
						commonState = traversingVertex;
						break;
					}
				} else {
					break;
				}
			}
			traversingVertex = m_originTrans.getSource();
			while (traversingVertex != targetState) {
				if (getContainer(traversingVertex) != null) {
					traversingVertex = (State)getContainer(traversingVertex);
					if (traversingVertex == targetState) {
						commonState = traversingVertex;
						break;
					}
				} else {
					break;
				}
			}

	        if (commonState != null) {
	            // find rectangle of the common state
	            Rectangle2D rect = findStateRectangle(stmRoot, commonState);
	            if (rect == null) return false;

	            boolean isExternal = false;
	            isExternal |= checkTransitionOutside(originTrans, stmRoot, rect);
	            isExternal |= checkTransitionOutside(lastTrans, stmRoot, rect);
	            return isExternal;
	        }
	    } catch (Exception ex) {
	        ex.printStackTrace();
	    }
	    return false;
	}

	/**
	 * Finds the rectangle (absolute Bounds) of a UML state in the notation model.
	 */
	private Rectangle2D findStateRectangle(StateMachine stmRoot, Vertex state) {
	    for (EObject eObj : stm.TMain.notationResource.getContents()) {
	        if (eObj instanceof Diagram) {
	            Diagram diagram = (Diagram) eObj;
	            if (diagram.getElement() == stmRoot) {
	                Node node = findNodeForElement(diagram, state);
	                if (node != null) {
	                    Rectangle2D r = getAbsoluteBounds(node);
	                    return r;
	                }
	            }
	        }
	    }
	    return null;
	}

	/**
	 * Check if any bendpoint of transition lies outside the given rectangle.
	 */
	private boolean checkTransitionOutside(Transition transition, StateMachine stmRoot, Rectangle2D rect) {
	    for (EObject eObj : stm.TMain.notationResource.getContents()) {
	        if (eObj instanceof Diagram) {
	            Diagram diagram = (Diagram) eObj;
	            if (diagram.getElement() == stmRoot) {
	                for (Object child : diagram.getEdges()) {
	                    if (child instanceof Connector) {
	                        Connector conn = (Connector) child;
	                        if (conn.getElement() == transition) {
	                            Object bp = conn.getBendpoints();
	                            if (bp instanceof RelativeBendpoints) {
	                                @SuppressWarnings("unchecked")
	                                List<RelativeBendpoint> points = ((RelativeBendpoints) bp).getPoints();
	                                for (RelativeBendpoint p : points) {
	                                    if (p.getSourceX() < rect.getMinX()-1 || p.getSourceX() > rect.getMaxX()+1
	                                            || p.getSourceY() < rect.getMinY()-1 || p.getSourceY() > rect.getMaxY()+1) {
	                                        return true; // external
	                                    }
	                                }
	                            }
	                        }
	                    }
	                }
	            }
	        }
	    }
	    return false;
	}

	/**
	 * getAbsoluteBounds
	 * @param view
	 * @return
	 */
	private Rectangle2D getAbsoluteBounds(Node view) {
	    double x = 0;
	    double y = 0;
	    double w = 0;
	    double h = 0;

	    //
	    // 1) Start with this view's own local bounds (if any)
	    //
	    if (view.getLayoutConstraint() instanceof Bounds) {
	        Bounds b = (Bounds) view.getLayoutConstraint();
	        x = b.getX();
	        y = b.getY();
	        w = b.getWidth();
	        h = b.getHeight();
	    }

	    //
	    // 2) Climb the GMF Notation containment hierarchy
	    //    adding all parent Bounds (local offsets)
	    //
	    EObject container = view.eContainer();
	    while (container instanceof Node) {
	        Node parentView = (Node) container;

	        if (parentView.getLayoutConstraint() instanceof Bounds) {
	            Bounds pb = (Bounds) parentView.getLayoutConstraint();
	            x += pb.getX();
	            y += pb.getY();
	        }

	        container = parentView.eContainer();
	    }

	    //
	    // The returned rectangle is now in absolute diagram coordinates
	    //
	    return new Rectangle2D.Double(x, y, w, h);
	}	
    
    /**
     * getSubvertexes
     * @param stmOrState
     * @return
     */
    private Collection<Vertex> getVertexes(NamedElement stmOrState) {
    	return getSubvertexes(stmOrState);
    }
    private Collection<Vertex> getSubvertexes(NamedElement stmOrState) {
    	Collection<Vertex> vtxCol = new ArrayList<Vertex>();
    	if (stmOrState instanceof StateMachine) {
    		vtxCol.addAll(((StateMachine)stmOrState).getRegions().get(0).getSubvertices());
    		vtxCol.addAll(((StateMachine)stmOrState).getConnectionPoints());
    	} else {
	    	if (stmOrState instanceof State) {
	    		for (Region rgn: ((State)stmOrState).getRegions()) {
	    			vtxCol.addAll(rgn.getSubvertices());
	    		}
	    		vtxCol.addAll(((State)stmOrState).getConnectionPoints());
	    	}
    	}
    	return vtxCol;
    }
    
    /**
     * getSubvertexes
     * @param stmOrState
     * @return
     */
    private Collection<Vertex> getSubvertexes(NamedElement stmOrState, int rgnIndex) throws IllegalArgumentException {
    	List<Vertex> vtxCol = new ArrayList<Vertex>();
    	if (stmOrState instanceof StateMachine) {
    		vtxCol.addAll(((StateMachine)stmOrState).getRegions().get(rgnIndex).getSubvertices());
    	} else {
	    	if (stmOrState instanceof State) {
	    		if (rgnIndex > 0 && rgnIndex >= ((State)stmOrState).getRegions().size()) {
	    			throw new IllegalArgumentException();
	    		} else if (!((State)stmOrState).getRegions().isEmpty()) {
		    		Region rgn = ((State)stmOrState).getRegions().get(rgnIndex);
	    			vtxCol = rgn.getSubvertices();
	    		}
	    	}
    	}
    	return vtxCol;
    }    
    
    /**
     * isChoicePseudostate
     * @param iPst
     * @return
     */
    private boolean isChoicePseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.CHOICE_LITERAL;
    }

    /**
     * isJunctionPseudostate
     * @param iPst
     * @return
     */
    private boolean isJunctionPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.JUNCTION_LITERAL;
    }

    /**
     * isDeepHistoryPseudostate
     * @param iPst
     * @return
     */
    private boolean isDeepHistoryPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.DEEP_HISTORY_LITERAL;
    }

    /**
     * isShallowHistoryPseudostate
     * @param iPst
     * @return
     */
    private boolean isShallowHistoryPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.SHALLOW_HISTORY_LITERAL;
    }

    /**
     * isEntryPointPseudostate
     * @param iPst
     * @return
     */
    private boolean isEntryPointPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.ENTRY_POINT_LITERAL;
    }

    /**
     * isExitPointPseudostate
     * @param iPst
     * @return
     */
    private boolean isExitPointPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.EXIT_POINT_LITERAL;
    }

    /**
     * isForkPseudostate
     * @param iPst
     * @return
     */
    private boolean isForkPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.FORK_LITERAL;
    }

    /**
     * isJoinPseudostate
     * @param iPst
     * @return
     */
    private boolean isJoinPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.JOIN_LITERAL;
    }
    
    /**
     * isInitialPseudostate
     * @param iPst
     * @return
     */
    private boolean isInitialPseudostate(Pseudostate iPst) {
    	return iPst.getKind() == PseudostateKind.INITIAL_LITERAL;
    }
    
    /**
     * Extract the guard condition text for a Transition.
     */
    private String getGuard(Transition trans) {
    	return getGuardText(trans);
    }
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
     * 
     * @param stm
     * @return
     */
    private Collection<Transition> getTransitions(StateMachine stm) {
    	Collection<Transition> result = new ArrayList<Transition>();
    	result.addAll(stm.getRegions().get(0).getTransitions());
    	new StateDeepTraverser() {
    		protected void checkState(State state, State container, int rgnIndex) {
    			if (state.getRegions().size() > 0) {
    				result.addAll(state.getRegions().get(0).getTransitions());
    			}
			}
    		protected void checkRegion(State state, int subRgnIndex, State container, int rgnIndex) {
				result.addAll(state.getRegions().get(subRgnIndex).getTransitions());
    		}
    	}.start(getVertexes(stm));
    	return result;
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
            if (t.getKind() == TransitionKind.INTERNAL_LITERAL) {
                internals.add(t);
            }
        }
        return internals;
    }
    
    
    /**
     * Extract the event trigger name of a Transition (first trigger's event or trigger name).
     */
    private String getEvent(Transition trans) {
    	return getEventName(trans);
    }
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
     * Extract the effect action text for a Transition.
     */
    private String getAction(Transition trans) {
    	return getActionText(trans);
    }
    private String getActionText(Transition trans) {
        String actionText = "";
        if (trans.getEffect() instanceof OpaqueBehavior) {
            OpaqueBehavior effect = (OpaqueBehavior) trans.getEffect();
            if (effect != null) {
	            int languageIndex = -1;
	            int i = 0;
	            for (String language: effect.getLanguages()) {
	            	if (language.equalsIgnoreCase(m_language)) {
	            		languageIndex = i;
	            		break;
	            	}
	            	i++;
	            }
	            if (languageIndex >= 0) {
	            	actionText = effect.getBodies().get(languageIndex);
	            }
            }
        }
        // If the effect is another kind of behavior, we could handle accordingly (not needed if not present).
        return actionText.trim();
    }

    /**
     * getEntry
     * @param iState
     * @return
     */
    private String getEntry(State iState) {
        String actionText = "";
        if (iState.getEntry() instanceof OpaqueBehavior) {
            OpaqueBehavior effect = (OpaqueBehavior) iState.getEntry();
            if (effect != null && !effect.getBodies().isEmpty()) {
                actionText = effect.getBodies().get(0);
            }
        }
        // If the effect is another kind of behavior, we could handle accordingly (not needed if not present).
        return actionText.trim();
    }

    /**
     * getExit
     * @param iState
     * @return
     */
    private String getExit(State iState) {
        String actionText = "";
        if (iState.getExit() instanceof OpaqueBehavior) {
            OpaqueBehavior effect = (OpaqueBehavior) iState.getExit();
            if (effect != null && !effect.getBodies().isEmpty()) {
                actionText = effect.getBodies().get(0);
            }
        }
        // If the effect is another kind of behavior, we could handle accordingly (not needed if not present).
        return actionText.trim();
    }
    
    
    /**
     * getStateMachineDiagram
     * @param stm
     * @return
     */
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
                        if (isSubmachineOf(subStm, getSubvertexes(nestedStm))) {
                            m_bResult = true;
                        }
                    }
                }
            }
        }.start(vertices);
        return m_bResult;
    }
    
    /**
     * findNodeForElement
     * @param parent
     * @param element
     * @return
     */
    private static Node findNodeForElement(View parent, EObject element) {
        // Check this node itself
        if (parent.getElement() == element && parent instanceof Node) {
            return (Node) parent;
        }

        // Recurse through children
        for (Object child : parent.getChildren()) {
            if (child instanceof View) {
                Node found = findNodeForElement((View) child, element);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    
	/**
	 * findTargetMachineName
	 * @param iStm
	 * @param targetState
	 * @return null if not found
	 *         state machine name if targetVertex is in top levels
	 *         containing region name in other cases
	 */
	private String findTargetMachineName(String rgnName, Collection<Vertex> iVertices, Vertex targetVertex, StringBuilder targetRgnName) {
		class ContainingRegionFinder extends StateDeepTraverser {
			public State m_containingState = null;
			public int	m_containingRgnIndex = 0;
			public boolean m_found = false;
			private Vertex m_targetVertex = targetVertex;
			private Stack<State> regionStateStack = new Stack<State>();
			private Stack<Integer> regionIndexStack = new Stack<Integer>();
			protected void checkPseudostate(Pseudostate iPseudostate, State container, int rgnIndex) {
				if (m_targetVertex == iPseudostate) {
					m_found = true;					
				}
			}
			protected void checkRegionBfr(State iState, int subRgnIndex, State container, int rgnIndex) {
				if (!m_found) {
					m_containingState = iState;
					m_containingRgnIndex = subRgnIndex;
					regionStateStack.push(iState);
					regionIndexStack.push(subRgnIndex);
				}
			}
			protected void checkRegion(State iState, int subRgnIndex, State container, int rgnIndex) {
				if (!m_found) {
					regionStateStack.pop();
					regionIndexStack.pop();
					if (regionStateStack.size() > 0) {
						m_containingState = regionStateStack.peek();
						m_containingRgnIndex = regionIndexStack.peek();
					} else {
						m_containingState = null;
						m_containingRgnIndex = 0;						
					}
				}
			}
			protected void checkState(State iState, State container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					for (Vertex subVertex: getSubvertexes(iState)) {
						if (m_targetVertex == subVertex) {
							m_found = true;
							return;
						}
					}
				}				
				if (m_targetVertex == iState) {
					m_found = true;
				}
			}
			public ContainingRegionFinder() {
				super();
			}
		}
		ContainingRegionFinder containingRegion = new ContainingRegionFinder();
		containingRegion.start(iVertices);
		String targetMachine;
		if (!containingRegion.m_found) {
			targetMachine = null;
		} else if (containingRegion.m_containingState == null) {	// containing region nor sub-machine not found
			targetMachine = rgnName + "Hsm";
			if (targetRgnName != null) {
				targetRgnName.append(rgnName);
			}
		} else {
			if (containingRegion.m_containingRgnIndex == 0) {	// sub-machine
				targetMachine = containingRegion.m_containingState.getName() + "@" + containingRegion.m_containingState.getSubmachine().getName();
				if (targetRgnName != null) {
					targetRgnName.append(targetMachine);
				}
			} else {
				targetMachine = makeRgnName(containingRegion.m_containingState, containingRegion.m_containingRgnIndex) + "Hsm";
				if (targetRgnName != null) {
					targetRgnName.append(makeRgnName(containingRegion.m_containingState, containingRegion.m_containingRgnIndex));
				}
			}
		}
		return targetMachine;
	}
	
	/**
	 * printTransition
	 * @param iTrans
	 */
	private void printTransition(StateMachine stmRoot, String rgnName, Collection<Vertex> iVertices, Transition iTrans) {
		// ■ branch.name
		// ■ branch.ext1st
		// ■ branch.extnxt
		// ■ branch.begin
		// ■ branch.end
		// if target is State
		if (iTrans.getTarget() instanceof State) {
			
			// if has Guard
			if (!getGuardText(iTrans).isEmpty()) {
				// print If Guard
				System.out.println(makeIndent(indent) + (getGuardText(iTrans).equalsIgnoreCase("else") ? "else:" : ("if " + getGuardText(iTrans) + ":")));
				try {
					if (getGuardText(iTrans).equalsIgnoreCase("else")) {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "begin"), 
							getGuardText(iTrans),				// name 
							m_iClass.getName(), 				// type
							"", 								// container
							getGuardText(iTrans),		 		// value
							collectActions(indent, getActionText(iTrans)),// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
							getGuardText(iTrans),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							getGuardText(iTrans),		 			// value
							collectActions(indent, getActionText(iTrans)),// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				indent++;
				TraverseTransition(stmRoot, rgnName, iVertices, iTrans);
				indent--;
			} else {// else (does have Guard)
				TraverseTransition(stmRoot, rgnName, iVertices, iTrans);
			}
		} else {// else (target is not State)
			// if has Guard
			if (!getGuardText(iTrans).isEmpty()) {
				// print If Guard
				System.out.println(makeIndent(indent) + (getGuardText(iTrans).equalsIgnoreCase("else") ? "else:" : ("if " + getGuardText(iTrans) + ":")));
				try {
					if (getGuardText(iTrans).equalsIgnoreCase("else")) {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "begin"), 
							getGuard(iTrans),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							getGuard(iTrans),		 			// value
							collectActions(indent, getAction(iTrans)),// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
							getGuard(iTrans),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							getGuard(iTrans),		 			// value
							collectActions(indent, getAction(iTrans)),// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				indent++;
				// print Action if have
				if (!getAction(iTrans).trim().isEmpty()) {
					System.out.println(makeIndent(indent) + getAction(iTrans).trim());
					try {
						String actions = collectActions(indent, getAction(iTrans).trim());
						System.out.println(actions);
						m_writer.write(actions);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}

				TraverseTransition(stmRoot, rgnName, iVertices, iTrans);
				indent--;
			} else {// else (does have Guard)
				// print Action if have
				if (!getAction(iTrans).trim().isEmpty()) {
					System.out.println(makeIndent(indent) + getAction(iTrans).trim());
					try {
						String actions = collectActions(indent, getAction(iTrans).trim());
						System.out.println(actions);
						m_writer.write(actions);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				TraverseTransition(stmRoot, rgnName, iVertices, iTrans);
			}
		}
	}
    
	/**
	 * TransitionTo
	 * @param iTrans
	 * @param targetStateName
	 * @param stmRoot
	 * @param rgnName
	 */
	private void TransitionTo(Transition iTrans, String targetStateName, StateMachine stmRoot, String rgnName) {
		Vertex iTgtVtx = iTrans.getTarget();
		String targetMachineName = findTargetMachineName(stmRoot.getName(), getSubvertexes(stmRoot), iTgtVtx, null);
		// if target belongs to this region: print curState -> targetState
		// if target belongs to other region: print targetMachine.curState -> targetState
		// print BgnTrans
		
		/**
		 * ▲ trans_action.name
		 * [->][nAME]Params* e = ( [nAME]Params* )pEventParams;
		 * ▲ trans_action.ext1st
		 * [->][nAME]
		 * ▲ trans_action.extnxt
		 * [->][nAME]
		 * ■ trans_action.begin
		 * [->][Scope]_BgnTrans( p[tYPE], pStm, [sCOPE]_[nAME] );
		 * [mODIFIER][->][Scope]_EndTrans( p[tYPE], pStm );
		 * [->]bResult = TRUE;
		 * ▲ trans_action.end
		 * [->]bResult = TRUE;
		 */		
		if (targetMachineName != null && targetMachineName.equals(rgnName + "Hsm")) {
			/**
			 * Sample:
			 * Stm_BgnTrans( pClass, pStm, STM_TARGET_STATE )
			 * Stm_EndTrans( pClass, pStm );
			 */			
			String targetMachineRef = "self";
			// Check if transition is local or external
			if (checkIfExternalTrans(iTrans)) {
				System.out.println(makeIndent(indent) + targetMachineRef + ".isExternalTrans = True");
				try {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_action", "ext1st"), ""));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// print BgnTrans
			System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + getStateMachineDiagram(stmRoot).getName() + "." + targetStateName + ")");
			// print Action if have
			if (!getAction(iTrans).trim().isEmpty()) {
				System.out.println(makeIndent(indent) + getAction(iTrans).trim());
			}
			// print EndTrans
			System.out.println(makeIndent(indent) + targetMachineRef + ".EndTrans()");			
			try {
				if (m_bIsInternalTrans == false) {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
						targetStateName,								// name
						m_iClass.getName(),								// type
						targetMachineName,								// container
						"",												// value
						collectActions(indent, getAction(iTrans)),		// modifier
						getDefinition(iTrans),							// description
						getStateMachineDiagram(stmRoot).getName()		// scope
					));
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "end"),
						targetStateName,								// name
						m_iClass.getName(),								// type
						targetMachineName,								// container
						"",												// value
						collectActions(indent, getAction(iTrans)),		// modifier
						getDefinition(iTrans),							// description
						getStateMachineDiagram(stmRoot).getName()		// scope
					));
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} else {
			/**
			 * ▲ substm.begin
			 */
			String targetMachineRef = "self.main." + targetMachineName;
			System.out.println(makeIndent(indent) + targetMachineRef + "Hsm.Initiate(" + getStateMachineDiagram(stmRoot).getName() + "." + targetStateName + ")");
			try {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "region", "begin"),
					targetStateName,								// name
					m_iClass.getName(),								// type
					targetMachineName,								// container
					"",												// value
					targetMachineName,								// modifier
					getDefinition(iTrans),							// description
					getStateMachineDiagram(stmRoot).getName()		// scope
				));
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
	 * TraverseTransition
	 * @param iTrans
	 */
	private void TraverseTransition(StateMachine stmRoot, String rgnName, Collection<Vertex> iVertices, Transition iTrans) {
		Vertex iTgtVtx = iTrans.getTarget();
		if (iTgtVtx != null) {
			if (iTgtVtx instanceof Pseudostate) {				
				Pseudostate iPstate = (Pseudostate)iTgtVtx;
				Collection<Transition> outgoings = iTgtVtx.getOutgoings();
				// check if external transition or local transition
				if (isChoicePseudostate(iPstate)) {
					if (outgoings.size() == 2) {
						Transition ifChoice, elseChoice;
						if (getGuard(toArray(outgoings)[0]).equalsIgnoreCase("else")) {
							ifChoice = toArray(outgoings)[1];
							elseChoice = toArray(outgoings)[0];
						} else if (getGuard(toArray(outgoings)[1]).equalsIgnoreCase("else")) {
							ifChoice = toArray(outgoings)[0];
							elseChoice = toArray(outgoings)[1];
						} else {
							ifChoice = null;
							elseChoice = null;
						}
						if (ifChoice != null && elseChoice != null) {
							// print if
							printTransition(stmRoot, rgnName, iVertices, ifChoice);
							// print else
							printTransition(stmRoot, rgnName, iVertices, elseChoice);
							// print end-if
							System.out.println(makeIndent(indent) + "# end if");
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									getGuard(iTrans),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									getGuard(iTrans),		 			// value
									collectActions(indent, getAction(iTrans)),// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else if (outgoings.size() == 1) {
						Transition ifChoice = toArray(outgoings)[0];
						if (ifChoice != null) {
							// print if
							printTransition(stmRoot, rgnName, iVertices, ifChoice);
							// print end-if
							System.out.println(makeIndent(indent) + "# end if");
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									getGuard(iTrans),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									getGuard(iTrans),		 			// value
									collectActions(indent, getAction(iTrans)),// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else {
						System.out.println("★★★ERROR★★★: Choice cannot have more than 2 outgoings");						
					}
				}else if (isJunctionPseudostate(iPstate)) {
					// traverse junction's outgoing
					if (outgoings.size() == 1) {
						printTransition(stmRoot, rgnName, iVertices, toArray(outgoings)[0]);
					} else {
						System.out.println("★★★ERROR★★★: Junction must have only one outgoing");
					}
				}else if (isDeepHistoryPseudostate(iPstate) || isShallowHistoryPseudostate(iPstate)) {
					// print curState -> shallowHistName & thisMachine's bit mask
					// if other regions existed: print targetRegion.pseudoState -> shallowHistName
					System.out.println(makeIndent(indent) + "if self.main." + iPstate.getName() + " != 0:");
					StringBuilder containingRgn = new StringBuilder();
					String targetHsm = findTargetMachineName(stmRoot.getName(), getSubvertexes(stmRoot), iPstate, containingRgn);
					indent++;
					System.out.println(makeIndent(indent) + "self.lastEnteredStateRecovering = True");
					String targetStateName = iTgtVtx.getName();
					// print BgnTrans
					System.out.println(makeIndent(indent)  + "self.BgnTrans(self.main." + targetStateName + ")");
					// print Action if have
					if (!getAction(iTrans).trim().isEmpty()) {
						System.out.println(makeIndent(indent) + getAction(iTrans).trim());
					}
					// print EndTrans
					System.out.println(makeIndent(indent) + "self.EndTrans()");									
					indent--;
					StringWriter tempWriter = new StringWriter();
					Writer originalWriter = m_writer;  // Save the original FileWriter
					m_writer = tempWriter;					
					if (iPstate.getOutgoings().size() > 0) {
						System.out.println(makeIndent(indent) + "else:");
						indent++;
						printTransition(stmRoot, rgnName, iVertices, toArray(iPstate.getOutgoings())[0]);
						indent--;
					} else {
						indent++;
						targetStateName = rgnName;
						State container = null;
						if (getContainer(iTgtVtx) != null) {
							container = getContainer(iTgtVtx);
							try {
								if (!Arrays.asList(getSubvertexes(container, 0)).contains(iTgtVtx)) {	// the pseudo-state belong to a region top
									container = null;
								}
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						if (container != null) {
							targetStateName = container.getName();
						}
						TransitionTo(iTrans, targetStateName, stmRoot, rgnName);
						indent--;
					}
					m_writer = originalWriter;
					
					// ■ history.begin
					// ■ deep_hist.begin
					// [->]if( pStm->[nAME] != STATE_UNDEF ){
					// [->]    [Scope]_BgnTrans( p[tYPE], pStm, pStm->[nAME] );
					// [mODIFIER][->]    [Scope]_EndTrans( p[tYPE], pStm );
					// [->]    bResult = TRUE;
					// [->]    break;
					// [->]}else{
					// [vALUE][->]}

					try {
						String syntax = isShallowHistoryPseudostate(iPstate) ? m_stxCsv.get(indent, "history", "begin"): m_stxCsv.get(indent, "deep_hist", "begin");
						m_writer.write(Utils.get(syntax, 
							iPstate.getName(),					// name 
							m_iClass.getName(), 				// type
							targetHsm,							// container
							tempWriter.toString(),	 			// value
							collectActions(indent, getAction(iTrans).trim()),// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
				}else if (isEntryPointPseudostate(iPstate)) {
					// if target's container does not have sub-machine: throws error
					// else: print subMachine.pseudoState -> entryPoint state
					// if target belongs to this region: print curState -> entryPt's container
					// if target belongs to other region: print targetMachine.curState -> entryPt's container
					State targetState = (State)getContainer(iPstate);
					String targetMachineName = findTargetMachineName(stmRoot.getName(), getSubvertexes(stmRoot), targetState, null);
					String targetMachineRef;
					if (targetMachineName.equals(rgnName + "Hsm")) {
						targetMachineRef = "self";
					} else {
						targetMachineRef = "self.main." + targetMachineName;
					}
					// print BgnTrans
					System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + getStateMachineDiagram(stmRoot).getName() + "." + targetState.getName() + ")");
					// print Action if have
					State container = (State)getContainer(iPstate);
					System.out.println(makeIndent(indent) + "self.main." + targetState + "Hsm.Initiate(self.lastEnteredStateRecovering, _" + container.getSubmachine().getName() + "Hsm." + iPstate.getName() + ")");
					if (!getAction(iTrans).trim().isEmpty()) {
						System.out.println(makeIndent(indent) + getAction(iTrans).trim());
					}
					System.out.println(makeIndent(indent) + targetMachineRef + ".EndTrans()");
					String actions = "";
					try {
						actions = collectActions(indent, getAction(iTrans));
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					try {						
						actions += Utils.get(m_stxCsv.get(indent, "substm", "begin"), 
							iPstate.getName(),							// name 
							m_iClass.getName(), 						// type
							getStateMachineDiagram(container.getSubmachine()).getName(),// container
							"",											// value
							targetState.getName() + "Hsm",						// modifier
							"",											// description 
							getStateMachineDiagram(stmRoot).getName()	// scope
						);
						//m_writer.write(actions);						
						//TransitionTo(iTrans, targetState.getName(), stmRoot, targetMachineName);
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
							targetState.getName(),							// name
							m_iClass.getName(),								// type
							targetMachineName,								// container
							"",												// value
							actions,								// modifier
							getDefinition(iTrans),							// description
							getStateMachineDiagram(stmRoot).getName()		// scope
						));
						
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (isExitPointPseudostate(iPstate)) {
					// if parentMachine is existed: print parentMachine.pseudoState -> exitPoint state
					String targetMachineName = stmRoot.getName() + "Hsm";
					String targetMachineRef;
					if (targetMachineName.equals(rgnName + "Hsm")) {
						targetMachineRef = "self";
					} else {
						targetMachineRef = "self.main." + targetMachineName;
					}
					// print BgnTrans
					System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + getStateMachineDiagram(stmRoot).getName() + "." + stmRoot.getName() + ")");
					// print Action if have
					System.out.println(makeIndent(indent) + "self.parent.pseudoState = " + getStateMachineDiagram(stmRoot).getName() + "." + iPstate.getName());
					if (!getAction(iTrans).trim().isEmpty()) {
						System.out.println(makeIndent(indent) + getAction(iTrans).trim());
					}
					System.out.println(makeIndent(indent) + targetMachineRef + ".EndTrans()");
					String actions = "";
					try {
						actions = collectActions(indent, getAction(iTrans));
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					try {						
						actions += Utils.get(m_stxCsv.get(indent, "substm", "end"), 
							iPstate.getName(),							// name 
							m_iClass.getName(), 						// type
							targetMachineName,							// container
							"",											// value
							"",											// modifier
							"",											// description 
							getStateMachineDiagram(stmRoot).getName()	// scope
						);
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
							stmRoot.getName(),							// name
							m_iClass.getName(),							// type
							targetMachineName,							// container
							"",											// value
							actions,									// modifier
							getDefinition(iTrans),						// description
							getStateMachineDiagram(stmRoot).getName()	// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (isForkPseudostate(iPstate)) {
					// traverse all outgoings
					System.out.println(makeIndent(indent) + "# begin forking");
					// find out-going direct to state belonging to the machine same as the origin
					Transition mainTrans = null;
					for (Transition outgoing: outgoings) {
						String sourceMachineName = findTargetMachineName(stmRoot.getName(), getSubvertexes(stmRoot), m_originTrans.getSource(), null);
						String targetMachineName = findTargetMachineName(stmRoot.getName(), getSubvertexes(stmRoot), outgoing.getTarget(), null);
						if (sourceMachineName.equals(targetMachineName)) {
							mainTrans = outgoing;
							break;
						}
					}
					if (checkIfExternalTrans(mainTrans)) {
						System.out.println(makeIndent(indent) + "self.isExternalTrans = True");
						try {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "state_action", "ext1st"), ""));
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					System.out.println(makeIndent(indent) + "self.BgnTrans(" + getStateMachineDiagram(stmRoot).getName() + "." + mainTrans.getTarget().getName() + ")");
					
					StringWriter tempWriter = new StringWriter();
					Writer originalWriter = m_writer;  // Save the original FileWriter
					m_writer = tempWriter;
					
					for (Transition outgoing: outgoings) {
						if (outgoing != mainTrans) {
							printTransition(stmRoot, rgnName, iVertices, outgoing);
						}
					}
					m_writer = originalWriter;					
					
					System.out.println(makeIndent(indent) + "self.EndTrans()");
					System.out.println(makeIndent(indent) + "# end forking");
					String actions = "";
					try {
						actions = collectActions(indent, getAction(iTrans));
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					try {
						actions += tempWriter;
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
							mainTrans.getTarget().getName(),				// name
							m_iClass.getName(),								// type
							rgnName + "Hsm",								// container
							"",												// value
							actions,										// modifier
							getDefinition(iTrans),							// description
							getStateMachineDiagram(stmRoot).getName()		// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (isJoinPseudostate(iPstate)) {
					// if joint bar belongs to this machine: print isIn(..) from other incoming transitions.
					System.out.println(makeIndent(indent) + "# begin joining");
					boolean firstRound = true;
					String isInConditions = "";
					for (Transition incoming: iPstate.getIncomings()) {
						if (incoming != iTrans) {
							if (incoming.getSource() instanceof State && getEvent(incoming).trim().isEmpty()) {
								State sourceState = (State)incoming.getSource();
								StringBuilder containingRgn = new StringBuilder();
								String targetMachineName = findTargetMachineName(stmRoot.getName(), getVertexes(stmRoot), sourceState, containingRgn);
								String targetMachineRef;
								if (targetMachineName.equals(rgnName + "Hsm")) {
									targetMachineRef = "self";
								} else {
									targetMachineRef = "self.main." + targetMachineName;
								}
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if IsIn(" + targetMachineRef + ".currentState," + getStateMachineDiagram(stmRoot).getName() + "." + sourceState + ")\\");
									try {
										// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
										isInConditions = Utils.get(m_stxCsv.get(indent, "trans_action", "ext1st"),
											sourceState.getName(),							// name
											m_iClass.getName(),								// type
											targetMachineName,								// container
											sourceState.getName(),							// value
											targetMachineName,								// modifier
											getDefinition(iTrans),							// description
											getStateMachineDiagram(stmRoot).getName()		// scope
										);
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + " and IsIn(" + targetMachineRef + ".currentState, " + getStateMachineDiagram(stmRoot).getName() + "." + sourceState + ")\\");
									try {
										// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
										isInConditions += Utils.get(m_stxCsv.get(indent, "trans_action", "extnxt"),
											sourceState.getName(),							// name
											m_iClass.getName(),								// type
											targetMachineName,								// container
											sourceState.getName(),							// value
											targetMachineName,								// modifier
											getDefinition(iTrans),							// description
											getStateMachineDiagram(stmRoot).getName()		// scope
										);
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							} else {
								System.out.println("★★★ERROR★★★: Joining from other regions cannot have event name");
							}
						}						
					}					
					if (outgoings.size() == 1) {
						System.out.println(makeIndent(indent) + ":");
						try {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
								isInConditions,						// name 
								m_iClass.getName(), 				// type
								"", 								// container
								isInConditions,			 			// value
								collectActions(indent, getAction(iTrans)),// modifier
								"",									// description 
								getStateMachineDiagram(stmRoot).getName()// scope
							));
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						indent++;
						printTransition(stmRoot, rgnName, iVertices, toArray(outgoings)[0]);
						indent--;
						if (iTrans.getSource() instanceof Pseudostate && isChoicePseudostate(((Pseudostate)iTrans.getSource())) && getGuard(iTrans).trim().isEmpty()) {
							//   ─◇ ─[empty]→┃▎
							//   └──[else]→
						} else {
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									isInConditions,						// name 
									m_iClass.getName(), 				// type
									"", 								// container
									isInConditions,			 			// value
									collectActions(indent, getAction(iTrans)),// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else {
						System.out.println("★★★ERROR★★★");
					}
					System.out.println(makeIndent(indent) + "# end joining");
				}else if (isInitialPseudostate(iPstate)) {
					// throws error
					System.out.println("★★★ERROR★★★");
				}else {
					// throws error
					System.out.println("★★★ERROR★★★");
				}
			}else if (iTgtVtx instanceof FinalState) {
				try {
					String targetStateName = rgnName;
					State container = null;
					if (getContainer(iTgtVtx) != null) {
						container = (State)getContainer(iTgtVtx);
						if (!Arrays.asList(getSubvertexes(container, 0)).contains(iTgtVtx)) {	// the pseudo-state belong to a region top
							container = null;
						}
					}
					if (container != null) {
						targetStateName = container.getName();
					}
					
					// if same level shallowHistory
					// set it to Zero
					String containingMachine = findTargetMachineName(rgnName, iVertices, iTgtVtx, null);
					container = (State)getContainer(iTgtVtx);
					Vertex shallowHistPt = findShallowHistoryPseudostate(containingMachine == null || container == null ? iVertices : getSubvertexes(container, 0));
					if (shallowHistPt != null) {
						System.out.println(makeIndent(indent) + "self.main." + shallowHistPt.getName() + " = 0");
						// ■ history.end
						m_writer.write(Utils.get(m_stxCsv.get(indent, "history", "end"), 
							shallowHistPt.getName(),			// name 
							m_iClass.getName(), 				// type
							"", 								// container
							"",						 			// value
							"",									// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					}						
					TransitionTo(iTrans, targetStateName, stmRoot, rgnName);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else if(iTgtVtx instanceof State){ 
				String targetStateName = iTgtVtx.getName();
				TransitionTo(iTrans, targetStateName, stmRoot, rgnName);
			} else {
				System.out.println("★★★ERROR★★★ Not supported target vertex");						
			}
		}
	}
    
	
	/**
	 * isLeafState
	 * @param iVertex
	 * @return
	 */
	private boolean isLeafState(Vertex iVertex) {
		if (iVertex instanceof State) {
			State iState = (State)iVertex;
			try {
				if (!(iState instanceof FinalState)) {
					if ((getSubvertexes(iState, 0).size() == 0 || iState.isSubmachineState())) {
						return true;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (iVertex instanceof Pseudostate) {
			Pseudostate iPseudostate = (Pseudostate)iVertex;
			State container = null;
			if (getContainer(iPseudostate) != null && getContainer(iPseudostate) instanceof State) {
				container = (State)getContainer(iPseudostate);
			}
			if (isInitialPseudostate(iPseudostate) 
			 || isEntryPointPseudostate(iPseudostate) && container == null
			 || isExitPointPseudostate(iPseudostate) && container == null
			 //|| isShallowHistoryPseudostate(iPseudostate) && iPseudostate.getOutgoings().length == 1
			 //|| isDeepHistoryPseudostate(iPseudostate) && iPseudostate.getOutgoings().length == 1
			) {
				return true;
			}
		}				
		return false;
	}

	/**
	 * isCompositeState
	 * @param iVertex
	 * @return
	 */
	protected boolean isCompositeState(Vertex iVertex) {
		if (iVertex instanceof State) {
			State iState = (State)iVertex;
			if (!(iState instanceof FinalState)) {
				if (!isLeafState(iVertex)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * getMainStm
	 * @return
	 */
    public StateMachine getMainStm() {
        return m_iMainStm;
    }


    
    
    
    
    
	/**
	 * printEventDecl
	 * @throws Exception 
	 * @throws IOException 
	 */
	List<String> uniqueSortedEvents = new ArrayList<>();
	public void printEventDecl() throws IOException, Exception  {
		// print event_decl enumeration
		m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "name"), 
			m_iClass.getName(), 
			m_iClass.getName(),
			getStateMachineDiagram(m_iMainStm).getName()
		));
		
		// print events of sub-machines
		class EventDeepTraverser extends StateDeepTraverser {
			protected void checkState(State iState, State container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					StateMachine iSubMachine = iState.getSubmachine();
					new EventDeepTraverser().start(getVertexes(iSubMachine));
					for (Transition iTrans: getTransitions(iSubMachine)) {
						if (!getEvent(iTrans).trim().isEmpty()) {
							if (!uniqueSortedEvents.contains(getEvent(iTrans).trim())) {
								uniqueSortedEvents.add(getEvent(iTrans).trim());
							}
						}
					}		
				}
				// collect internal transitions
				for (Transition iTrans: getInternalTransitions(iState)) {
					if (!getEvent(iTrans).trim().isEmpty()) {
						if (!uniqueSortedEvents.contains(getEvent(iTrans).trim())) {
							uniqueSortedEvents.add(getEvent(iTrans).trim());
						}
					}
				}
			}
		};
		new EventDeepTraverser().start(getVertexes(m_iMainStm));
		// print events of main-machine
		for (Transition iTrans: getTransitions(m_iMainStm)) {
			if (!getEvent(iTrans).trim().isEmpty()) {
				if (!uniqueSortedEvents.contains(getEvent(iTrans).trim())) {
					uniqueSortedEvents.add(getEvent(iTrans).trim());
				}
			}
		}		
		Collections.sort(uniqueSortedEvents);
		System.out.println(makeIndent(indent) + "class Events(Enum):");
		indent++;
		String path = m_stxCsv.get(indent, "event_decl", "ext1st");
		int nIndex = 0;
		for (String key: uniqueSortedEvents) {
			System.out.println(makeIndent(indent) + key + " = auto()");
			m_writer.write(Utils.get(path, 
				key, 
				m_iClass.getName(), 
				m_iClass.getName(), 
				String.valueOf(nIndex), 
				"", 
				""
			));
			nIndex++;
			path = m_stxCsv.get(indent, "event_decl", "extnxt");
		}
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "end"), 
			m_iClass.getName(), 
			m_iClass.getName(),
			getStateMachineDiagram(m_iMainStm).getName()
		));
	}
	
	/**
	 * printStmInitialization
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStmInitialization() throws IOException, Exception {
		m_writer.write(Utils.get(
			m_stxCsv.get(indent, "statemachine", "extnxt"), 
			m_iMainStm.getName(), 
			getStateMachineDiagram(m_iMainStm).getName(), 
			"",
			"", "", ""
		));
	}
	
	/**
	 * printMainStmDeclaration
	 * @throws IOException
	 * @throws Exception
	 */
	public void printMainStmDeclaration() throws IOException, Exception {
		m_writer.write(Utils.get(
			m_stxCsv.get(indent, "statemachine", "ext1st"), 
			m_iMainStm.getName(), 
			getStateMachineDiagram(m_iMainStm).getName(), 
			"",
			"", "", ""
		));
	}

	/**
	 * findInitialPseudostate
	 * @param iState
	 * @param iStm
	 * @return
	 */
	protected Pseudostate findInitialPseudostate(Collection<Vertex> iVertices) {
		for (Vertex iVtx: iVertices) {
			if (iVtx instanceof Pseudostate) {
				Pseudostate iPseudostate = (Pseudostate)iVtx;
				if (isInitialPseudostate(iPseudostate)) {
					return iPseudostate;
				}
			}
		}
		return null;
	}
	
	/**
	 * findShallowHistoryPseudostate
	 * @param iState
	 * @param iStm
	 * @return
	 */
	protected Pseudostate findShallowHistoryPseudostate(Collection<Vertex> iVertices) {
		for (Vertex iVtx: iVertices) {
			if (iVtx instanceof Pseudostate) {
				Pseudostate iPseudostate = (Pseudostate)iVtx;
				if (isShallowHistoryPseudostate(iPseudostate)) {
					return iPseudostate;
				}
			}
		}
		return null;
	}

	/**
	 * findDeepHistoryPseudostate
	 * @param iState
	 * @param iStm
	 * @return
	 */
	protected Pseudostate findDeepHistoryPseudostate(Collection<Vertex> iVertices) {
		for (Vertex iVtx: iVertices) {
			if (iVtx instanceof Pseudostate) {
				Pseudostate iPseudostate = (Pseudostate)iVtx;
				if (isDeepHistoryPseudostate(iPseudostate)) {
					return iPseudostate;
				}
			}
		}
		return null;
	}

	/**
	 * printStmTypes
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStmImpls() throws IOException, Exception {		
		for (StateMachine iStm: m_sortedStmDgrs) {
			m_stmRoot = iStm;
			String rgnName = iStm.getName();
			String rgnDgrName = getStateMachineDiagram(iStm).getName();
			String rgnDefinition = getDefinition(iStm);
			Collection<Vertex> rgnVertices = getVertexes(iStm);
			// list up sub-regions
			new StateDeepTraverser() {
				protected void checkRegion(State iState, int subRgnIndex, State container, int rgnIndex) {
					try {
						String rgnName = makeRgnName(iState, subRgnIndex);
						String rgnDgrName = makeRgnName(iState, subRgnIndex) + "Hsm";
						String rgnDefinition = getDefinition(iState);
						Collection<Vertex> rgnVertices = getSubvertexes(iState, subRgnIndex);
						System.out.println(makeIndent(indent) + "# Region sub-class");						
						// print state-machine sub-class
						System.out.println(makeIndent(indent) + "class _" + rgnName + "Hsm(Statemachine):");
						StringWriter tempWriter = new StringWriter();
						Writer originalWriter = m_writer;  // Save the original FileWriter
						m_writer = tempWriter;		
						indent++;
						printStmImpl(
							iStm,
							rgnName,
							rgnDgrName,
							rgnDefinition,
							rgnVertices
						);
						indent--;
						m_writer = originalWriter;
						m_writer.write(Utils.get(
							m_stxCsv.get(indent, "region", "end"), 
							rgnDgrName, 					// name
							m_iClass.getName(),				// type
							rgnName, 						// container
							tempWriter.toString(),			// value 
							"", 							// modifier
							"",								// description
							getStateMachineDiagram(iStm).getName()// scope
						));			
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start(getVertexes(iStm));
			
			System.out.println(makeIndent(indent) + "# Region sub-class");						
			// print state-machine sub-class
			System.out.println(makeIndent(indent) + "class _" + rgnName + "Hsm(Statemachine):");

			StringWriter tempWriter = new StringWriter();
			Writer originalWriter = m_writer;  // Save the original FileWriter
			m_writer = tempWriter;		
			indent++;
			printStmImpl(
				iStm,
				rgnName,
				rgnDgrName,
				rgnDefinition,
				rgnVertices
			);
			indent--;
			m_writer = originalWriter;
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "region", "end"), 
				rgnDgrName, 					// name
				m_iClass.getName(),				// type
				rgnName, 						// container
				tempWriter.toString(),			// value 
				"", 							// modifier
				"",								// description
				getStateMachineDiagram(iStm).getName()// scope
			));			
			
		}
		// list up state-machines
		for (StateMachine iStm: m_sortedStmDgrs) {
			m_stmRoot = iStm;
			String rgnName = iStm.getName();
			String rgnDgrName = getStateMachineDiagram(iStm).getName();
			String rgnDefinition = getDefinition(iStm);
			Collection<Vertex> rgnVertices = getVertexes(iStm);
			
			System.out.println(makeIndent(indent) + "# Statemachine sub-class");						
			// print state-machine sub-class
			System.out.println(makeIndent(indent) + "class " + rgnDgrName + "(ParallelStatemachine):");
			
			StringWriter tempWriter = new StringWriter();
			Writer originalWriter = m_writer;  // Save the original FileWriter
			m_writer = tempWriter;					
			
			// list up region state declaration
			new StateDeepTraverser() {
				protected void checkRegion(State iState, int subRgnIndex, State container, int rgnIndex) {
					try {
						String rgnName = makeRgnName(iState, subRgnIndex);
						String rgnDgrName = makeRgnName(iState, subRgnIndex) + "Hsm";
						String rgnDefinition = getDefinition(iState);
						Collection<Vertex> rgnVertices = getSubvertexes(iState, subRgnIndex);
						// print state-machine sub-class
						printStatesDefinition(		
							rgnName,
							rgnDgrName,
							rgnDefinition,
							rgnVertices
						);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start(getVertexes(iStm));			
			// list up main state declaration
			printStatesDefinition(		
				rgnName,
				rgnDgrName,
				rgnDefinition,
				rgnVertices
			);
			
			String subStmAndRgnInitStr = printSubStmAndRgnDecls(		
				rgnName,
				rgnDgrName,
				rgnDefinition,
				rgnVertices
			);
			
			m_writer = originalWriter;

			// ▲ state.name
			// ▲ state.ext1st
			// ▲ state.extnxt
			// ■ state.begin
			// ■ state.end
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "statemachine", "begin"), 
				rgnDgrName, 
				m_iClass.getName(),
				rgnName + "Hsm", 
				tempWriter.toString(), 
				"", 
				"",
				getStateMachineDiagram(iStm).getName()// scope				
			));					
			indent++;
			
			printStmAPIs(iStm);
			
			indent--;
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "statemachine", "end"), 
				rgnDgrName, 					// name
				m_iClass.getName(),				// type
				rgnName, 						// container
				subStmAndRgnInitStr,			// value 
				"", 							// modifier
				"",								// description
				getStateMachineDiagram(iStm).getName()// scope
			));			
		}
	}
	
	/**
	 * printStatesDefinition
	 * @param rgnName
	 * @param rgnDgrName
	 * @param rgnDefinition
	 * @param rgnVertices
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStatesDefinition(		
		String rgnName,
		String rgnDgrName,
		String rgnDefinition,
		Collection<Vertex> rgnVertices
	) throws IOException, Exception {		
		// print states definitions
		System.out.println(makeIndent(indent) + "# States definitions");
		
		StringWriter tempWriter = new StringWriter();
		Writer originalWriter = m_writer;  // Save the original FileWriter
		m_writer = tempWriter;		
		
		new StateDeepTraverserRgn0() {
			private int m_autoId = 0;
			public void printCompositeState(Collection<Vertex> iVertices, String stateName, String containerName, String definition) {
				String subStateNames = null;
				try {
					for (Vertex iVertex: iVertices) {
						if (isLeafState(iVertex) || isCompositeState(iVertex)) {
							if (subStateNames == null) {
								subStateNames = iVertex.getName();
								m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "ext1st"), 
									stateName, 
									m_iClass.getName(), 
									containerName, 
									iVertex.getName(), 
									String.format("%2d", 0), 
									definition,
									getStateMachineDiagram(m_stmRoot).getName()
								));
							} else {
								subStateNames += (" | " + iVertex.getName());
								m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "extnxt"), 
									stateName, 
									m_iClass.getName(), 
									containerName, 
									iVertex.getName(), 
									String.format("%2d", 0), 
									definition,
									getStateMachineDiagram(m_stmRoot).getName()
								));
							}
						}
					}
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "end"), 
						stateName, 
						m_iClass.getName(), 
						containerName, 
						"", 
						stateName, 
						definition,
						getStateMachineDiagram(m_stmRoot).getName()
					));												
					// print regions
					System.out.println(makeIndent(indent) + stateName + " = " + subStateNames);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			protected void checkPseudostate(Pseudostate iPseudostate, State container) {
				// print initial point, main-machine entry point, sub-machine exit point as simple states
				if (isLeafState(iPseudostate)) {
					System.out.println(makeIndent(indent) + iPseudostate.getName() + " = MakeState(" + m_autoId + ")");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "name"), 
							iPseudostate.getName(), 
							m_iClass.getName(),
							container != null ? container.getName() : rgnName,
							String.format("%2d", m_autoId),
							"",
							getDefinition(iPseudostate),
							getStateMachineDiagram(m_stmRoot).getName()
						));
					} catch (Exception e) {
						e.printStackTrace();
					}
					m_autoId++;;
				}
			}
			protected void checkState(State iState, State container) {
				String containerName = rgnName;
				if (getContainer(iState) != null) {
					containerName = ((NamedElement)getContainer(iState)).getName();
				}
				if (isLeafState(iState)) {
					// print leaf states
					System.out.println(makeIndent(indent) + iState.getName() + " = MakeState(" + m_autoId + ")");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "name"), 
							iState.getName(), 
							m_iClass.getName(),
							containerName,
							String.format("%2d", m_autoId),
							"",
							getDefinition(iState),
							getStateMachineDiagram(m_stmRoot).getName()
						));
					} catch (Exception e) {
						e.printStackTrace();
					}
					m_autoId++;
				} else if (isCompositeState(iState)) {
					try {
						printCompositeState(getSubvertexes(iState, 0), iState.getName(), containerName, getDefinition(iState));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			public void start(Collection<Vertex> iVertices) {
				super.start(iVertices);
				printCompositeState(rgnVertices, rgnName, "", rgnDefinition);
			}
		}.start(rgnVertices);
		
		m_writer = originalWriter;
		
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "begin"), 
			rgnName, 
			m_iClass.getName(),
			rgnName + "Hsm",
			tempWriter.toString(),
			"",
			"",
			getStateMachineDiagram(m_stmRoot).getName()
		));
	}
	
	/**
	 * printSubStmAndRgnDecls
	 * @throws IOException
	 * @throws Exception
	 */
	private String m_sResult;
	public String printSubStmAndRgnDecls(		
		String rgnName,
		String rgnDgrName,
		String rgnDefinition,
		Collection<Vertex> rgnVertices
	) throws IOException, Exception {
		m_sResult = "";
				
		System.out.println(makeIndent(indent) + "def __init__(self, _main, _parent):");
		indent++;
		System.out.println(makeIndent(indent) + "super().__init__(_main, _parent)");
		
		System.out.println(makeIndent(indent) + "# sub-machine and regions declaration");
		System.out.println(makeIndent(indent) + "self." + rgnName + "Hsm = self._" + rgnName + "Hsm(self, self.parent)");
		try {
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "region", "name"), 
				rgnName + "Hsm",					// name
				m_iClass.getName(),							// type
				rgnDgrName,						// container 
				"", 										// value
				"",											// modifier
				rgnDefinition,								// definition
				getStateMachineDiagram(m_stmRoot).getName()	// scope
			));
			m_sResult += Utils.get(
				m_stxCsv.get(indent, "region", "ext1st"), 
				rgnName + "Hsm",					// name
				m_iClass.getName(),							// type
				rgnDgrName,						// container 
				"", 										// value
				"",											// modifier
				rgnDefinition,								// definition
				getStateMachineDiagram(m_stmRoot).getName()	// scope
			);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		new StateDeepTraverser() {
			protected void checkPseudostate(Pseudostate iPseudostate, State container, int rgnIdx) {
				if (isShallowHistoryPseudostate(iPseudostate) || isDeepHistoryPseudostate(iPseudostate)) {
					System.out.println(makeIndent(indent) + "self." + iPseudostate.getName() + " = 0");
					try {
						m_writer.write(Utils.get(
							m_stxCsv.get(indent, "history", "name"), 
							iPseudostate.getName(),						// name
							m_iClass.getName(),							// type
							"",											// container 
							"", 										// value
							"",											// modifier
							rgnDefinition,								// definition
							getStateMachineDiagram(m_stmRoot).getName()	// scope
						));
						m_sResult += Utils.get(
							m_stxCsv.get(indent, "history", "ext1st"), 
							iPseudostate.getName(),						// name
							m_iClass.getName(),							// type
							"",											// container 
							"", 										// value
							"",											// modifier
							rgnDefinition,								// definition
							getStateMachineDiagram(m_stmRoot).getName()	// scope
						);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}
			protected void checkRegionBfr(State iState, int subRgnIdx, State container, int rgnIndex) {
				System.out.println(makeIndent(indent) + "self." + makeRgnName(iState, subRgnIdx) + "Hsm = self._" + makeRgnName(iState, subRgnIdx) + "Hsm(self, self.parent)");					
				try {
					m_writer.write(Utils.get(
						m_stxCsv.get(indent, "region", "name"), 
						makeRgnName(iState, subRgnIdx) + "Hsm",		// name
						m_iClass.getName(),							// type
						makeRgnName(iState, subRgnIdx) + "Hsm",		// container 
						"", 										// value
						"",											// modifier
						rgnDefinition,								// definition
						getStateMachineDiagram(m_stmRoot).getName()	// scope
					));
					m_sResult += Utils.get(
						m_stxCsv.get(indent, "region", "ext1st"), 
						makeRgnName(iState, subRgnIdx) + "Hsm",		// name
						m_iClass.getName(),							// type
						makeRgnName(iState, subRgnIdx) + "Hsm",		// container 
						"", 										// value
						"",											// modifier
						rgnDefinition,								// definition
						getStateMachineDiagram(m_stmRoot).getName()	// scope
					);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
			}
			protected void checkState(State iState, State container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					Region iSubStmDgr = getStateMachineDiagram(iState.getSubmachine());
					String targetMachineName = findTargetMachineName(rgnName, rgnVertices, iState, null);
					System.out.println(makeIndent(indent) + "self." + iState.getName() + "Hsm = " + iSubStmDgr.getName() + "(self, self." + targetMachineName + ")");
					try {
						m_writer.write(Utils.get(
							m_stxCsv.get(indent, "substm", "name"), 
							iState.getName() + "Hsm",					// name
							m_iClass.getName(),							// type
							iSubStmDgr.getName(),						// container 
							"", 										// value
							targetMachineName,							// modifier
							rgnDefinition,								// definition
							getStateMachineDiagram(m_stmRoot).getName()	// scope
						));
						m_sResult += Utils.get(
							m_stxCsv.get(indent, "substm", "ext1st"), 
							iState.getName() + "Hsm",					// name
							m_iClass.getName(),							// type
							iSubStmDgr.getName(),						// container 
							"", 										// value
							targetMachineName,							// modifier
							rgnDefinition,								// definition
							getStateMachineDiagram(m_stmRoot).getName()	// scope
						);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}
		}.start(rgnVertices);
		
		indent--;
		
		return m_sResult;
	}
	
	/**
	 * printStmAPIs
	 */
	private void printStmAPIs(StateMachine iStm) {
		/**
		 * print state-machine API: event-handle
		 */
		System.out.println(makeIndent(indent) + "def EventHandle(self, e, params):");
		indent++;
		// print regions and sub-machines' event-handles
		System.out.println(makeIndent(indent) + "result = False");
		StringWriter tempWriter = new StringWriter();
		Writer originalWriter = m_writer;  // Save the original FileWriter
		m_writer = tempWriter;					
				
		/**
		 * print state-machine API: run-to-completion
		 */		
		System.out.println(makeIndent(indent) + "def DefaultTrans(self):");
		indent++;
		System.out.println(makeIndent(indent) + "while True:");
		indent++;
		System.out.println(makeIndent(indent) + "result = False");
		
		// print regions and sub-machines' default-transitions		
		tempWriter = new StringWriter();
		m_writer = tempWriter;					
		new StateDeepTraverser() {
			protected void checkRegion(State iState, int subRgnIndex, State container, int rgnIndex) {
				String regionName = makeRgnName(iState, subRgnIndex); 
				System.out.print(makeIndent(indent) + "result = ");
				m_sResult = "";
				try {
					new StateDeepTraverserRgn0() {
						protected void checkState(State iState, State container) {
							if (iState.isSubmachineState()) {
								String subStmDgrName = getStateMachineDiagram(iState.getSubmachine()).getName();
								System.out.print("self." + iState.getName() + "Hsm.DefaultTrans() or ");
								try {
									m_sResult += Utils.get(
										m_stxCsv.get(indent, "stm_api", "end"), 
										iState.getName(),							// name
										m_iClass.getName(),							// type
										subStmDgrName,								// container 
										"", 										// value
										iState.getName() + "Hsm",					// modifier
										"",											// definition
										getStateMachineDiagram(m_stmRoot).getName()	// scope
									);
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
					}.start(getSubvertexes(iState, subRgnIndex));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("self." + regionName + "Hsm.DefaultTrans() or result");
				try {
					m_writer.write(Utils.get(
						m_stxCsv.get(indent, "stm_api", "begin"), 
						regionName,									// name
						m_iClass.getName(),							// type
						regionName + "Hsm",							// container 
						"", 										// value
						m_sResult,									// modifier
						"",											// definition
						getStateMachineDiagram(m_stmRoot).getName()	// scope
					));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start(getVertexes(iStm));
		
		// print this machine default-transition
		System.out.print(makeIndent(indent) + "result = ");
		m_sResult = "";
		new StateDeepTraverserRgn0() {
			protected void checkState(State iState, State container) {
				if (iState.isSubmachineState()) {
					String subStmDgrName = getStateMachineDiagram(iState.getSubmachine()).getName();
					System.out.print("self." + iState.getName() + "Hsm.DefaultTrans() or ");
					try {
						m_sResult += Utils.get(
							m_stxCsv.get(indent, "stm_api", "end"), 
							iState.getName(),							// name
							m_iClass.getName(),							// type
							subStmDgrName,								// container 
							"", 										// value
							iState.getName() + "Hsm",					// modifier
							"",											// definition
							getStateMachineDiagram(m_stmRoot).getName()	// scope
						);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start(getVertexes(iStm));
		System.out.println("self." + iStm.getName() + "Hsm.DefaultTrans() or result");
		System.out.println(makeIndent(indent) + "if result == False:");
		System.out.println(makeIndent(indent) + "    break");
		try {
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "stm_api", "begin"), 
				iStm.getName(),								// name
				m_iClass.getName(),							// type
				iStm.getName() + "Hsm",						// container 
				"", 										// value
				m_sResult,									// modifier
				"",											// definition
				getStateMachineDiagram(m_stmRoot).getName()	// scope
			));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String defaultTrans = tempWriter.toString();
		m_writer = originalWriter;
		indent--;
		System.out.println(makeIndent(indent) + "# end loop");
		indent--;
		System.out.println(makeIndent(indent) + "# end def");

		// print Initiate function
		System.out.println(makeIndent(indent) + "def Initiate(self, lastEnteredStateRecovering, entryPoint = 0):");
		System.out.println(makeIndent(indent) + "    self.lastEnteredStateRecovering = lastEnteredStateRecovering");
		System.out.println(makeIndent(indent) + "    self." + iStm.getName() + "Hsm.Initiate(entryPoint)");
		System.out.println(makeIndent(indent) + "# end def");
		// print Terminate function
		System.out.println(makeIndent(indent) + "def Terminate(self):");
		System.out.println(makeIndent(indent) + "    self." + iStm.getName() + "Hsm.Terminate()");
		System.out.println(makeIndent(indent) + "# end def");		

		try {
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "stm_api", "name"), 
				iStm.getName(),								// name
				m_iClass.getName(),							// type
				iStm.getName() + "Hsm",						// container 
				"",			 								// value
				defaultTrans,								// modifier
				"",											// definition
				getStateMachineDiagram(m_stmRoot).getName()	// scope
			));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * printStmType
	 * @throws IOException
	 * @throws Exception
	 */
	boolean m_bIsInternalTrans = false;
	public void printStmImpl(
		StateMachine stmRoot,
		String rgnName,
		String rgnDgrName,
		String rgnDefinition,
		Collection<Vertex> rgnVertices
	) throws IOException, Exception {
		System.out.println(makeIndent(indent) + "# Region implementation: " + rgnName);

		// print states' entryAction, eventHandle, exitAction
		new StateDeepTraverserRgn0() {
			protected void checkStateBfr(State _iState, State container) {				
				if (_iState instanceof FinalState) {
					return;
				}
				String stateName = rgnName;
				String rectRatio = "";
				if (_iState != null) {
					stateName = _iState.getName();
				}
				String containerName = rgnName;
				if (container != null) {
					containerName = container.getName();
				}
				
				// ■ transition.name
				// ■ transition.ext1st
				// ▲ transition.extnxt
				// ▲ transition.begin
				// ▲ transition.end
				// print state entry actions
				System.out.println(makeIndent(indent) + "def " + stateName + "_Enter(self):");
				indent++;
				System.out.println(makeIndent(indent) + "if self.Enterable(" + getStateMachineDiagram(stmRoot).getName() + "." + stateName + "):");
				if (_iState != null) {
					System.out.println(makeIndent(indent) + "    self." + containerName + "_Enter()");
				}
				try {
					String actions = "";
					Collection<Vertex> vertices = rgnVertices;
					if (_iState != null) {
						stateName = _iState.getName();
						vertices = getSubvertexes(_iState, 0);
					}
					// if initialPoint exists AND targetState == thisState: curState <- initialPoint
					Vertex initPt = findInitialPseudostate(vertices);
					if (initPt != null) {
						System.out.println(makeIndent(indent) + "    if self.targetState == " + getStateMachineDiagram(stmRoot).getName() + "." + stateName + ":");
						System.out.println(makeIndent(indent) + "        self.pseudoState = " + getStateMachineDiagram(stmRoot).getName() + "." + initPt.getName());
						// ■ action.begin
						actions += Utils.get(m_stxCsv.get(indent, "state_action", "name"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							"",									// value
							initPt.getName(), 					// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}

					// if lastEnteteredStateRecovering, recover last state
					if (_iState == null) {
						// ▲ deep_hist.end
						System.out.println(makeIndent(indent) + "    if self.lastEnteredStateRecovering:");
						System.out.println(makeIndent(indent) + "        self.pseudoState = self.lastEnteredState");
						actions += Utils.get(m_stxCsv.get(indent, "deep_hist", "end"), 
							"",			// name 
							m_iClass.getName(), 				// type
							"", 								// container
							"",		 			// value
							"",									// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}
					
					// if container has shallowHistoryPt and : shallowHistory <- thisState
					if (_iState != null) {
						Vertex shallowHistPt = findShallowHistoryPseudostate(container == null ? rgnVertices : getSubvertexes(container, 0));
						// ■ history.extnxt
						if (shallowHistPt != null) {
							//String containingMachine = _iState != null ? findTargetMachineName(rgnName, rgnVertices, _iState) : stmRoot.getName();
							System.out.println(makeIndent(indent) + "    self.main." + shallowHistPt.getName() + " = " + getStateMachineDiagram(stmRoot).getName() + "." + _iState.getName());
							actions += Utils.get(m_stxCsv.get(indent, "history", "extnxt"), 
								shallowHistPt.getName(),			// name 
								m_iClass.getName(), 				// type
								"", 								// container
								_iState.getName(),		 			// value
								"",									// modifier
								"",									// description 
								getStateMachineDiagram(stmRoot).getName()// scope
							);
						}
					}
					// initiate regions if available
					int subRgnIndex = 1;
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (getSubvertexes(_iState, subRgnIndex).size() > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Initiate()");
								// ■ substm.begin
								actions += Utils.get(m_stxCsv.get(indent, "state_action", "begin"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								);
							}
							subRgnIndex++;
						} catch (Exception e) {
							break;
						}
					}
					
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmName = getStateMachineDiagram(_iState.getSubmachine()).getName();
						// initiate sub-machine if available
						System.out.println(makeIndent(indent) + "    self.main." + stateName + "Hsm.Initiate(self.lastEnteredStateRecovering)");
						// ■ substm.begin
						// [cONTAINER]_Reset( p[tYPE], ( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[nAME] );
						actions += Utils.get(m_stxCsv.get(indent, "state_action", "begin"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							subStmName, 						// container
							"",									// value
							stateName + "Hsm",					// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}
					System.out.println(makeIndent(indent) + "    self.DefaultEntryAction('" + stateName + "')");

					// event-processing for regions if available
					subRgnIndex = 1;
					String modifier = "";
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (getSubvertexes(_iState, subRgnIndex).size() > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Initiate()");
								// ■ substm.begin
								modifier += Utils.get(m_stxCsv.get(indent, "stm_api", "ext1st"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								);
							}
							subRgnIndex++;
						} catch (Exception e) {
							break;
						}
					}
					
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmName = getStateMachineDiagram(_iState.getSubmachine()).getName();
						// event-processing for sub-machine if available
						System.out.println(makeIndent(indent) + "    self.main." + stateName + "Hsm.Initiate(self.lastEnteredStateRecovering)");
						// ■ substm.begin
						// [cONTAINER]_Reset( p[tYPE], ( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[nAME] );
						modifier += Utils.get(m_stxCsv.get(indent, "stm_api", "extnxt"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							subStmName, 						// container
							"",									// value
							stateName + "Hsm",					// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}
					
					if (_iState != null) {
						// Iterate over the notation model to find the View corresponding to this UML State
						Rectangle2D rect = null;
						Rectangle2D localStmRect = null;

						for (EObject eObj : stm.TMain.notationResource.getContents()) {
						    if (eObj instanceof Diagram) {
						        Diagram diagram = (Diagram) eObj;

						        if (diagram.getElement() == stmRoot) {
						            // Find the node for the state
						            Node stateNode = findNodeForElement(diagram, _iState);
						            if (stateNode != null && stateNode.getLayoutConstraint() instanceof Bounds) {
						                Bounds bounds = (Bounds) stateNode.getLayoutConstraint();
						                rect = new Rectangle2D.Double(
						                        bounds.getX(),
						                        bounds.getY(),
						                        bounds.getWidth(),
						                        bounds.getHeight()
						                );
						            }

						            // Find the node for the state machine itself
						            Node rootNode = findNodeForElement(diagram, stmRoot);
						            if (rootNode != null && rootNode.getLayoutConstraint() instanceof Bounds) {
						                Bounds bounds = (Bounds) rootNode.getLayoutConstraint();
						                localStmRect = new Rectangle2D.Double(
						                        bounds.getX(),
						                        bounds.getY(),
						                        bounds.getWidth(),
						                        bounds.getHeight()
						                );
						            }

						            break; // Done with this diagram
						        }
						    }
						}

						// Output ratio
						if (rect != null && localStmRect != null) {
						    rectRatio = String.format(
						        "%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%s",
						        Math.round(rect.getX()),
						        Math.round(rect.getY()),
						        Math.round(rect.getWidth()),
						        Math.round(rect.getHeight()),
						        Math.round(localStmRect.getX()),
						        Math.round(localStmRect.getY()),
						        Math.round(localStmRect.getWidth()),
						        Math.round(localStmRect.getHeight()),
						        _iState.getName()
						    );
						    System.out.println(rectRatio);
						}						// Added transition action
						actions += collectActions(indent, getEntry(_iState));
						
						m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "name"), 
							stateName,					// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							actions, 							// value
							modifier,							// modifier
							getFullNamespace(getStateMachineDiagram(stmRoot)).replace("::", "/") + "\t" + rectRatio,// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_top", "name"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							actions,							// value
							modifier,							// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				indent--;
				System.out.println(makeIndent(indent) + "# end def");
				
				// print transitions from states
				System.out.println(makeIndent(indent) + "def " + stateName + "_EventHandle(self, e, params):");
				System.out.println(makeIndent(indent) + "    self.sourceState = " + getStateMachineDiagram(stmRoot).getName() + "." + stateName);
				indent++;
				boolean firstRound = true;
				if (_iState != null) {
					List<String> internalEvents = new ArrayList<>();
					for (Transition iTrans: getInternalTransitions(_iState)) {
						if (!internalEvents.contains(getEvent(iTrans).trim())) {
							internalEvents.add(getEvent(iTrans).trim());
						}
					}	
					for (Transition iTrans: _iState.getOutgoings()) {
						if (internalEvents.contains(getEvent(iTrans).trim())) {
							m_bIsInternalTrans = true;
						}
						if (!getEvent(iTrans).trim().isEmpty()) {
							m_originTrans = iTrans;
							if (firstRound) {
								System.out.println(makeIndent(indent) + "if e == Events." + getEvent(iTrans).trim() + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "ext1st"), 
										getEvent(iTrans).trim(),			// name 
										m_iClass.getName(), 				// type
										containerName, 						// container
										"",		 							// value
										"", 								// modifier
										"",									// description 
										getStateMachineDiagram(stmRoot).getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								firstRound = false;
							} else {
								System.out.println(makeIndent(indent) + "elif e == Events." + getEvent(iTrans).trim() + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "extnxt"), 
										getEvent(iTrans).trim(),			// name 
										m_iClass.getName(), 				// type
										containerName, 						// container
										"",		 							// value
										"", 								// modifier
										"",									// description 
										getStateMachineDiagram(stmRoot).getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							indent++;
							if (!getGuard(iTrans).isEmpty()) {
								// print if
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								// print end-if
								System.out.println(makeIndent(indent) + "return True");
								System.out.println(makeIndent(indent) + "# end if");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
										getGuard(iTrans),					// name 
										m_iClass.getName(), 				// type
										"", 								// container
										getGuard(iTrans),		 			// value
										collectActions(indent, getAction(iTrans)),// modifier
										"",									// description 
										getStateMachineDiagram(stmRoot).getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
							} else {
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								System.out.println(makeIndent(indent) + "return True");
							}
							indent--;
						}
						m_bIsInternalTrans = false;
					}
				}
				try {
					if (_iState != null) {
						System.out.println(makeIndent(indent) + "return self." + containerName + "_EventHandle(e, params)");
						if (firstRound == false) {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "begin"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								"",		 							// value
								"", 								// modifier
								"",									// description 
								rgnDgrName							// scope
							));
						}
					} else {
						System.out.println(makeIndent(indent) + "return False");
						if (firstRound == false) {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_top", "begin"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								"",		 							// value
								"", 								// modifier
								"",									// description 
								rgnDgrName							// scope
							));
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				indent--;
				System.out.println(makeIndent(indent) + "# end def");
				
				// print state exit actions
				System.out.println(makeIndent(indent) + "def " + stateName + "_Exit(self):");
				indent++;
				System.out.println(makeIndent(indent) + "if self.Exitable(" + getStateMachineDiagram(stmRoot).getName() + "."  + stateName + "):");
				try {
					String actions = "";
					// if deepHistoryPt exists, deepHistoryPt <- lastEnteredState
					Vertex subDeepHistPt = findDeepHistoryPseudostate(_iState == null ? rgnVertices : getSubvertexes(_iState, 0));
					if (subDeepHistPt != null) {
						//String containingMachine = _iState != null ? findTargetMachineName(rgnName, rgnVertices, _iState) : stmRoot.getName();
						System.out.println(makeIndent(indent) + "    self.main." + subDeepHistPt.getName() + " = self.lastEnteredState");
						// ▲ deep_hist.extnxt
						actions += Utils.get(m_stxCsv.get(indent, "deep_hist", "extnxt"), 
							subDeepHistPt.getName(),			// name 
							m_iClass.getName(), 				// type
							"", 								// container
							"",						 			// value
							"",									// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}					
					// terminate regions if available
					int subRgnIndex = 1;
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (getSubvertexes(_iState, subRgnIndex).size() > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Terminate()");							
								// ■ substm.end
								actions += Utils.get(m_stxCsv.get(indent, "state_action", "end"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								);
							}
							subRgnIndex++;
						} catch (Exception e) {
							break;
						}
					}
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmDgrName = getStateMachineDiagram(_iState.getSubmachine()).getName();
						// terminate sub-machine if available
						System.out.println(makeIndent(indent) + "    self.main." + stateName + "Hsm.Terminate()");
						// ■ substm.begin
						// [cONTAINER]_Reset( p[tYPE], ( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[nAME] );
						actions += Utils.get(m_stxCsv.get(indent, "state_action", "end"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							subStmDgrName, 						// container
							"",									// value
							stateName + "Hsm",					// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
					}
					
					try {

						if (_iState != null) {
							actions += collectActions(indent, getExit(_iState));
							
							m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "end"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								actions, 							// value
								"", 								// modifier
								getFullNamespace(getStateMachineDiagram(stmRoot)).replace("::", "/") + "\t" + rectRatio,// description 
								getStateMachineDiagram(stmRoot).getName()// scope
							));
						} else {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_top", "end"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								actions, 							// value
								"", 								// modifier
								"",									// description 
								getStateMachineDiagram(stmRoot).getName()// scope
							));
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.out.println(makeIndent(indent) + "    self.DefaultExitAction('" + stateName + "')");
				if (_iState != null) {
					System.out.println(makeIndent(indent) + "    self." + containerName + "_Exit()");
				}
				indent--;
				System.out.println(makeIndent(indent) + "# end def");
			}
			public void start(Collection<Vertex> iVertices) {				
				checkStateBfr(null, null);
				super.start(iVertices);
			}			
		}.start(rgnVertices);

		/**
		 * print state-exit-map
		 */
		System.out.println(makeIndent(indent) + "def BgnTrans(self, targetState):");
		System.out.println(makeIndent(indent) + "    self.targetState = targetState");
		System.out.println(makeIndent(indent) + "    self.pseudoState = targetState");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "name"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "if self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    self." + rgnName + "_Exit()");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			getStateMachineDiagram(stmRoot).getName()// scope
		));
		new StateDeepTraverserRgn0() {
			protected void checkState(State iState, State container) {
				if (!(iState instanceof FinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    self." + iState.getName() + "_Exit()");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"", 									// value
							"", 									// modifier
							"", 									// description
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start(rgnVertices);
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "begin"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		
		/**
		 * print state-eventProc-map
		 */
		System.out.println(makeIndent(indent) + "def EventHandle(self, e, params):");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "name"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "if self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    return self." + rgnName + "_EventHandle(e, params)");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			getStateMachineDiagram(stmRoot).getName()// scope
		));		
		new StateDeepTraverserRgn0() {
			protected void checkState(State iState, State container) {
				if (!(iState instanceof FinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    return self." + iState.getName() + "_EventHandle(e, params)");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"",		 								// value
							"", 									// modifier
							"", 									// description
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}
		}.start(rgnVertices);
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "begin"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		
		
		/**
		 * print state-entry-map
		 */				
		System.out.println(makeIndent(indent) + "def EndTrans(self):");
		System.out.println(makeIndent(indent) + "    self.currentState = self.targetState"); 
		System.out.println(makeIndent(indent) + "    self.isExternalTrans = False");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "name"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		indent++;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			getStateMachineDiagram(stmRoot).getName()// scope
		));		
		System.out.println(makeIndent(indent) + "if self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    self." + rgnName + "_Enter()");
		new StateDeepTraverserRgn0() {
			protected void checkState(State iState, State container) {
				if (!(iState instanceof FinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    self." + iState.getName() + "_Enter()");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"",		 								// value
							"", 									// modifier
							"", 									// description
							getStateMachineDiagram(stmRoot).getName()// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start(rgnVertices);
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "begin"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			getStateMachineDiagram(stmRoot).getName()		// scope
		));

		// print null-triggered transitions
		System.out.println(makeIndent(indent) + "def DefaultTrans(self):");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "name"), 
			rgnName,							// name 
			m_iClass.getName(), 				// type
			rgnName + "Hsm",	 						// container
			"",		 							// value
			"", 								// modifier
			"",									// description 
			getStateMachineDiagram(stmRoot).getName()// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "self.sourceState = self.currentState");
		System.out.println(makeIndent(indent) + "self.lcaState = 0");
		boolean firstRound = true;
		for (Transition iTrans: getTransitions(stmRoot)) {
			Vertex iSrcVtx = iTrans.getSource();
			// find all transitions originated from a vertex belong to this region only
			String targetMachineName = findTargetMachineName(rgnName, rgnVertices, iSrcVtx, null);
			if (getEvent(iTrans).trim().isEmpty() && targetMachineName != null && targetMachineName.equals(rgnName + "Hsm")) {
				m_originTrans = iTrans;
				if (iSrcVtx != null) {
					if (iSrcVtx instanceof Pseudostate) {
						Pseudostate iPstate = (Pseudostate)iSrcVtx;
						// check if external transition or local transition
						if (isEntryPointPseudostate(iPstate) && getContainer(iPstate) == null/* || iPstate.isStubState()*/
						 || isExitPointPseudostate(iPstate) && getContainer(iPstate) != null
						 //|| isDeepHistoryPseudostate(iPstate)
						 //|| isShallowHistoryPseudostate(iPstate)
						 //|| isChoicePseudostate(iPstate)
					     //|| isJunctionPseudostate(iPstate)
					     //|| isForkPseudostate(iPstate)
						 //|| isJoinPseudostate(iPstate)
						 || isInitialPseudostate(iPstate)
						) {
							String containerName = rgnName;
							State container = null;
							if (getContainer(iSrcVtx) != null && getContainer(iSrcVtx) instanceof State) {
								container = (State)getContainer(iSrcVtx);
								if (!Arrays.asList(getSubvertexes(container, 0)).contains(iSrcVtx)) {	// the pseudo-state belong to a region top
									container = null;
								}
							}
							if (container != null) {
								containerName = container.getName();
							}
							String pseudoStateName = iPstate.getName();
							if (isInitialPseudostate(iPstate)
							 || isEntryPointPseudostate(iPstate) && getContainer(iPstate) == null
							) {
								String ifCondition = Utils.get(m_stxCsv.get("default_trans", "ext1st"), 
									iSrcVtx.getName(),					// name 
									m_iClass.getName(), 				// type
									getStateMachineDiagram(stmRoot).getName(),// container
									containerName,						// value
									"",									// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								);
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if self.pseudoState == " + getStateMachineDiagram(stmRoot).getName() + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										ifCondition						// name
									));
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + "elif self.pseudoState == " + getStateMachineDiagram(stmRoot).getName() + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "extnxt"), 
										ifCondition						// name
									));
								}
							} else {  // isExitPointPseudostate(iPstate) && getContainer(iPstate) != null
								container = (State)getContainer(iSrcVtx);
								containerName = container.getName();
								String subMachineDgrName = "";
								if (container.isSubmachineState()) {
									subMachineDgrName = getStateMachineDiagram(container.getSubmachine()).getName();
								}
								String ifCondition = Utils.get(m_stxCsv.get("default_trans", "extnxt"), 
									iSrcVtx.getName(),					// name 
									m_iClass.getName(), 				// type
									subMachineDgrName, 					// container
									containerName,						// value
									"",									// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								);
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + container.getName() + " and self.pseudoState == " + subMachineDgrName + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										ifCondition						// name
									));
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + "elif self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + container.getName() + " and self.pseudoState == " + subMachineDgrName + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "extnxt"), 
										ifCondition						// name
									));
								}
							}
							indent++;
							if (!getGuard(iTrans).isEmpty()) {
								// print if
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								// print end-if
								System.out.println(makeIndent(indent) + "return True");
								System.out.println(makeIndent(indent) + "# end if");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
										getGuard(iTrans),					// name 
										m_iClass.getName(), 				// type
										"", 								// container
										getGuard(iTrans),		 			// value
										collectActions(indent, getAction(iTrans)),// modifier
										"",									// description 
										getStateMachineDiagram(stmRoot).getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
							} else {
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								System.out.println(makeIndent(indent) + "return True");
							}
							indent--;								
						}else {
							// throws error
						}
					}else if (iSrcVtx instanceof State && !isJoinBar(iTrans.getTarget())) {
						State iState = (State)iSrcVtx;
						String syntax;
						if (firstRound) {
							System.out.println(makeIndent(indent) + "if self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + iState.getName() + "\\");
							syntax = m_stxCsv.get(indent, "branch", "ext1st");
							firstRound = false;
						} else {
							System.out.println(makeIndent(indent) + "elif self.currentState == " + getStateMachineDiagram(stmRoot).getName() + "." + iState.getName() + "\\");
							syntax = m_stxCsv.get(indent, "branch", "extnxt");
						}
						int subRgnIdx = 1;
						String isCompletedConditions = "";
						while (true) {											// All sub-regions must be completed
							try {
								if (getSubvertexes(iState, subRgnIdx).size() > 0) {
									String subRgnName = makeRgnName(iState, subRgnIdx);
									System.out.println(makeIndent(indent) + "    and self.main." + subRgnName + "Hsm.pseudoState == self.main." + subRgnName + "\\");
									isCompletedConditions += Utils.get(m_stxCsv.get(indent, "region", "extnxt"),
										subRgnName,										// name
										m_iClass.getName(),								// type
										subRgnName + "Hsm",								// container
										subRgnName,										// value
										targetMachineName,								// modifier
										getDefinition(iTrans),							// description
										getStateMachineDiagram(stmRoot).getName()		// scope
									);
								}
							} catch (Exception e) {
								break;
							}
							subRgnIdx++;
						}
						// sub-machine, if have, must be completed
						if (iState.isSubmachineState()) {
							String subStmName = getStateMachineDiagram(iState.getSubmachine()).getName();
							System.out.println(makeIndent(indent) + "    and self.main." + subStmName + "Hsm.pseudoState == self.main." + subStmName + "\\");
							isCompletedConditions += Utils.get(m_stxCsv.get(indent, "substm", "extnxt"),
								subStmName,										// name
								m_iClass.getName(),								// type
								subStmName,										// container
								subStmName,										// value
								iState.getName() + "Hsm",						// modifier
								getDefinition(iTrans),							// description
								getStateMachineDiagram(stmRoot).getName()		// scope
							);
						}
						
						System.out.println(makeIndent(indent) + ":");
						String ifCondition = Utils.get(m_stxCsv.get(indent, "default_trans", "extnxt"), 
							iSrcVtx.getName(),					// name 
							m_iClass.getName(), 				// type
							getStateMachineDiagram(stmRoot).getName(),// container
							iSrcVtx.getName(),					// value
							isCompletedConditions,				// modifier
							"",									// description 
							getStateMachineDiagram(stmRoot).getName()// scope
						);
						m_writer.write(Utils.get(syntax, 
							ifCondition							// name 
						));
						indent++;
						if (!getGuard(iTrans).isEmpty()) {
							// print if
							printTransition(stmRoot, rgnName, rgnVertices, iTrans);
							// print end-if
							System.out.println(makeIndent(indent) + "return True");
							System.out.println(makeIndent(indent) + "#endif");
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									getGuard(iTrans),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									getGuard(iTrans),		 			// value
									collectActions(indent, getAction(iTrans)),// modifier
									"",									// description 
									getStateMachineDiagram(stmRoot).getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						} else {
							printTransition(stmRoot, rgnName, rgnVertices, iTrans);
							System.out.println(makeIndent(indent) + "return True");
						}
						indent--;								
					}else if (iSrcVtx instanceof State && isJoinBar(iTrans.getTarget())) {
						// Check if this join bar belongs to this region
						Pseudostate iPstate = (Pseudostate)iTrans.getTarget();
						Collection<Transition> outgoings = iPstate.getOutgoings();						
						
						String joinBarMachineName = findTargetMachineName(rgnName, rgnVertices, iPstate, null);
						if (joinBarMachineName != null && joinBarMachineName.equals(rgnName + "Hsm")) {
							System.out.println(makeIndent(indent) + "# begin joining");
							String isInConditions = "";
							for (Transition incoming: iPstate.getIncomings()) {
								if (incoming != iTrans) {
									if (incoming.getSource() instanceof State && getEvent(incoming).trim().isEmpty()) {
										State sourceState = (State)incoming.getSource();
										StringBuilder containingRgn = new StringBuilder();
										targetMachineName = findTargetMachineName(stmRoot.getName(), getVertexes(stmRoot), sourceState, containingRgn);
										String targetMachineRef;
										if (targetMachineName.equals(rgnName + "Hsm")) {
											targetMachineRef = "self";
										} else {
											targetMachineRef = "self.main." + targetMachineName;
										}
										if (isInConditions.isEmpty()) {
											System.out.println(makeIndent(indent) + "if IsIn(" + targetMachineRef + ".currentState," + getStateMachineDiagram(stmRoot).getName() + "." + sourceState + ")\\");
											try {
												// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
												isInConditions = Utils.get(m_stxCsv.get(indent, "trans_action", "ext1st"),
													sourceState.getName(),							// name
													m_iClass.getName(),								// type
													targetMachineName,								// container
													sourceState.getName(),							// value
													targetMachineName,								// modifier
													getDefinition(iTrans),							// description
													getStateMachineDiagram(stmRoot).getName()		// scope
												);
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
											firstRound = false;
										} else {
											System.out.println(makeIndent(indent) + " and IsIn(" + targetMachineRef + ".currentState, " + getStateMachineDiagram(stmRoot).getName() + "." + sourceState + ")\\");
											try {
												// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
												isInConditions += Utils.get(m_stxCsv.get(indent, "trans_action", "extnxt"),
													sourceState.getName(),							// name
													m_iClass.getName(),								// type
													targetMachineName,								// container
													sourceState.getName(),							// value
													targetMachineName,								// modifier
													getDefinition(iTrans),							// description
													getStateMachineDiagram(stmRoot).getName()		// scope
												);
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}
									} else {
										System.out.println("★★★ERROR★★★: Joining from other regions cannot have event name");
									}
								}						
							}
							if (outgoings.size() == 1) {
								System.out.println(makeIndent(indent) + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										isInConditions,						// name 
										m_iClass.getName(), 				// type
										"", 								// container
										isInConditions,			 			// value
										collectActions(indent, getAction(iTrans)),// modifier
										"",									// description 
										getStateMachineDiagram(stmRoot).getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								indent++;
								printTransition(stmRoot, rgnName, rgnVertices, toArray(outgoings)[0]);
								indent--;
							}							
						}
					}else if (iSrcVtx instanceof FinalState) {
						// throws error
					}else {
						// throws error
					}
				}
			}
		}
		// if this region/machine is active and pseudoState != currentState : transit to pseudoState
		if (firstRound) {
			System.out.println(makeIndent(indent) + "if self.currentState != 0 and self.currentState != self.pseudoState" + ":");
			firstRound = false;
		} else {
			System.out.println(makeIndent(indent) + "elif self.currentState != 0 and self.currentState != self.pseudoState" + ":");
		}
		System.out.println(makeIndent(indent) + "    self.BgnTrans(self.pseudoState)");
		System.out.println(makeIndent(indent) + "    self.EndTrans()");
		System.out.println(makeIndent(indent) + "    return True");
		System.out.println(makeIndent(indent) + "self.lastEnteredStateRecovering = False");
		System.out.println(makeIndent(indent) + "return False");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "begin"), 
			rgnName,							// name 
			m_iClass.getName(), 				// type
			rgnName + "Hsm",	 						// container
			"",		 							// value
			"", 								// modifier
			"",									// description 
			getStateMachineDiagram(stmRoot).getName()// scope
		));
		indent--;
		System.out.println(makeIndent(indent) + "# end def");

		m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "end"), 
			rgnName,							// name 
			m_iClass.getName(), 				// type
			rgnName + "Hsm",	 						// container
			"",		 							// value
			"", 								// modifier
			"",									// description 
			getStateMachineDiagram(stmRoot).getName()// scope
		));		
		
		System.out.println(makeIndent(indent) + "def Initiate(self, entryPoint = 0):");
		System.out.println(makeIndent(indent) + "    if entryPoint != 0:");
		System.out.println(makeIndent(indent) + "        self.pseudoState = entryPoint");
		System.out.println(makeIndent(indent) + "        return");
		System.out.println(makeIndent(indent) + "    if self.pseudoState == 0:");
		System.out.println(makeIndent(indent) + "        self.pseudoState = " + getStateMachineDiagram(stmRoot).getName() + "." + rgnName);
		System.out.println(makeIndent(indent) + "    self.BgnTrans(self.pseudoState)");
		System.out.println(makeIndent(indent) + "    self.EndTrans()");
		System.out.println(makeIndent(indent) + "# end def");

		System.out.println(makeIndent(indent) + "def Terminate(self):");
		System.out.println(makeIndent(indent) + "    self.BgnTrans(0)");
		System.out.println(makeIndent(indent) + "    self.EndTrans()");
		System.out.println(makeIndent(indent) + "# end def");
		
	}
}
