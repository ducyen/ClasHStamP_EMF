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

import com.change_vision.jude.api.inf.exception.*;
import com.change_vision.jude.api.inf.model.*;
import com.change_vision.jude.api.inf.presentation.*;
import com.sun.xml.internal.ws.message.RootElementSniffer;

/**
 * Python template
 * @author DucHM28
 * 
 * from enum import Enum, auto
 * import sys
 * 
 * def IsIn( leaf, composite ):
 *     return ( composite >= leaf and ( composite & leaf ) > 0 )
 * 
 * 
 * def Push(stack, item):
 *     pass
 * 
 * def Pop(stack):
 *     return None
 * 
 * class Statemachine:
 *     currentState = 0
 *     sourceState = 0
 *     lcaState = 0
 *     targetState = 0
 *     pseudoState = 0
 *     isExternalTrans = 0
 *     def Enterable(self, thisState):
 *         isThisLca = IsIn(self.lcaState, thisState)
 *         if not isThisLca or self.lcaState == 0:
 *             return True
 *         return False
 *     def Exitable(self, thisState):
 *         isThisLca = IsIn(self.sourceState, thisState) and IsIn(self.targetState, thisState)
 *         if not isThisLca or self.isExternalTrans:
 *             self.isExternalTrans = self.isExternalTrans and not isThisLca
 *             return True
 *         else:
 *             self.lcaState = thisState
 *         return False
 * 
 * gVar = 0
 */

public class RStmGenerator extends TBaseGenerator {

	/**
	 * m_sortedStmDgrs
	 */
	private List<IStateMachine> m_sortedStmDgrs = new ArrayList<>();
	
	private IStateMachine m_iMainStm = null;
	
	private IStateMachine m_stmRoot = null;
	
	private ITransition m_originTrans = null;

	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 * @param iMainStm
	 */
	public RStmGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		
		/*
		 * csv format: 
		 * 		[->][Scope]_Initiate( p[tYPE], (([cONTAINER]*)pStm->base.pOwner)->[sCOPE], &[nAME], [mODIFIER]_[vALUE] );
		 * [name]		: super deep history if available
		 * [type]		: main class
		 * [container]	: state machine entity's owner
		 * [value]		: state value
		 * [modifier]	: state machine diagram name
		 * [desc]		: comments if available
		 * [scope]		: 
		 */
		
		for (IDiagram iDgr: m_iClass.getDiagrams()) {
			if (iDgr instanceof IStateMachineDiagram) {
				m_sortedStmDgrs.add(((IStateMachineDiagram)iDgr).getStateMachine());
			}
		}
		Collections.sort(m_sortedStmDgrs, new Comparator<IStateMachine>() {
			@Override
			public int compare(IStateMachine o1, IStateMachine o2) {
				if (isSubmachineOf(o1, o2.getVertexes())) 
					return -1;
				if (isSubmachineOf(o2, o1.getVertexes())) 
					return 1;
				return 0;
			}
			
		});
		
		if (m_sortedStmDgrs.size() > 0) {
			m_iMainStm = m_sortedStmDgrs.get(m_sortedStmDgrs.size()-1);
		}
	}

	/**
	 * StateDeepTraverserRgn0
	 * @author DucHM28
	 *
	 */
	private class StateDeepTraverserRgn0 {
		protected int m_level = 0;
		protected void checkPseudostate(IPseudostate iPseudostate, IState container) {}
		protected void checkStateBfr(IState iState, IState container) {}
		protected void checkState(IState iState, IState container) {}
		protected void traverse(IVertex iVtx, IState container) {
			m_level++;
			if (iVtx instanceof IState) {
				IState iState = (IState)iVtx;
				try {
					checkStateBfr(iState, container);
					// traverse region 0 vertices
					if (iState.getSubvertexes(0).length > 0) {
						for (IVertex iSubVtx: iState.getSubvertexes(0)) {
							traverse(iSubVtx, iState);
						}
					}
					checkState(iState, container);
				} catch (InvalidUsingException e) {
				}
			} else if (iVtx instanceof IPseudostate) {
				checkPseudostate((IPseudostate)iVtx, container);
			} else {
				System.out.println("★★★ERROR★★★ Traverse to an unknown state type");						
			}
			m_level--;
		}	
		public StateDeepTraverserRgn0() {}
		public void start(IVertex[] iVertices) {
			for (IVertex iVtx: iVertices) {
				traverse(iVtx, null);
			}
		}
	}
	
	/**
	 * StateDeepTraverser
	 * @author DucHM28
	 *
	 */
	private class StateDeepTraverser {
		protected int m_level = 0;
		protected void checkPseudostate(IPseudostate iPseudostate, IState container, int rgnIndex) {}
		protected void checkRegionBfr(IState iState, int subRgnIndex, IState container, int rgnIndex) {}
		protected void checkRegion(IState iState, int subRgnIndex, IState container, int rgnIndex) {}
		protected void checkState(IState iState, IState container, int rgnIndex) {}
		protected void checkStateBfr(IState iState, IState container, int rgnIndex) {}
		protected void traverse(IVertex iVtx, IState container, int rgnIndex) {
			m_level++;
			if (iVtx instanceof IState) {
				IState iState = (IState)iVtx;
				int subRgnIdx = 0;
				while (true) {
					try {
						if (subRgnIdx == 0) {
							checkStateBfr(iState, container, rgnIndex);
						}
						if (iState.getSubvertexes(subRgnIdx).length > 0) {
							if (subRgnIdx > 0) {
								checkRegionBfr(iState, subRgnIdx, container, rgnIndex);
							}
							// traverse regions vertices
							for (IVertex iSubVtx: iState.getSubvertexes(subRgnIdx)) {
								traverse(iSubVtx, iState, subRgnIdx);
							}
							if (subRgnIdx > 0) {
								checkRegion(iState, subRgnIdx, container, rgnIndex);
							}
						}
						if (subRgnIdx == 0) {
							checkState(iState, container, rgnIndex);
						}
					} catch (InvalidUsingException e) {
						break;
					}
					subRgnIdx++;
				}
			} else if (iVtx instanceof IPseudostate) {
				checkPseudostate((IPseudostate)iVtx, container, rgnIndex);
			} else {
				System.out.println("★★★ERROR★★★ Traverse to an unknown state type");						
			}
			m_level--;
		}	
		public StateDeepTraverser() {}
		public void start(IVertex[] iVertices) {
			for (IVertex iVtx: iVertices) {
				traverse(iVtx, null, 0);
			}
		}
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
	private String makeRgnName(IState iState, int rgnIndex) {
		return rgnIndex == 0 ? iState.getName() : iState.getName() + "Rgn" + rgnIndex;
	}
	
	/**
	 * checkIfExternalTrans
	 * @param targetState
	 * @return
	 */
	private boolean checkIfExternalTrans(ITransition lastTrans) {
		IVertex targetState = lastTrans.getTarget();
		try {
			IVertex commonState = null;
			if (m_originTrans.getSource() == targetState) {
				commonState = targetState;
			}
			IVertex traversingVertex = targetState;
			// find least common ancestor
			while (traversingVertex != m_originTrans.getSource()) {
				if (traversingVertex.getContainer() != null) {
					traversingVertex = (IState)traversingVertex.getContainer();
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
				if (traversingVertex.getContainer() != null) {
					traversingVertex = (IState)traversingVertex.getContainer();
					if (traversingVertex == targetState) {
						commonState = traversingVertex;
						break;
					}
				} else {
					break;
				}
			}
			
			if (commonState != null) {						
				Rectangle2D iRect = null;
				for (IPresentation iPresentxn : commonState.getPresentations()) {
					INodePresentation iNode = (INodePresentation)iPresentxn;
					iRect = iNode.getRectangle();
				}
				boolean isExternalTrans = false;
				for (IPresentation iPresentxn : m_originTrans.getPresentations()) {
					ILinkPresentation iLink = (ILinkPresentation)iPresentxn;
					for (Point2D pt : iLink.getAllPoints()) {
						if (pt.getX() < iRect.getMinX() || pt.getX() > iRect.getMaxX() ||
							pt.getY() < iRect.getMinY() || pt.getY() > iRect.getMaxY())
						{
							isExternalTrans = true;
						}
					}
				}
				for (IPresentation iPresentxn : lastTrans.getPresentations()) {
					ILinkPresentation iLink = (ILinkPresentation)iPresentxn;
					for (Point2D pt : iLink.getAllPoints()) {
						if (pt.getX() < iRect.getMinX() || pt.getX() > iRect.getMaxX() ||
							pt.getY() < iRect.getMinY() || pt.getY() > iRect.getMaxY())
						{
							isExternalTrans = true;
						}
					}
				}
				return isExternalTrans;
			}
		} catch (InvalidUsingException e) {
			e.printStackTrace();
		}		
		return false;
	}
	
	/**
	 * isSubmachineOf
	 * @param iSubmachine
	 * @param iStm
	 * @return
	 */
	private boolean m_bResult = false;
	private boolean isSubmachineOf(IStateMachine iSubmachine, IVertex[] iVertices) {
		m_bResult = false;
		new StateDeepTraverser() {				
			protected void checkState(IState iState, IState container, int rgnIdx) {
				if (iState.isSubmachineState()) {
					if (iState.getSubmachine() == iSubmachine) {
						m_bResult = true;
					} else if (isSubmachineOf(iSubmachine, iState.getSubmachine().getVertexes())) {
						m_bResult = true;
					}
				}
			}
		}.start(iVertices);
		return m_bResult;
	}
	
	/**
	 * findTargetMachineName
	 * @param iStm
	 * @param targetState
	 * @return null if not found
	 *         state machine name if targetVertex is in top levels
	 *         containing region name in other cases
	 */
	private String findTargetMachineName(String rgnName, IVertex[] iVertices, IVertex targetVertex, StringBuilder targetRgnName) {
		class ContainingRegionFinder extends StateDeepTraverser {
			public IState m_containingState = null;
			public int	m_containingRgnIndex = 0;
			public boolean m_found = false;
			private IVertex m_targetVertex = targetVertex;
			private Stack<IState> regionStateStack = new Stack<IState>();
			private Stack<Integer> regionIndexStack = new Stack<Integer>();
			protected void checkPseudostate(IPseudostate iPseudostate, IState container, int rgnIndex) {
				if (m_targetVertex == iPseudostate) {
					m_found = true;					
				}
			}
			protected void checkRegionBfr(IState iState, int subRgnIndex, IState container, int rgnIndex) {
				if (!m_found) {
					m_containingState = iState;
					m_containingRgnIndex = subRgnIndex;
					regionStateStack.push(iState);
					regionIndexStack.push(subRgnIndex);
				}
			}
			protected void checkRegion(IState iState, int subRgnIndex, IState container, int rgnIndex) {
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
			protected void checkState(IState iState, IState container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					for (IVertex subVertex: iState.getSubvertexes()) {
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
	private void printTransition(IStateMachine stmRoot, String rgnName, IVertex[] iVertices, ITransition iTrans) {
		// ■ branch.name
		// ■ branch.ext1st
		// ■ branch.extnxt
		// ■ branch.begin
		// ■ branch.end
		// if target is IState
		if (iTrans.getTarget() instanceof IState) {
			
			// if has Guard
			if (!iTrans.getGuard().isEmpty()) {
				// print If Guard
				System.out.println(makeIndent(indent) + (iTrans.getGuard().equalsIgnoreCase("else") ? "else:" : ("if " + iTrans.getGuard() + ":")));
				try {
					if (iTrans.getGuard().equalsIgnoreCase("else")) {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "begin"), 
							iTrans.getGuard(),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							iTrans.getGuard(),		 			// value
							collectActions(indent, iTrans.getAction()),// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
							iTrans.getGuard(),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							iTrans.getGuard(),		 			// value
							collectActions(indent, iTrans.getAction()),// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
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
		} else {// else (target is not IState)
			// if has Guard
			if (!iTrans.getGuard().isEmpty()) {
				// print If Guard
				System.out.println(makeIndent(indent) + (iTrans.getGuard().equalsIgnoreCase("else") ? "else:" : ("if " + iTrans.getGuard() + ":")));
				try {
					if (iTrans.getGuard().equalsIgnoreCase("else")) {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "begin"), 
							iTrans.getGuard(),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							iTrans.getGuard(),		 			// value
							collectActions(indent, iTrans.getAction()),// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
							iTrans.getGuard(),					// name 
							m_iClass.getName(), 				// type
							"", 								// container
							iTrans.getGuard(),		 			// value
							collectActions(indent, iTrans.getAction()),// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				indent++;
				// print Action if have
				if (!iTrans.getAction().trim().isEmpty()) {
					System.out.println(makeIndent(indent) + iTrans.getAction().trim());
					try {
						String actions = collectActions(indent, iTrans.getAction().trim());
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
				if (!iTrans.getAction().trim().isEmpty()) {
					System.out.println(makeIndent(indent) + iTrans.getAction().trim());
					try {
						String actions = collectActions(indent, iTrans.getAction().trim());
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
	private void TransitionTo(ITransition iTrans, String targetStateName, IStateMachine stmRoot, String rgnName) {
		IVertex iTgtVtx = iTrans.getTarget();
		String targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), iTgtVtx, null);
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
		if (targetMachineName.equals(rgnName + "Hsm")) {
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
			System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + stmRoot.getStateMachineDiagram().getName() + "." + targetStateName + ")");
			// print Action if have
			if (!iTrans.getAction().trim().isEmpty()) {
				System.out.println(makeIndent(indent) + iTrans.getAction().trim());
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
						collectActions(indent, iTrans.getAction()),// modifier
						iTrans.getDefinition(),							// description
						stmRoot.getStateMachineDiagram().getName()		// scope
					));
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "end"),
						targetStateName,								// name
						m_iClass.getName(),								// type
						targetMachineName,								// container
						"",												// value
						collectActions(indent, iTrans.getAction()),// modifier
						iTrans.getDefinition(),							// description
						stmRoot.getStateMachineDiagram().getName()		// scope
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
			System.out.println(makeIndent(indent) + targetMachineRef + "Hsm.Initiate(" + stmRoot.getStateMachineDiagram().getName() + "." + targetStateName + ")");
			try {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "region", "begin"),
					targetStateName,								// name
					m_iClass.getName(),								// type
					targetMachineName,								// container
					"",												// value
					targetMachineName,								// modifier
					iTrans.getDefinition(),							// description
					stmRoot.getStateMachineDiagram().getName()		// scope
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
	private void TraverseTransition(IStateMachine stmRoot, String rgnName, IVertex[] iVertices, ITransition iTrans) {
		IVertex iTgtVtx = iTrans.getTarget();
		if (iTgtVtx != null) {
			if (iTgtVtx instanceof IPseudostate) {				
				IPseudostate iPstate = (IPseudostate)iTgtVtx;
				ITransition[] outgoings = iTgtVtx.getOutgoings();
				// check if external transition or local transition
				if (iPstate.isChoicePseudostate()) {
					if (outgoings.length == 2) {
						ITransition ifChoice, elseChoice;
						if (outgoings[0].getGuard().equalsIgnoreCase("else")) {
							ifChoice = outgoings[1];
							elseChoice = outgoings[0];
						} else if (outgoings[1].getGuard().equalsIgnoreCase("else")) {
							ifChoice = outgoings[0];
							elseChoice = outgoings[1];
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
									iTrans.getGuard(),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									iTrans.getGuard(),		 			// value
									collectActions(indent, iTrans.getAction()),// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else if (outgoings.length == 1) {
						ITransition ifChoice = outgoings[0];
						if (ifChoice != null) {
							// print if
							printTransition(stmRoot, rgnName, iVertices, ifChoice);
							// print end-if
							System.out.println(makeIndent(indent) + "# end if");
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									iTrans.getGuard(),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									iTrans.getGuard(),		 			// value
									collectActions(indent, iTrans.getAction()),// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else {
						System.out.println("★★★ERROR★★★: Choice cannot have more than 2 outgoings");						
					}
				}else if (iPstate.isJunctionPseudostate()) {
					// traverse junction's outgoing
					if (outgoings.length == 1) {
						printTransition(stmRoot, rgnName, iVertices, outgoings[0]);
					} else {
						System.out.println("★★★ERROR★★★: Junction must have only one outgoing");
					}
				}else if (iPstate.isDeepHistoryPseudostate() || iPstate.isShallowHistoryPseudostate()) {
					// print curState -> shallowHistName & thisMachine's bit mask
					// if other regions existed: print targetRegion.pseudoState -> shallowHistName
					System.out.println(makeIndent(indent) + "if self.main." + iPstate.getName() + " != 0:");
					StringBuilder containingRgn = new StringBuilder();
					String targetHsm = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), iPstate, containingRgn);
					indent++;
					System.out.println(makeIndent(indent) + "self.lastEnteredStateRecovering = True");
					String targetStateName = iTgtVtx.getName();
					// print BgnTrans
					System.out.println(makeIndent(indent)  + "self.BgnTrans(self.main." + targetStateName + ")");
					// print Action if have
					if (!iTrans.getAction().trim().isEmpty()) {
						System.out.println(makeIndent(indent) + iTrans.getAction().trim());
					}
					// print EndTrans
					System.out.println(makeIndent(indent) + "self.EndTrans()");									
					indent--;
					StringWriter tempWriter = new StringWriter();
					Writer originalWriter = m_writer;  // Save the original FileWriter
					m_writer = tempWriter;					
					if (iPstate.getOutgoings().length > 0) {
						System.out.println(makeIndent(indent) + "else:");
						indent++;
						printTransition(stmRoot, rgnName, iVertices, iPstate.getOutgoings()[0]);
						indent--;
					} else {
						indent++;
						targetStateName = rgnName;
						IState container = null;
						if (iTgtVtx.getContainer() != null) {
							container = (IState)iTgtVtx.getContainer();
							try {
								if (!Arrays.asList(container.getSubvertexes(0)).contains(iTgtVtx)) {	// the pseudo-state belong to a region top
									container = null;
								}
							} catch (InvalidUsingException e) {
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
						String syntax = iPstate.isShallowHistoryPseudostate() ? m_stxCsv.get(indent, "history", "begin"): m_stxCsv.get(indent, "deep_hist", "begin");
						m_writer.write(Utils.get(syntax, 
							iPstate.getName(),					// name 
							m_iClass.getName(), 				// type
							targetHsm,							// container
							tempWriter.toString(),	 			// value
							collectActions(indent, iTrans.getAction().trim()),// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
				}else if (iPstate.isEntryPointPseudostate() || iPstate.isStubState()) {
					// if target's container does not have sub-machine: throws error
					// else: print subMachine.pseudoState -> entryPoint state
					// if target belongs to this region: print curState -> entryPt's container
					// if target belongs to other region: print targetMachine.curState -> entryPt's container
					IState targetState = (IState)iPstate.getContainer();
					String targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), targetState, null);
					String targetMachineRef;
					if (targetMachineName.equals(rgnName + "Hsm")) {
						targetMachineRef = "self";
					} else {
						targetMachineRef = "self.main." + targetMachineName;
					}
					// print BgnTrans
					System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + stmRoot.getStateMachineDiagram().getName() + "." + targetState.getName() + ")");
					// print Action if have
					IState container = (IState)iPstate.getContainer();
					System.out.println(makeIndent(indent) + "self.main." + targetState + "Hsm.Initiate(self.lastEnteredStateRecovering, _" + container.getSubmachine().getName() + "Hsm." + iPstate.getName() + ")");
					if (!iTrans.getAction().trim().isEmpty()) {
						System.out.println(makeIndent(indent) + iTrans.getAction().trim());
					}
					System.out.println(makeIndent(indent) + targetMachineRef + ".EndTrans()");
					String actions = "";
					try {
						actions = collectActions(indent, iTrans.getAction());
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					try {						
						actions += Utils.get(m_stxCsv.get(indent, "substm", "begin"), 
							iPstate.getName(),							// name 
							m_iClass.getName(), 						// type
							container.getSubmachine().getStateMachineDiagram().getName(),// container
							"",											// value
							targetState.getName() + "Hsm",						// modifier
							"",											// description 
							stmRoot.getStateMachineDiagram().getName()	// scope
						);
						m_writer.write(actions);
						/*
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
							targetState.getName(),							// name
							m_iClass.getName(),								// type
							targetMachineName,								// container
							"",												// value
							actions,								// modifier
							iTrans.getDefinition(),							// description
							stmRoot.getStateMachineDiagram().getName()		// scope
						));
						*/
						TransitionTo(iTrans, targetState.getName(), stmRoot, targetMachineName);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (iPstate.isExitPointPseudostate()) {
					// if parentMachine is existed: print parentMachine.pseudoState -> exitPoint state
					String targetMachineName = stmRoot.getName() + "Hsm";
					String targetMachineRef;
					if (targetMachineName.equals(rgnName + "Hsm")) {
						targetMachineRef = "self";
					} else {
						targetMachineRef = "self.main." + targetMachineName;
					}
					// print BgnTrans
					System.out.println(makeIndent(indent) + targetMachineRef + ".BgnTrans(" + stmRoot.getStateMachineDiagram().getName() + "." + stmRoot.getName() + ")");
					// print Action if have
					System.out.println(makeIndent(indent) + "self.parent.pseudoState = " + stmRoot.getStateMachineDiagram().getName() + "." + iPstate.getName());
					if (!iTrans.getAction().trim().isEmpty()) {
						System.out.println(makeIndent(indent) + iTrans.getAction().trim());
					}
					System.out.println(makeIndent(indent) + targetMachineRef + ".EndTrans()");
					IState container = (IState)iPstate.getContainer();
					String actions = "";
					try {
						actions = collectActions(indent, iTrans.getAction());
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
							stmRoot.getStateMachineDiagram().getName()	// scope
						);
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_action", "begin"),
							stmRoot.getName(),							// name
							m_iClass.getName(),							// type
							targetMachineName,							// container
							"",											// value
							actions,									// modifier
							iTrans.getDefinition(),						// description
							stmRoot.getStateMachineDiagram().getName()	// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (iPstate.isForkPseudostate()) {
					// traverse all outgoings
					System.out.println(makeIndent(indent) + "# begin forking");
					// find out-going direct to state belonging to the machine same as the origin
					ITransition mainTrans = null;
					for (ITransition outgoing: outgoings) {
						String sourceMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), m_originTrans.getSource(), null);
						String targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), outgoing.getTarget(), null);
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
					System.out.println(makeIndent(indent) + "self.BgnTrans(" + stmRoot.getStateMachineDiagram().getName() + "." + mainTrans.getTarget().getName() + ")");
					
					StringWriter tempWriter = new StringWriter();
					Writer originalWriter = m_writer;  // Save the original FileWriter
					m_writer = tempWriter;
					
					for (ITransition outgoing: outgoings) {
						if (outgoing != mainTrans) {
							printTransition(stmRoot, rgnName, iVertices, outgoing);
						}
					}
					m_writer = originalWriter;					
					
					System.out.println(makeIndent(indent) + "self.EndTrans()");
					System.out.println(makeIndent(indent) + "# end forking");
					String actions = "";
					try {
						actions = collectActions(indent, iTrans.getAction());
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
							actions,								// modifier
							iTrans.getDefinition(),							// description
							stmRoot.getStateMachineDiagram().getName()		// scope
						));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if (iPstate.isJoinPseudostate()) {
					// if joint bar belongs to this machine: print isIn(..) from other incoming transitions.
					System.out.println(makeIndent(indent) + "# begin joining");
					boolean firstRound = true;
					String isInConditions = "";
					for (ITransition incoming: iPstate.getIncomings()) {
						if (incoming != iTrans) {
							if (incoming.getSource() instanceof IState && incoming.getEvent().trim().isEmpty()) {
								IState sourceState = (IState)incoming.getSource();
								StringBuilder containingRgn = new StringBuilder();
								String targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), sourceState, containingRgn);
								String targetMachineRef;
								if (targetMachineName.equals(rgnName + "Hsm")) {
									targetMachineRef = "self";
								} else {
									targetMachineRef = "self.main." + targetMachineName;
								}
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if IsIn(" + targetMachineRef + ".currentState," + stmRoot.getStateMachineDiagram().getName() + "." + sourceState + ")\\");
									try {
										// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
										isInConditions = Utils.get(m_stxCsv.get(indent, "trans_action", "ext1st"),
											sourceState.getName(),							// name
											m_iClass.getName(),								// type
											targetMachineName,								// container
											sourceState.getName(),							// value
											targetMachineName,								// modifier
											iTrans.getDefinition(),							// description
											stmRoot.getStateMachineDiagram().getName()		// scope
										);
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + " and IsIn(" + targetMachineRef + ".currentState, " + stmRoot.getStateMachineDiagram().getName() + "." + sourceState + ")\\");
									try {
										// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
										isInConditions += Utils.get(m_stxCsv.get(indent, "trans_action", "extnxt"),
											sourceState.getName(),							// name
											m_iClass.getName(),								// type
											targetMachineName,								// container
											sourceState.getName(),							// value
											targetMachineName,								// modifier
											iTrans.getDefinition(),							// description
											stmRoot.getStateMachineDiagram().getName()		// scope
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
					if (outgoings.length == 1) {
						System.out.println(makeIndent(indent) + ":");
						try {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
								isInConditions,						// name 
								m_iClass.getName(), 				// type
								"", 								// container
								isInConditions,			 			// value
								collectActions(indent, iTrans.getAction()),// modifier
								"",									// description 
								stmRoot.getStateMachineDiagram().getName()// scope
							));
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						indent++;
						printTransition(stmRoot, rgnName, iVertices, outgoings[0]);
						indent--;
						if (iTrans.getSource() instanceof IPseudostate && ((IPseudostate)iTrans.getSource()).isChoicePseudostate() && iTrans.getGuard().trim().isEmpty()) {
							//   ─◇ ─[empty]→┃▎
							//   └──[else]→
						} else {
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									isInConditions,						// name 
									m_iClass.getName(), 				// type
									"", 								// container
									isInConditions,			 			// value
									collectActions(indent, iTrans.getAction()),// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
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
				}else if (iPstate.isInitialPseudostate()) {
					// throws error
					System.out.println("★★★ERROR★★★");
				}else {
					// throws error
					System.out.println("★★★ERROR★★★");
				}
			}else if (iTgtVtx instanceof IFinalState) {
				try {
					String targetStateName = rgnName;
					IState container = null;
					if (iTgtVtx.getContainer() != null) {
						container = (IState)iTgtVtx.getContainer();
						if (!Arrays.asList(container.getSubvertexes(0)).contains(iTgtVtx)) {	// the pseudo-state belong to a region top
							container = null;
						}
					}
					if (container != null) {
						targetStateName = container.getName();
					}
					
					// if same level shallowHistory
					// set it to Zero
					String containingMachine = findTargetMachineName(rgnName, iVertices, iTgtVtx, null);
					container = (IState)iTgtVtx.getContainer();
					IVertex shallowHistPt = findShallowHistoryPseudostate(containingMachine == null || container == null ? iVertices : container.getSubvertexes(0));
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
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					}						
					TransitionTo(iTrans, targetStateName, stmRoot, rgnName);
				} catch (InvalidUsingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else if(iTgtVtx instanceof IState){ 
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
	private boolean isLeafState(IVertex iVertex) {
		if (iVertex instanceof IState) {
			IState iState = (IState)iVertex;
			try {
				if (!(iState instanceof IFinalState)) {
					if ((iState.getSubvertexes(0).length == 0 || iState.isSubmachineState())) {
						return true;
					}
				}
			} catch (InvalidUsingException e) {
				e.printStackTrace();
			}
		}
		if (iVertex instanceof IPseudostate) {
			IPseudostate iPseudostate = (IPseudostate)iVertex;
			IState container = null;
			if (iPseudostate.getContainer() != null) {
				container = (IState)iPseudostate.getContainer();
			}
			if (iPseudostate.isInitialPseudostate() 
			 || iPseudostate.isEntryPointPseudostate() && container == null
			 || iPseudostate.isExitPointPseudostate() && container == null
			 //|| iPseudostate.isShallowHistoryPseudostate() && iPseudostate.getOutgoings().length == 1
			 //|| iPseudostate.isDeepHistoryPseudostate() && iPseudostate.getOutgoings().length == 1
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
	protected boolean isCompositeState(IVertex iVertex) {
		if (iVertex instanceof IState) {
			IState iState = (IState)iVertex;
			if (!(iState instanceof IFinalState)) {
				if (!isLeafState(iVertex)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public IStateMachine getMainStm() {
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
			m_iMainStm.getStateMachineDiagram().getName()
		));
		
		// print events of sub-machines
		class EventDeepTraverser extends StateDeepTraverser {
			protected void checkState(IState iState, IState container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					IStateMachine iSubMachine = iState.getSubmachine();
					new EventDeepTraverser().start(iSubMachine.getVertexes());
					for (ITransition iTrans: iSubMachine.getTransitions()) {
						if (!iTrans.getEvent().trim().isEmpty()) {
							if (!uniqueSortedEvents.contains(iTrans.getEvent().trim())) {
								uniqueSortedEvents.add(iTrans.getEvent().trim());
							}
						}
					}		
				}
				// collect internal transitions
				for (ITransition iTrans: iState.getInternalTransitions()) {
					if (!iTrans.getEvent().trim().isEmpty()) {
						if (!uniqueSortedEvents.contains(iTrans.getEvent().trim())) {
							uniqueSortedEvents.add(iTrans.getEvent().trim());
						}
					}
				}
			}
		};
		new EventDeepTraverser().start(m_iMainStm.getVertexes());
		// print events of main-machine
		for (ITransition iTrans: m_iMainStm.getTransitions()) {
			if (!iTrans.getEvent().trim().isEmpty()) {
				if (!uniqueSortedEvents.contains(iTrans.getEvent().trim())) {
					uniqueSortedEvents.add(iTrans.getEvent().trim());
				}
			}
		}		
		Collections.sort(uniqueSortedEvents);
		System.out.println(makeIndent(indent) + "class Events(Enum):");
		indent++;
		String path = m_stxCsv.get(indent, "event_decl", "ext1st");
		for (String key: uniqueSortedEvents) {
			System.out.println(makeIndent(indent) + key + " = auto()");
			m_writer.write(Utils.get(path, key, m_iClass.getName(), m_iClass.getName(), "", "", ""));
			path = m_stxCsv.get(indent, "event_decl", "extnxt");
		}
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "end"), 
			m_iClass.getName(), 
			m_iClass.getName(),
			m_iMainStm.getStateMachineDiagram().getName()
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
			m_iMainStm.getStateMachineDiagram().getName(), 
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
			m_iMainStm.getStateMachineDiagram().getName(), 
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
	protected IPseudostate findInitialPseudostate(IVertex[] iVertices) {
		for (IVertex iVtx: iVertices) {
			if (iVtx instanceof IPseudostate) {
				IPseudostate iPseudostate = (IPseudostate)iVtx;
				if (iPseudostate.isInitialPseudostate()) {
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
	protected IPseudostate findShallowHistoryPseudostate(IVertex[] iVertices) {
		for (IVertex iVtx: iVertices) {
			if (iVtx instanceof IPseudostate) {
				IPseudostate iPseudostate = (IPseudostate)iVtx;
				if (iPseudostate.isShallowHistoryPseudostate()) {
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
	protected IPseudostate findDeepHistoryPseudostate(IVertex[] iVertices) {
		for (IVertex iVtx: iVertices) {
			if (iVtx instanceof IPseudostate) {
				IPseudostate iPseudostate = (IPseudostate)iVtx;
				if (iPseudostate.isDeepHistoryPseudostate()) {
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
		// list up state-machines
		for (IStateMachine iStm: m_sortedStmDgrs) {
			m_stmRoot = iStm;
			String rgnName = iStm.getName();
			String rgnDgrName = iStm.getStateMachineDiagram().getName();
			String rgnDefinition = iStm.getDefinition();
			IVertex[] rgnVertices = iStm.getVertexes();
			
			System.out.println(makeIndent(indent) + "# Statemachine sub-class");						
			// print state-machine sub-class
			System.out.println(makeIndent(indent) + "class " + rgnDgrName + "(ParallelStatemachine):");
			
			StringWriter tempWriter = new StringWriter();
			Writer originalWriter = m_writer;  // Save the original FileWriter
			m_writer = tempWriter;					
			
			// list up region state declaration
			new StateDeepTraverser() {
				protected void checkRegion(IState iState, int subRgnIndex, IState container, int rgnIndex) {
					try {
						String rgnName = makeRgnName(iState, subRgnIndex);
						String rgnDgrName = makeRgnName(iState, subRgnIndex) + "Hsm";
						String rgnDefinition = iState.getDefinition();
						IVertex[] rgnVertices = iState.getSubvertexes(subRgnIndex);
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
			}.start(iStm.getVertexes());			
			// list up main state declaration
			printStatesDefinition(		
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
				iStm.getStateMachineDiagram().getName()// scope				
			));					
			indent++;
			
			String subStmAndRgnInitStr = printSubStmAndRgnDecls(		
				rgnName,
				rgnDgrName,
				rgnDefinition,
				rgnVertices
			);
			
			// list up sub-regions
			new StateDeepTraverser() {
				protected void checkRegion(IState iState, int subRgnIndex, IState container, int rgnIndex) {
					try {
						String rgnName = makeRgnName(iState, subRgnIndex);
						String rgnDgrName = makeRgnName(iState, subRgnIndex) + "Hsm";
						String rgnDefinition = iState.getDefinition();
						IVertex[] rgnVertices = iState.getSubvertexes(subRgnIndex);
						System.out.println(makeIndent(indent) + "# Region sub-class");						
						// print state-machine sub-class
						System.out.println(makeIndent(indent) + "class _" + rgnName + "Hsm(Statemachine):");
						indent++;							
						printStmImpl(
							iStm,
							rgnName,
							rgnDgrName,
							rgnDefinition,
							rgnVertices
						);
						indent--;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start(iStm.getVertexes());
			
			System.out.println(makeIndent(indent) + "# Region sub-class");						
			// print state-machine sub-class
			System.out.println(makeIndent(indent) + "class _" + rgnName + "Hsm(Statemachine):");
			indent++;
			
			printStmImpl(
				iStm,
				rgnName,
				rgnDgrName,
				rgnDefinition,
				rgnVertices
			);
			indent--;
			
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
				iStm.getStateMachineDiagram().getName()// scope
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
		IVertex[] rgnVertices
	) throws IOException, Exception {		
		// print states definitions
		System.out.println(makeIndent(indent) + "# States definitions");
		
		StringWriter tempWriter = new StringWriter();
		Writer originalWriter = m_writer;  // Save the original FileWriter
		m_writer = tempWriter;		
		
		new StateDeepTraverserRgn0() {
			private int m_autoId = 0;
			public void printCompositeState(IVertex[] iVertices, String stateName, String containerName, String definition) {
				String subStateNames = null;
				try {
					for (IVertex iVertex: iVertices) {
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
									m_stmRoot.getStateMachineDiagram().getName()
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
									m_stmRoot.getStateMachineDiagram().getName()
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
						m_stmRoot.getStateMachineDiagram().getName()
					));												
					// print regions
					System.out.println(makeIndent(indent) + stateName + " = " + subStateNames);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			protected void checkPseudostate(IPseudostate iPseudostate, IState container) {
				// print initial point, main-machine entry point, sub-machine exit point as simple states
				if (isLeafState(iPseudostate)) {
					System.out.println(makeIndent(indent) + iPseudostate.getName() + " = MakeState(" + m_autoId + ")");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "name"), 
							iPseudostate.getName(), 
							m_iClass.getName(),
							container != null ? container.getName() : rgnName,
							String.format("%2d", m_autoId),
							iPseudostate.getAlias1(),
							iPseudostate.getDefinition(),
							m_stmRoot.getStateMachineDiagram().getName()
						));
					} catch (Exception e) {
						e.printStackTrace();
					}
					m_autoId++;;
				}
			}
			protected void checkState(IState iState, IState container) {
				String containerName = rgnName;
				if (iState.getContainer() != null) {
					containerName = ((INamedElement)iState.getContainer()).getName();
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
							iState.getAlias1(),
							iState.getDefinition(),
							m_stmRoot.getStateMachineDiagram().getName()
						));
					} catch (Exception e) {
						e.printStackTrace();
					}
					m_autoId++;
				} else if (isCompositeState(iState)) {
					try {
						printCompositeState(iState.getSubvertexes(0), iState.getName(), containerName, iState.getDefinition());
					} catch (InvalidUsingException e) {
						e.printStackTrace();
					}
				}
			}
			public void start(IVertex[] iVertices) {
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
			m_stmRoot.getStateMachineDiagram().getName()
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
		IVertex[] rgnVertices
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
				rgnName + "Hsm",						// container 
				"", 										// value
				"",											// modifier
				rgnDefinition,								// definition
				m_stmRoot.getStateMachineDiagram().getName()	// scope
			));
			m_sResult += Utils.get(
				m_stxCsv.get(indent, "region", "ext1st"), 
				rgnName + "Hsm",					// name
				m_iClass.getName(),							// type
				rgnName + "Hsm",						// container 
				"", 										// value
				"",											// modifier
				rgnDefinition,								// definition
				m_stmRoot.getStateMachineDiagram().getName()	// scope
			);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		new StateDeepTraverser() {
			protected void checkPseudostate(IPseudostate iPseudostate, IState container, int rgnIdx) {
				if (iPseudostate.isShallowHistoryPseudostate() || iPseudostate.isDeepHistoryPseudostate()) {
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
							m_stmRoot.getStateMachineDiagram().getName()	// scope
						));
						m_sResult += Utils.get(
							m_stxCsv.get(indent, "history", "ext1st"), 
							iPseudostate.getName(),						// name
							m_iClass.getName(),							// type
							"",											// container 
							"", 										// value
							"",											// modifier
							rgnDefinition,								// definition
							m_stmRoot.getStateMachineDiagram().getName()	// scope
						);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}
			protected void checkRegionBfr(IState iState, int subRgnIdx, IState container, int rgnIndex) {
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
						m_stmRoot.getStateMachineDiagram().getName()	// scope
					));
					m_sResult += Utils.get(
						m_stxCsv.get(indent, "region", "ext1st"), 
						makeRgnName(iState, subRgnIdx) + "Hsm",		// name
						m_iClass.getName(),							// type
						makeRgnName(iState, subRgnIdx) + "Hsm",		// container 
						"", 										// value
						"",											// modifier
						rgnDefinition,								// definition
						m_stmRoot.getStateMachineDiagram().getName()	// scope
					);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
			}
			protected void checkState(IState iState, IState container, int rgnIndex) {
				if (iState.isSubmachineState()) {
					IStateMachineDiagram iSubStmDgr = iState.getSubmachine().getStateMachineDiagram();
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
							m_stmRoot.getStateMachineDiagram().getName()	// scope
						));
						m_sResult += Utils.get(
							m_stxCsv.get(indent, "substm", "ext1st"), 
							iState.getName() + "Hsm",					// name
							m_iClass.getName(),							// type
							iSubStmDgr.getName(),						// container 
							"", 										// value
							targetMachineName,							// modifier
							rgnDefinition,								// definition
							m_stmRoot.getStateMachineDiagram().getName()	// scope
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
	private void printStmAPIs(IStateMachine iStm) {
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
			protected void checkRegion(IState iState, int subRgnIndex, IState container, int rgnIndex) {
				String regionName = makeRgnName(iState, subRgnIndex); 
				System.out.print(makeIndent(indent) + "result = ");
				m_sResult = "";
				try {
					new StateDeepTraverserRgn0() {
						protected void checkState(IState iState, IState container) {
							if (iState.isSubmachineState()) {
								String subStmDgrName = iState.getSubmachine().getStateMachineDiagram().getName();
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
										m_stmRoot.getStateMachineDiagram().getName()	// scope
									);
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
					}.start(iState.getSubvertexes(subRgnIndex));
				} catch (InvalidUsingException e) {
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
						m_stmRoot.getStateMachineDiagram().getName()	// scope
					));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start(iStm.getVertexes());
		
		// print this machine default-transition
		System.out.print(makeIndent(indent) + "result = ");
		m_sResult = "";
		new StateDeepTraverserRgn0() {
			protected void checkState(IState iState, IState container) {
				if (iState.isSubmachineState()) {
					String subStmDgrName = iState.getSubmachine().getStateMachineDiagram().getName();
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
							m_stmRoot.getStateMachineDiagram().getName()	// scope
						);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start(iStm.getVertexes());
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
				m_stmRoot.getStateMachineDiagram().getName()	// scope
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
				m_stmRoot.getStateMachineDiagram().getName()	// scope
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
		IStateMachine stmRoot,
		String rgnName,
		String rgnDgrName,
		String rgnDefinition,
		IVertex[] rgnVertices
	) throws IOException, Exception {
		System.out.println(makeIndent(indent) + "# Region implementation: " + rgnName);

		// print states' entryAction, eventHandle, exitAction
		new StateDeepTraverserRgn0() {
			protected void checkStateBfr(IState _iState, IState container) {				
				if (_iState instanceof IFinalState) {
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
				System.out.println(makeIndent(indent) + "if self.Enterable(" + stmRoot.getStateMachineDiagram().getName() + "." + stateName + "):");
				if (_iState != null) {
					System.out.println(makeIndent(indent) + "    self." + containerName + "_Enter()");
				}
				try {
					String actions = "";
					IVertex[] vertices = rgnVertices;
					if (_iState != null) {
						stateName = _iState.getName();
						vertices = _iState.getSubvertexes(0);
					}
					// if initialPoint exists AND targetState == thisState: curState <- initialPoint
					IVertex initPt = findInitialPseudostate(vertices);
					if (initPt != null) {
						System.out.println(makeIndent(indent) + "    if self.targetState == " + stmRoot.getStateMachineDiagram().getName() + "." + stateName + ":");
						System.out.println(makeIndent(indent) + "        self.pseudoState = " + stmRoot.getStateMachineDiagram().getName() + "." + initPt.getName());
						// ■ action.begin
						actions += Utils.get(m_stxCsv.get(indent, "state_action", "name"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							"",									// value
							initPt.getName(), 					// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
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
							stmRoot.getStateMachineDiagram().getName()// scope
						);
					}
					
					// if container has shallowHistoryPt and : shallowHistory <- thisState
					if (_iState != null) {
						IVertex shallowHistPt = findShallowHistoryPseudostate(container == null ? rgnVertices : container.getSubvertexes(0));
						// ■ history.extnxt
						if (shallowHistPt != null) {
							//String containingMachine = _iState != null ? findTargetMachineName(rgnName, rgnVertices, _iState) : stmRoot.getName();
							System.out.println(makeIndent(indent) + "    self.main." + shallowHistPt.getName() + " = " + stmRoot.getStateMachineDiagram().getName() + "." + _iState.getName());
							actions += Utils.get(m_stxCsv.get(indent, "history", "extnxt"), 
								shallowHistPt.getName(),			// name 
								m_iClass.getName(), 				// type
								"", 								// container
								_iState.getName(),		 			// value
								"",									// modifier
								"",									// description 
								stmRoot.getStateMachineDiagram().getName()// scope
							);
						}
					}
					// initiate regions if available
					int subRgnIndex = 1;
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (_iState.getSubvertexes(subRgnIndex).length > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Initiate()");
								// ■ substm.begin
								actions += Utils.get(m_stxCsv.get(indent, "state_action", "begin"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								);
							}
							subRgnIndex++;
						} catch (InvalidUsingException e) {
							break;
						}
					}
					
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmName = _iState.getSubmachine().getStateMachineDiagram().getName();
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
							stmRoot.getStateMachineDiagram().getName()// scope
						);
					}
					System.out.println(makeIndent(indent) + "    self.DefaultEntryAction('" + stateName + "')");

					// event-processing for regions if available
					subRgnIndex = 1;
					String modifier = "";
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (_iState.getSubvertexes(subRgnIndex).length > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Initiate()");
								// ■ substm.begin
								modifier += Utils.get(m_stxCsv.get(indent, "stm_api", "ext1st"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								);
							}
							subRgnIndex++;
						} catch (InvalidUsingException e) {
							break;
						}
					}
					
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmName = _iState.getSubmachine().getStateMachineDiagram().getName();
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
							stmRoot.getStateMachineDiagram().getName()// scope
						);
					}
					
					if (_iState != null) {						
						Rectangle2D iRect = null;
						for (IPresentation iPresentxn : _iState.getPresentations()) {
							INodePresentation iNode = (INodePresentation)iPresentxn;
							iRect = iNode.getRectangle();
						}
						Rectangle2D iLocalStmRect = stmRoot.getStateMachineDiagram().getBoundRect();
						if (iRect != null && iLocalStmRect != null) {
							rectRatio = "" + Math.round(iRect.getX()) 
									+ "\t" + Math.round(iRect.getY())
									+ "\t" + Math.round(iRect.getWidth())
									+ "\t" + Math.round(iRect.getHeight())
									+ "\t" + Math.round(iLocalStmRect.getX())
									+ "\t" + Math.round(iLocalStmRect.getY())
									+ "\t" + Math.round(iLocalStmRect.getWidth())
									+ "\t" + Math.round(iLocalStmRect.getHeight())
									+ "\t" + _iState.getName();
						}
						
						// Added transition action
						actions += collectActions(indent, _iState.getEntry());
						
						m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "name"), 
							stateName,					// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							actions, 							// value
							modifier,							// modifier
							stmRoot.getStateMachineDiagram().getFullName("/") + "\t" + rectRatio,// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					} else {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_top", "name"), 
							stateName,							// name 
							m_iClass.getName(), 				// type
							containerName, 						// container
							actions,							// value
							modifier,							// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						));
					}
				} catch (InvalidUsingException e) {
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				indent--;
				System.out.println(makeIndent(indent) + "# end def");
				
				// print transitions from states
				System.out.println(makeIndent(indent) + "def " + stateName + "_EventHandle(self, e, params):");
				System.out.println(makeIndent(indent) + "    self.sourceState = " + stmRoot.getStateMachineDiagram().getName() + "." + stateName);
				indent++;
				boolean firstRound = true;
				if (_iState != null) {
					List<String> internalEvents = new ArrayList<>();
					for (ITransition iTrans: _iState.getInternalTransitions()) {
						if (!internalEvents.contains(iTrans.getEvent().trim())) {
							internalEvents.add(iTrans.getEvent().trim());
						}
					}	
					for (ITransition iTrans: _iState.getOutgoings()) {
						if (internalEvents.contains(iTrans.getEvent().trim())) {
							m_bIsInternalTrans = true;
						}
						if (!iTrans.getEvent().trim().isEmpty()) {
							m_originTrans = iTrans;
							if (firstRound) {
								System.out.println(makeIndent(indent) + "if e == Events." + iTrans.getEvent().trim() + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "ext1st"), 
										iTrans.getEvent().trim(),			// name 
										m_iClass.getName(), 				// type
										containerName, 						// container
										"",		 							// value
										"", 								// modifier
										"",									// description 
										stmRoot.getStateMachineDiagram().getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								firstRound = false;
							} else {
								System.out.println(makeIndent(indent) + "elif e == Events." + iTrans.getEvent().trim() + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "extnxt"), 
										iTrans.getEvent().trim(),			// name 
										m_iClass.getName(), 				// type
										containerName, 						// container
										"",		 							// value
										"", 								// modifier
										"",									// description 
										stmRoot.getStateMachineDiagram().getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							indent++;
							if (!iTrans.getGuard().isEmpty()) {
								// print if
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								// print end-if
								System.out.println(makeIndent(indent) + "return True");
								System.out.println(makeIndent(indent) + "# end if");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
										iTrans.getGuard(),					// name 
										m_iClass.getName(), 				// type
										"", 								// container
										iTrans.getGuard(),		 			// value
										collectActions(indent, iTrans.getAction()),// modifier
										"",									// description 
										stmRoot.getStateMachineDiagram().getName()// scope
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
				System.out.println(makeIndent(indent) + "if self.Exitable(" + stmRoot.getStateMachineDiagram().getName() + "."  + stateName + "):");
				try {
					String actions = "";
					// if deepHistoryPt exists, deepHistoryPt <- lastEnteredState
					IVertex subDeepHistPt = findDeepHistoryPseudostate(_iState == null ? rgnVertices : _iState.getSubvertexes(0));
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
							stmRoot.getStateMachineDiagram().getName()// scope
						);
					}					
					// terminate regions if available
					int subRgnIndex = 1;
					while (_iState != null) {
						try {
							String regionName = subRgnIndex == 0 ? stateName : stateName + "Rgn" + subRgnIndex; 
							if (_iState.getSubvertexes(subRgnIndex).length > 0) {
								System.out.println(makeIndent(indent) + "    self.main." + regionName + "Hsm.Terminate()");							
								// ■ substm.end
								actions += Utils.get(m_stxCsv.get(indent, "state_action", "end"), 
									stateName,							// name 
									m_iClass.getName(), 				// type
									regionName + "Hsm",					// container
									"",									// value
									regionName + "Hsm",					// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								);
							}
							subRgnIndex++;
						} catch (InvalidUsingException e) {
							break;
						}
					}
					if (_iState != null && _iState.isSubmachineState()) {
						String subStmDgrName = _iState.getSubmachine().getStateMachineDiagram().getName();
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
							stmRoot.getStateMachineDiagram().getName()// scope
						);
					}
					
					try {

						if (_iState != null) {
							actions += collectActions(indent, _iState.getExit());
							
							m_writer.write(Utils.get(m_stxCsv.get(indent, "transition", "end"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								actions, 							// value
								"", 								// modifier
								stmRoot.getStateMachineDiagram().getFullName("/") + "\t" + rectRatio,// description 
								stmRoot.getStateMachineDiagram().getName()// scope
							));
						} else {
							m_writer.write(Utils.get(m_stxCsv.get(indent, "trans_top", "end"), 
								stateName,					// name 
								m_iClass.getName(), 				// type
								containerName, 						// container
								actions, 							// value
								"", 								// modifier
								"",									// description 
								stmRoot.getStateMachineDiagram().getName()// scope
							));
						}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (InvalidUsingException e) {
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println(makeIndent(indent) + "    self.DefaultExitAction('" + stateName + "')");
				if (_iState != null) {
					System.out.println(makeIndent(indent) + "    self." + containerName + "_Exit()");
				}
				indent--;
				System.out.println(makeIndent(indent) + "# end def");
			}
			public void start(IVertex[] iVertices) {				
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "if self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    self." + rgnName + "_Exit()");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			stmRoot.getStateMachineDiagram().getName()// scope
		));
		new StateDeepTraverserRgn0() {
			protected void checkState(IState iState, IState container) {
				if (!(iState instanceof IFinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    self." + iState.getName() + "_Exit()");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"", 									// value
							"", 									// modifier
							"", 									// description
							stmRoot.getStateMachineDiagram().getName()// scope
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			stmRoot.getStateMachineDiagram().getName()		// scope
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "if self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    return self." + rgnName + "_EventHandle(e, params)");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			stmRoot.getStateMachineDiagram().getName()// scope
		));		
		new StateDeepTraverserRgn0() {
			protected void checkState(IState iState, IState container) {
				if (!(iState instanceof IFinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    return self." + iState.getName() + "_EventHandle(e, params)");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"",		 								// value
							"", 									// modifier
							"", 									// description
							stmRoot.getStateMachineDiagram().getName()// scope
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			stmRoot.getStateMachineDiagram().getName()		// scope
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		indent++;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "ext1st"), 
			rgnName, 								// name
			m_iClass.getName(), 					// type
			rgnName + "Hsm",	 						// container
			"",		 								// value
			"", 									// modifier
			"", 									// description
			stmRoot.getStateMachineDiagram().getName()// scope
		));		
		System.out.println(makeIndent(indent) + "if self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + rgnName + ":");
		System.out.println(makeIndent(indent) + "    self." + rgnName + "_Enter()");
		new StateDeepTraverserRgn0() {
			protected void checkState(IState iState, IState container) {
				if (!(iState instanceof IFinalState)) {
					System.out.println(makeIndent(indent) + "elif self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + iState.getName() + ":");
					System.out.println(makeIndent(indent) + "    self." + iState.getName() + "_Enter()");
					try {
						m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "extnxt"), 
							iState.getName(), 						// name
							m_iClass.getName(), 					// type
							rgnName + "Hsm",	 						// container
							"",		 								// value
							"", 									// modifier
							"", 									// description
							stmRoot.getStateMachineDiagram().getName()// scope
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
			stmRoot.getStateMachineDiagram().getName()		// scope
		));
		System.out.println(makeIndent(indent) + "# end def");
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "end"), 
			rgnName, 										// name
			m_iClass.getName(), 							// type
			rgnName + "Hsm",	 						// container
			"", 											// value
			"", 											// modifier
			"", 											// description
			stmRoot.getStateMachineDiagram().getName()		// scope
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
			stmRoot.getStateMachineDiagram().getName()// scope
		));
		indent++;
		System.out.println(makeIndent(indent) + "self.sourceState = self.currentState");
		System.out.println(makeIndent(indent) + "self.lcaState = 0");
		boolean firstRound = true;
		for (ITransition iTrans: stmRoot.getTransitions()) {
			IVertex iSrcVtx = iTrans.getSource();
			// find all transitions originated from a vertex belong to this region only
			String targetMachineName = findTargetMachineName(rgnName, rgnVertices, iSrcVtx, null);
			if (iTrans.getEvent().trim().isEmpty() && targetMachineName != null && targetMachineName.equals(rgnName + "Hsm")) {
				m_originTrans = iTrans;
				if (iSrcVtx != null) {
					if (iSrcVtx instanceof IPseudostate) {
						IPseudostate iPstate = (IPseudostate)iSrcVtx;
						// check if external transition or local transition
						if (iPstate.isEntryPointPseudostate() && iPstate.getContainer() == null/* || iPstate.isStubState()*/
						 || iPstate.isExitPointPseudostate() && iPstate.getContainer() != null
						 //|| iPstate.isDeepHistoryPseudostate()
						 //|| iPstate.isShallowHistoryPseudostate()
						 //|| iPstate.isChoicePseudostate()
					     //|| iPstate.isJunctionPseudostate()
					     //|| iPstate.isForkPseudostate()
						 //|| iPstate.isJoinPseudostate()
						 || iPstate.isInitialPseudostate()
						) {
							String containerName = rgnName;
							IState container = null;
							if (iSrcVtx.getContainer() != null) {
								container = (IState)iSrcVtx.getContainer();
								if (!Arrays.asList(container.getSubvertexes(0)).contains(iSrcVtx)) {	// the pseudo-state belong to a region top
									container = null;
								}
							}
							if (container != null) {
								containerName = container.getName();
							}
							String pseudoStateName = iPstate.getName();
							if (iPstate.isInitialPseudostate()
							 || iPstate.isEntryPointPseudostate() && iPstate.getContainer() == null
							) {
								String ifCondition = Utils.get(m_stxCsv.get("default_trans", "ext1st"), 
									iSrcVtx.getName(),					// name 
									m_iClass.getName(), 				// type
									stmRoot.getStateMachineDiagram().getName(),// container
									containerName,						// value
									"",									// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								);
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if self.pseudoState == " + stmRoot.getStateMachineDiagram().getName() + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										ifCondition						// name
									));
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + "elif self.pseudoState == " + stmRoot.getStateMachineDiagram().getName() + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "extnxt"), 
										ifCondition						// name
									));
								}
							} else {  // iPstate.isExitPointPseudostate() && iPstate.getContainer() != null
								container = (IState)iSrcVtx.getContainer();
								containerName = container.getName();
								String subMachineDgrName = "";
								if (container.isSubmachineState()) {
									subMachineDgrName = container.getSubmachine().getStateMachineDiagram().getName();
								}
								String ifCondition = Utils.get(m_stxCsv.get("default_trans", "extnxt"), 
									iSrcVtx.getName(),					// name 
									m_iClass.getName(), 				// type
									subMachineDgrName, 					// container
									containerName,						// value
									"",									// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
								);
								if (firstRound) {
									System.out.println(makeIndent(indent) + "if self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + container.getName() + " and self.pseudoState == " + subMachineDgrName + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										ifCondition						// name
									));
									firstRound = false;
								} else {
									System.out.println(makeIndent(indent) + "elif self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + container.getName() + " and self.pseudoState == " + subMachineDgrName + "." + pseudoStateName + ":");
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "extnxt"), 
										ifCondition						// name
									));
								}
							}
							indent++;
							if (!iTrans.getGuard().isEmpty()) {
								// print if
								printTransition(stmRoot, rgnName, rgnVertices, iTrans);
								// print end-if
								System.out.println(makeIndent(indent) + "return True");
								System.out.println(makeIndent(indent) + "# end if");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
										iTrans.getGuard(),					// name 
										m_iClass.getName(), 				// type
										"", 								// container
										iTrans.getGuard(),		 			// value
										collectActions(indent, iTrans.getAction()),// modifier
										"",									// description 
										stmRoot.getStateMachineDiagram().getName()// scope
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
					}else if (iSrcVtx instanceof IState && !isJoinBar(iTrans.getTarget())) {
						IState iState = (IState)iSrcVtx;
						String syntax;
						if (firstRound) {
							System.out.println(makeIndent(indent) + "if self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + iState.getName() + "\\");
							syntax = m_stxCsv.get(indent, "branch", "ext1st");
							firstRound = false;
						} else {
							System.out.println(makeIndent(indent) + "elif self.currentState == " + stmRoot.getStateMachineDiagram().getName() + "." + iState.getName() + "\\");
							syntax = m_stxCsv.get(indent, "branch", "extnxt");
						}
						int subRgnIdx = 1;
						String isCompletedConditions = "";
						while (true) {											// All sub-regions must be completed
							try {
								if (iState.getSubvertexes(subRgnIdx).length > 0) {
									String subRgnName = makeRgnName(iState, subRgnIdx);
									System.out.println(makeIndent(indent) + "    and self.main." + subRgnName + "Hsm.pseudoState == self.main." + subRgnName + "\\");
									isCompletedConditions += Utils.get(m_stxCsv.get(indent, "region", "extnxt"),
										subRgnName,										// name
										m_iClass.getName(),								// type
										subRgnName + "Hsm",								// container
										subRgnName,										// value
										targetMachineName,								// modifier
										iTrans.getDefinition(),							// description
										stmRoot.getStateMachineDiagram().getName()		// scope
									);
								}
							} catch (InvalidUsingException e) {
								break;
							}
							subRgnIdx++;
						}
						// sub-machine, if have, must be completed
						if (iState.isSubmachineState()) {
							String subStmName = iState.getSubmachine().getStateMachineDiagram().getName();
							System.out.println(makeIndent(indent) + "    and self.main." + subStmName + "Hsm.pseudoState == self.main." + subStmName + "\\");
							isCompletedConditions += Utils.get(m_stxCsv.get(indent, "substm", "extnxt"),
								subStmName,										// name
								m_iClass.getName(),								// type
								subStmName,										// container
								subStmName,										// value
								iState.getName() + "Hsm",						// modifier
								iTrans.getDefinition(),							// description
								stmRoot.getStateMachineDiagram().getName()		// scope
							);
						}
						
						System.out.println(makeIndent(indent) + ":");
						String ifCondition = Utils.get(m_stxCsv.get(indent, "default_trans", "extnxt"), 
							iSrcVtx.getName(),					// name 
							m_iClass.getName(), 				// type
							stmRoot.getStateMachineDiagram().getName(),// container
							iSrcVtx.getName(),					// value
							isCompletedConditions,				// modifier
							"",									// description 
							stmRoot.getStateMachineDiagram().getName()// scope
						);
						m_writer.write(Utils.get(syntax, 
							ifCondition							// name 
						));
						indent++;
						if (!iTrans.getGuard().isEmpty()) {
							// print if
							printTransition(stmRoot, rgnName, rgnVertices, iTrans);
							// print end-if
							System.out.println(makeIndent(indent) + "return True");
							System.out.println(makeIndent(indent) + "#endif");
							try {
								m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "end"), 
									iTrans.getGuard(),					// name 
									m_iClass.getName(), 				// type
									"", 								// container
									iTrans.getGuard(),		 			// value
									collectActions(indent, iTrans.getAction()),// modifier
									"",									// description 
									stmRoot.getStateMachineDiagram().getName()// scope
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
					}else if (iSrcVtx instanceof IState && isJoinBar(iTrans.getTarget())) {
						// Check if this join bar belongs to this region
						IPseudostate iPstate = (IPseudostate)iTrans.getTarget();
						ITransition[] outgoings = iPstate.getOutgoings();						
						
						String joinBarMachineName = findTargetMachineName(rgnName, rgnVertices, iPstate, null);
						if (joinBarMachineName != null && joinBarMachineName.equals(rgnName + "Hsm")) {
							System.out.println(makeIndent(indent) + "# begin joining");
							String isInConditions = "";
							for (ITransition incoming: iPstate.getIncomings()) {
								if (incoming != iTrans) {
									if (incoming.getSource() instanceof IState && incoming.getEvent().trim().isEmpty()) {
										IState sourceState = (IState)incoming.getSource();
										StringBuilder containingRgn = new StringBuilder();
										targetMachineName = findTargetMachineName(stmRoot.getName(), stmRoot.getVertexes(), sourceState, containingRgn);
										String targetMachineRef;
										if (targetMachineName.equals(rgnName + "Hsm")) {
											targetMachineRef = "self";
										} else {
											targetMachineRef = "self.main." + targetMachineName;
										}
										if (isInConditions.isEmpty()) {
											System.out.println(makeIndent(indent) + "if IsIn(" + targetMachineRef + ".currentState," + stmRoot.getStateMachineDiagram().getName() + "." + sourceState + ")\\");
											try {
												// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
												isInConditions = Utils.get(m_stxCsv.get(indent, "trans_action", "ext1st"),
													sourceState.getName(),							// name
													m_iClass.getName(),								// type
													targetMachineName,								// container
													sourceState.getName(),							// value
													targetMachineName,								// modifier
													iTrans.getDefinition(),							// description
													stmRoot.getStateMachineDiagram().getName()		// scope
												);
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
											firstRound = false;
										} else {
											System.out.println(makeIndent(indent) + " and IsIn(" + targetMachineRef + ".currentState, " + stmRoot.getStateMachineDiagram().getName() + "." + sourceState + ")\\");
											try {
												// [cONTAINER]_IsIn( &( ( [sCOPE]* )pStm->pMain )->[mODIFIER], [sCOPE]_[vALUE] )
												isInConditions += Utils.get(m_stxCsv.get(indent, "trans_action", "extnxt"),
													sourceState.getName(),							// name
													m_iClass.getName(),								// type
													targetMachineName,								// container
													sourceState.getName(),							// value
													targetMachineName,								// modifier
													iTrans.getDefinition(),							// description
													stmRoot.getStateMachineDiagram().getName()		// scope
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
							if (outgoings.length == 1) {
								System.out.println(makeIndent(indent) + ":");
								try {
									m_writer.write(Utils.get(m_stxCsv.get(indent, "branch", "ext1st"), 
										isInConditions,						// name 
										m_iClass.getName(), 				// type
										"", 								// container
										isInConditions,			 			// value
										collectActions(indent, iTrans.getAction()),// modifier
										"",									// description 
										stmRoot.getStateMachineDiagram().getName()// scope
									));
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								indent++;
								printTransition(stmRoot, rgnName, rgnVertices, outgoings[0]);
								indent--;
							}							
						}
					}else if (iSrcVtx instanceof IFinalState) {
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
			stmRoot.getStateMachineDiagram().getName()// scope
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
			stmRoot.getStateMachineDiagram().getName()// scope
		));		
		
		System.out.println(makeIndent(indent) + "def Initiate(self, entryPoint = 0):");
		System.out.println(makeIndent(indent) + "    if entryPoint != 0:");
		System.out.println(makeIndent(indent) + "        self.pseudoState = entryPoint");
		System.out.println(makeIndent(indent) + "        return");
		System.out.println(makeIndent(indent) + "    if self.pseudoState == 0:");
		System.out.println(makeIndent(indent) + "        self.pseudoState = " + stmRoot.getStateMachineDiagram().getName() + "." + rgnName);
		System.out.println(makeIndent(indent) + "    self.BgnTrans(self.pseudoState)");
		System.out.println(makeIndent(indent) + "    self.EndTrans()");
		System.out.println(makeIndent(indent) + "# end def");

		System.out.println(makeIndent(indent) + "def Terminate(self):");
		System.out.println(makeIndent(indent) + "    self.BgnTrans(0)");
		System.out.println(makeIndent(indent) + "    self.EndTrans()");
		System.out.println(makeIndent(indent) + "# end def");
		
	}
}
