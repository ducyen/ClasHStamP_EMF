package stm;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.change_vision.jude.api.inf.exception.InvalidUsingException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IFinalState;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IOperation;
import com.change_vision.jude.api.inf.model.IPseudostate;
import com.change_vision.jude.api.inf.model.IState;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IStateMachineDiagram;
import com.change_vision.jude.api.inf.model.ITransition;
import com.change_vision.jude.api.inf.model.IVertex;
import com.change_vision.jude.api.inf.presentation.ILinkPresentation;
import com.change_vision.jude.api.inf.presentation.INodePresentation;
import com.change_vision.jude.api.inf.presentation.IPresentation;
import com.change_vision.jude.api.inf.project.ModelFinder;

import stm.TRgnGenerator.Region;

public class TTransGenerator extends TBaseGenerator {
	private static HashMap<String, ITransition> m_eventList = new HashMap<String, ITransition>();
	private static List<String> m_sortedEvents = new ArrayList<String>();
	private ITransition m_originTrans = null;
	private IStateMachine m_iLocalStm = null;
	private boolean m_bFirstRound = false;
	private String m_path;
	private IStateMachine m_iMainStm;
	
	/**
	 * Constructor
	 * @param m_stxCsv
	 * @param iClass
	 * @param m_writer
	 */
	public TTransGenerator(SyntaxCsv m_stxCsv, IClass iClass, Writer m_writer, IStateMachine iLocalStm, IStateMachine iMainStm) {
		super(m_stxCsv, iClass, m_writer);
		// TODO Auto-generated constructor stub
		m_iLocalStm = iLocalStm;
		m_iMainStm = iMainStm;
	}


	/**
	 * clearEventList
	 */
	public static void clearEventLists() {
		m_eventList.clear();
	}
	/**
	 * createEventList
	 * @param iStm
	 * @throws InvalidUsingException 
	 */
	public static void createEventLists(IStateMachine iStm) throws InvalidUsingException {
		// find transitions
		for (ITransition iTrans: iStm.getTransitions()) {
			if (!iTrans.getEvent().isEmpty()) {							
				String eventName = iTrans.getEvent().trim();
				if (!m_eventList.containsKey(eventName)) {
					m_eventList.put(eventName, iTrans);
				}
			}
		}
		// find internal transitions
		class InternalTransTraverser {
			private void checking(IState state) {
				for (ITransition iTrans: state.getInternalTransitions()) {
					if (!iTrans.getEvent().isEmpty()) {							
						String eventName = iTrans.getEvent().trim();
						if (!m_eventList.containsKey(eventName)) {
							m_eventList.put(eventName, iTrans);
						}
					}
				}
			}
			private void traverse(IVertex iVtx) throws InvalidUsingException {
				if (iVtx instanceof IState) {
					checking((IState)iVtx);
					for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes()) {
						traverse(iSubvtx);
					}
				}
			}
			public InternalTransTraverser(IStateMachine iStm) throws InvalidUsingException {
				for (IVertex iVtx: iStm.getVertexes()) {
					traverse(iVtx);
				}
			}
		}
		new InternalTransTraverser(iStm);
		m_sortedEvents = new ArrayList<String>(m_eventList.keySet());
		Collections.sort(m_sortedEvents);
	}
	
	/**
	 * printEventDecl
	 * @throws IOException
	 * @throws Exception
	 */
	public static void printEventDecl(IClass iClass) throws IOException, Exception {
		String evtEnumName = iClass.getName();
		m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "name"), evtEnumName, iClass.getName()));
		//m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "begin"), evtEnumName, iClass.getName()));
		indent++;
		String path = m_stxCsv.get(indent, "event_decl", "ext1st");
		for (String key: m_sortedEvents) {
			m_writer.write(Utils.get(path, key, iClass.getName(), evtEnumName, "", "", ""));
			path = m_stxCsv.get(indent, "event_decl", "extnxt");
		}
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "event_decl", "end"), evtEnumName, iClass.getName()));
	}
	
	/**
	 * isOperationExisted
	 * @param container
	 * @param operName
	 * @return
	 */
	public static boolean isOperationExisted(IClass container, String operName) {
		for (IOperation oper : container.getOperations()) {
			String firstItem = Utils.strConvertToMixedCase(oper.getName()).toLowerCase().trim();
			String secondItem = Utils.strConvertToMixedCase(operName).toLowerCase().trim();
			if (firstItem.equalsIgnoreCase(secondItem) && oper.isPublicVisibility()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * findSharedRoot
	 * @param src
	 * @param tgt
	 * @return
	 */
	private IVertex findSharedRoot(IVertex src, IVertex tgt ){
		// find top shared container of source and target
		List<IVertex> containerList = new ArrayList<IVertex>();
		
		IVertex container = src;
		while (container != null) {
			containerList.add(container);
			container = (IVertex)container.getContainer();			
		}
		
		container = tgt;
		while (container != null && !containerList.contains(container)) {
			container = (IVertex)container.getContainer();			
		}
		
		return container;
	}
	
	/**
	 * isLocalTransition
	 * @param originTrans
	 * @param transition
	 * @return
	 * @throws Exception
	 */
	private boolean isLocalTransition(ITransition originTrans, ITransition transition) throws Exception {
		IVertex src = originTrans.getSource();
		if (src instanceof IPseudostate) {
			if (src.getContainer() != null) {
				src = (IVertex)src.getContainer();
			} else {
				return true;
			}
		}
		
		IVertex tgt = transition.getTarget();
		if (tgt instanceof IPseudostate) {
			if (tgt.getContainer() != null) {
				tgt = (IVertex)tgt.getContainer();
			}
		}
		
		IVertex container = findSharedRoot(src, tgt);
		
		if (container == null) {
			return true;
		}
		
		// get shared container rectangle
		Rectangle2D iRect = null;
		for (IPresentation iPresentxn : container.getPresentations()) {
			INodePresentation iNode = (INodePresentation)iPresentxn;
			iRect = iNode.getRectangle();
		}
		if (iRect == null) {
			return true;
		}
		
		// get points of transition
		// if any of transition's point is out of container's rectangle
		// this transition will be treated as external
		// otherwise
		// treated as internal
		for (IPresentation iPresentxn : transition.getPresentations()) {
			ILinkPresentation iLink = (ILinkPresentation)iPresentxn;
			for (Point2D pt : iLink.getAllPoints()) {
				if (pt.getX() < iRect.getMinX() || pt.getX() > iRect.getMaxX() ||
					pt.getY() < iRect.getMinY() || pt.getY() > iRect.getMaxY())
				{
					return false;
				}
			}
		}

		for (IPresentation iPresentxn : originTrans.getPresentations()) {
			ILinkPresentation iLink = (ILinkPresentation)iPresentxn;
			for (Point2D pt : iLink.getAllPoints()) {
				if (pt.getX() < iRect.getMinX() || pt.getX() > iRect.getMaxX() ||
					pt.getY() < iRect.getMinY() || pt.getY() > iRect.getMaxY())
				{
					return false;
				}
			}
		}
	
		return true;
	}
	
	private String getGuardString(ITransition iThisTrans, int level) throws Exception {
		IVertex targetVertex = iThisTrans.getTarget();
		if (isJoinBar(targetVertex)) {
			// find join incoming, which come from region 1 and subsequence or sub machines
			String ifCondition = "";
			String path = m_stxCsv.get(indent + level, "junction", "begin");
			for (ITransition iTransition: targetVertex.getIncomings()) {
				IVertex joinSource = iTransition.getSource();
				IStateMachine iStm = TStmGenerator.findStmOf(joinSource);
				if (iStm != m_iLocalStm) {
					String subState = joinSource.getName();
					String modifier = iStm.getStateMachineDiagram().getName();

					ifCondition += Utils.get(path, 
						iStm.getName(),
						m_iClass.getName(),
						m_iLocalStm.getStateMachineDiagram().getName(),
						subState,
						modifier,
						"",
						iStm.getStateMachineDiagram().getName()
					);							
					path = m_stxCsv.get(indent + level, "junction", "end");
				}else if (isSubExitPoint(joinSource)) {
					IPseudostate iSubstmExitPt = (IPseudostate)joinSource;
					IState srcState = (IState)iSubstmExitPt.getContainer();
					
					String subState = iSubstmExitPt.getName();
					String modifier = srcState.getSubmachine().getStateMachineDiagram().getName();

					ifCondition += Utils.get(path, 
						srcState.getName(),
						m_iClass.getName(),
						m_iLocalStm.getStateMachineDiagram().getName(),
						subState,
						modifier,
						"",
						srcState.getSubmachine().getStateMachineDiagram().getName()
					);							
					path = m_stxCsv.get(indent + level, "junction", "end");
				}
			}
			return ifCondition;
		}
		return iThisTrans.getGuard();
	}

	/**
	 * traverseBranch
	 * @param iTransition
	 * @param level
	 * @param branchType
	 * @throws Exception
	 */
	private void traverseBranch(ITransition iTransition, int level, String branchType, String accumActions) throws Exception {
		if (!isVertexFound(m_iLocalStm.getVertexes(), iTransition.getTarget())) {
			return;
		}
		
		if (!getGuardString(iTransition, level).isEmpty()) {
			level += 1;
		}		
		int localLevel = level;
		printBranchEnd(iTransition, level, branchType, accumActions);

		if (m_originTrans == iTransition) {				// very first action should be a declaration so that do not queue it for 
			accumActions = "";							// action print after now
		}

		IPseudostate iChoice = null;
		if (iTransition.getTarget() instanceof IPseudostate) {
			IPseudostate iPseudostate = (IPseudostate)iTransition.getTarget();
			if (iPseudostate.isChoicePseudostate()) {
				iChoice = iPseudostate;
			}
		}
		ITransition elseBranch = iTransition;

		boolean firstRound = true;
		String elseActions = "";
		boolean hasGuard = false;
		while (elseBranch != null && iChoice != null) {
			elseBranch = null;
			for (ITransition branch: iChoice.getOutgoings()) {	
				if (getGuardString(branch, level).compareTo("else") == 0) {
					elseBranch = branch;
					iChoice = null;
					if (elseBranch.getTarget() instanceof IPseudostate) {
						IPseudostate iPseudostate = (IPseudostate)elseBranch.getTarget();
						if (iPseudostate.isChoicePseudostate()) {
							iChoice = iPseudostate;
							elseActions = elseBranch.getAction();
						}
					}
				}else{
					if (firstRound) {
						traverseBranch(branch, level, "If", accumActions + elseActions + branch.getAction());
						firstRound = false;
					}else{
						traverseBranch(branch, level, "ElseIf", accumActions + elseActions + branch.getAction());
					}
					if (!getGuardString(branch, level).isEmpty()) {
						hasGuard = true;
					}
				}
			}
		}
		if (firstRound == false && elseBranch != null) {
			if (!hasGuard) {
				printBranchEnd(elseBranch, localLevel + 1, "Next", accumActions + elseActions + elseBranch.getAction());
			} else {
				printBranchEnd(elseBranch, localLevel + 1, "Else", accumActions + elseActions + elseBranch.getAction());
			}
		}
		if (firstRound == false) {
			printBranchEnd(null, localLevel + 1, "EndIf", "");
		} 
		// print for guard only
		if (iTransition.getSource() instanceof IState && !getGuardString(iTransition, level).isEmpty()) {
			printBranchEnd(null, localLevel, "EndIf", "");
		} else if (iTransition.getSource() instanceof IPseudostate) {
			IPseudostate iPseudostate = (IPseudostate)iTransition.getSource();
			if (isJunctionPoint(iPseudostate) && !getGuardString(iTransition, level).isEmpty()) {
				printBranchEnd(null, localLevel, "EndIf", "");
			}
		}
		
	}
	
	/**
	 * printBranch
	 * @param branch
	 * @param level
	 * @param branchType
	 * @throws Exception
	 */
	boolean bActionPrinted = false;
	private void printBranchEnd(ITransition branch, int level, String branchType, String accumActions) throws Exception {
		if (branchType.compareTo("EndIf") == 0) {
			m_writer.write(Utils.get(m_stxCsv.get(indent + level, "branch", "end")));
			return;
		}
		
		if (!getGuardString(branch, level).isEmpty()) {
			if (branchType.compareTo("If") == 0) {
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "branch", "ext1st"), getGuardString(branch, level)));
			} else if (branchType.compareTo("ElseIf") == 0) {
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "branch", "extnxt"), getGuardString(branch, level)));
			} else if (branchType.compareTo("Next") == 0) {
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "branch", "name"), getGuardString(branch, level)));
			} else {
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "branch", "begin")));
			}
		}

		indent++;
		
		// Find source state name
		String sourceStateName = m_iLocalStm.getStateMachineDiagram().getName();
		boolean isInternalTrans = false;
		if (m_originTrans.getSource() instanceof IState) {
			sourceStateName = m_originTrans.getSource().getName();
			List<ITransition> internalTransList = Arrays.asList(((IState)m_originTrans.getSource()).getInternalTransitions());
			if (internalTransList.contains(m_originTrans)) {
				isInternalTrans = true;
			}
		} else {		// In case source state is Initial State, History State
			INamedElement sourceContainer = (INamedElement)m_originTrans.getSource().getContainer(); 
			if (sourceContainer != null) {
				sourceStateName = sourceContainer.getName();
			}
		}
		
		// Mark state transition external
		boolean isLocalTrans = isLocalTransition(m_originTrans, branch);
		if (!isLocalTrans) {
			m_writer.write(Utils.get(m_stxCsv.get(indent + level, "api_call", "ext1st"),
				sourceStateName,
				m_iClass.getName()
			));
		}
		// Collect actions
		String actions = collectActions(level, accumActions);
		String actionsIndented = collectActions(level+1, accumActions);
		
		String targetContainerName = m_iLocalStm.getName();
		INamedElement targetContainer = (INamedElement)branch.getTarget().getContainer(); 
		if (targetContainer != null) {
			targetContainerName = targetContainer.getName();
		}
		// Generate code for each target state
		if (branch.getTarget() instanceof IState) {										// In case of target state is a State
			IState targetState = (IState)branch.getTarget();
			if (isInternalTrans) {
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "end"), targetState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (targetState instanceof IFinalState) {
				if (targetContainer instanceof IState) {
					IVertex histVtx = findHistoryWithoutIncoming((IVertex)targetContainer, targetState, 3);
					if (histVtx != null) {
						accumActions += Utils.get(m_stxCsv.get(0, "history", "extnxt"), 
							targetContainerName, 
							m_iClass.getName(),
							targetContainerName,
							targetContainerName,
							histVtx.getAlias1(),
							histVtx.getDefinition(),
							m_iLocalStm.getStateMachineDiagram().getName()
						);
						actions = collectActions(level, accumActions);
					}
				}
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), targetContainerName, m_iClass.getName(), "true", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (isInitialPoint(m_originTrans.getSource()) && m_originTrans.getSource().getContainer() != null && 
					findHistoryWithoutIncoming((IVertex)m_originTrans.getSource().getContainer(), m_originTrans.getSource(), 3) != null) 
			{
				IVertex srcContainer = (IVertex)m_originTrans.getSource().getContainer();
				IVertex iHistVtx = findHistoryWithoutIncoming(srcContainer, m_originTrans.getSource(), 3);
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "history", "begin"), srcContainer.getName(), m_iClass.getName(), m_iLocalStm.getName(), iHistVtx.getName(), actionsIndented, "", m_iLocalStm.getStateMachineDiagram().getName()));
				IVertex initVtx = findInitialPoint(targetState);
				if (initVtx == null) {
					m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), targetState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent + level, "history", "end") , initVtx.getName(), m_iClass.getName(), targetState.getName(), sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
				}
			} else {
				IVertex initVtx = findInitialPoint(targetState);
				if (initVtx == null) {
					m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), targetState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent + level, "history", "end") , initVtx.getName(), m_iClass.getName(), targetState.getName(), sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
				}
			}
		} else if (branch.getTarget() instanceof IPseudostate) {								// In case of target state is a pseudo-state
			IPseudostate targetState = (IPseudostate)branch.getTarget();
			if (isJunctionPoint(targetState)) {												// if target state is a junction
				//actions += Utils.get(m_stxCsv.get(indent + level, "junction", "name"), targetState.getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), targetState.getName(), "", "", m_iLocalStm.getStateMachineDiagram().getName());
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), targetState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (targetState.isShallowHistoryPseudostate() || targetState.isDeepHistoryPseudostate()) {		// If target state is a history state
				// if targetState is a history state, set historyTrans as In
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "history", "begin"), targetContainerName, m_iClass.getName(), m_iLocalStm.getName(), targetState.getName(), actionsIndented, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (targetState.isChoicePseudostate()) {
				m_writer.write(actions);
			} else if (isSuperExitPoint(targetState)) {
				accumActions += Utils.get(m_stxCsv.get(0, "event_decl", "begin"), 
					m_iMainStm.getStateMachineDiagram().getName(), 
					m_iClass.getName(),
					targetContainerName,
					targetState.getName(),
					m_iLocalStm.getAlias1(),
					m_iLocalStm.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				);				
				actions = collectActions(level, accumActions);
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), m_iLocalStm.getName(), m_iClass.getName(), "true", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (isSubEntryPoint(targetState)) {
				IState tgtState = (IState)branch.getTarget().getContainer();
				accumActions += Utils.get(m_stxCsv.get(0, "substm_decl", "end"), 
					tgtState.getName(),
					m_iClass.getName(),
					m_iLocalStm.getStateMachineDiagram().getName(),
					targetState.getName(),
					tgtState.getSubmachine().getStateMachineDiagram().getName(),
					"",
					tgtState.getSubmachine().getStateMachineDiagram().getName()
				);
				actions = collectActions(level, accumActions);
				m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "begin"), tgtState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
			} else if (isForkBar(targetState)) {
				// find fork outgoing, which directs to sub-machines' entry points
				for (ITransition iTransition: targetState.getOutgoings()) {
					IVertex forkTarget = iTransition.getTarget();
					IStateMachine region = TStmGenerator.findStmOf(forkTarget);
					if (isSubEntryPoint(forkTarget)) {
						IPseudostate iSubstmEntryPt = (IPseudostate)forkTarget;
						IState tgtState = (IState)iSubstmEntryPt.getContainer();

						String subState = iSubstmEntryPt.getName();
						String modifier = tgtState.getSubmachine().getStateMachineDiagram().getName();
					
						accumActions += Utils.get(m_stxCsv.get(0, "substm_decl", "end"), 
							tgtState.getName(),
							m_iClass.getName(),
							m_iLocalStm.getStateMachineDiagram().getName(),
							subState,
							modifier,
							"",
							tgtState.getSubmachine().getStateMachineDiagram().getName()
						);
						actions = collectActions(level, accumActions);
					}else if (region != m_iLocalStm) {
						String subState = forkTarget.getName();
						String modifier = region.getStateMachineDiagram().getName();
						if (forkTarget instanceof IState) {
							IState tgtState = (IState)forkTarget;
							IVertex initVtx = findInitialPoint(tgtState);
							if (initVtx != null) {
								subState = initVtx.getName();
							}
							if (TStmGenerator.IsStmBelongToAnotherStm(region, m_iLocalStm) ) {
								accumActions += Utils.get(m_stxCsv.get(0, "substm_decl", "end"), 
									region.getName(),
									m_iClass.getName(),
									m_iLocalStm.getStateMachineDiagram().getName(),
									subState,
									modifier,
									"",
									region.getStateMachineDiagram().getName()
								);
								actions = collectActions(level, accumActions);
							}else if (m_iLocalStm instanceof Region) {
								IStateMachine parentStm = (IStateMachine)m_iLocalStm.getContainer();
								accumActions += Utils.get(m_stxCsv.get(0, "substm_decl", "end"), 
									region.getName(),
									m_iClass.getName(),
									parentStm.getStateMachineDiagram().getName(),
									subState,
									modifier,
									"",
									region.getStateMachineDiagram().getName()
								);
								actions = collectActions(level, accumActions);
							}else {
								accumActions += Utils.get(m_stxCsv.get(0, "substm_decl", "end"), 
									region.getName(),
									m_iClass.getName(),
									m_iLocalStm.getStateMachineDiagram().getName(),
									subState,
									modifier,
									"",
									region.getStateMachineDiagram().getName()
								);
								actions = collectActions(level, accumActions);
							}
						}else {
							traverseBranch(iTransition, level-1, "If", accumActions + iTransition.getAction());
						}
					}
				}
				// find fork outgoing, which directs to state of this machine
				bActionPrinted = false;
				for (ITransition iTransition: targetState.getOutgoings()) {
					IVertex forkTarget = iTransition.getTarget();
					if (forkTarget instanceof IState && isVertexFound(m_iLocalStm.getVertexes(), forkTarget)) {
						IState tgtState = (IState)forkTarget;
						if (!tgtState.isSubmachineState()) {
							isLocalTrans = isLocalTransition(m_originTrans, iTransition);
							if (!isLocalTrans) {
								m_writer.write(Utils.get(m_stxCsv.get(indent + level, "api_call", "ext1st"),
									sourceStateName,
									m_iClass.getName()
								));
							}
							traverseBranch(iTransition, level-1, "If", accumActions + iTransition.getAction());
							bActionPrinted = true;
						}
					} else if (!isSubEntryPoint(forkTarget)) {
						traverseBranch(iTransition, level-1, "If", accumActions + iTransition.getAction());
					}
				}
				if (!bActionPrinted) {
					//m_writer.write(Utils.get(m_stxCsv.get(indent + level, "action", "end"), targetState.getName(), m_iClass.getName(), "false", sourceStateName, actions, "", m_iLocalStm.getStateMachineDiagram().getName()));
					m_writer.write(actions);
				}
			} else if (targetState.isJoinPseudostate()) {
				// find join outgoing, which might be the event transition
				for (ITransition iTransition: targetState.getOutgoings()) {
					traverseBranch(iTransition, level-1, "If", accumActions + iTransition.getAction());
				}				
			} else {
				throw new Exception(String.format("There is an unsupported transition directed to %s", targetState.getName()));
			}
		}
		indent--;
	}
	
	/**
	 * findEventParamsClass
	 * @param container
	 * @param event
	 * @return
	 * @throws Exception
	 */
	private IClass findEventParamsClass(IClass container, String event) throws Exception {
		IClass result = null;
		final String eventParams = (Utils.strConvertToMixedCase(event) + "Params");
		INamedElement[] pkg = TMain.prjAccessor.findElements(
			new ModelFinder() {					
				@Override
				public boolean isTarget(INamedElement arg0) {
					// TODO Auto-generated method stub
					if (arg0 instanceof IClass) {
						IClass iClass = (IClass)arg0;
						return Utils.strConvertToMixedCase(iClass.getName()).equalsIgnoreCase(eventParams);
					}
					return false;						
				}
			}
		);
		if (pkg.length > 0 && pkg[0] instanceof IClass) {
			result = (IClass)pkg[0];
		}
		return result;

	}
	
	/**
	 * printTransition
	 * @param transition
	 * @throws Exception
	 */
	public void printTransition(ITransition transition) throws Exception {
		String eventName = transition.getEvent().trim();
		m_originTrans = transition;
		IClass eventParams = findEventParamsClass(m_iClass, eventName);
		
		if (m_bFirstRound) {
			// forward event to sub-machines
			m_writer.write(Utils.get(m_stxCsv.get(indent, m_path, "ext1st"), eventName, m_iClass.getName(), m_originTrans.getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
			m_bFirstRound = false;
		} else if (getGuardString(transition, 0).equalsIgnoreCase("else") == false) {			// case of internal transition and outgoing transition have the same name 
			m_writer.write(Utils.get(m_stxCsv.get(indent, m_path, "extnxt"), eventName, m_iClass.getName(), m_originTrans.getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
					
		// Declare parameters
		if (eventParams != null && getGuardString(transition, 0).equalsIgnoreCase("else") == false) {
			String containerName = "";
			if (eventParams.getOwner() instanceof INamedElement) {
				containerName = ((INamedElement)eventParams.getOwner()).getName();
			}
			m_writer.write(Utils.get(m_stxCsv.get(indent+1, "action", "name"), eventName, m_iClass.getName(), containerName));
		}

		// Enumerate branches or guard of the transition
		if (getGuardString(transition, 0).equalsIgnoreCase("else")) {
			traverseBranch(transition, 0, "Next", transition.getAction());	// Enumerate branches or guard of the transition
		} else {
			traverseBranch(transition, 0, "If", transition.getAction() );	// Enumerate branches or guard of the transition
		}
	
	}
	
	/**
	 * printInitialTransition
	 * @param transition
	 * @throws Exception
	 */
	public void printDefaultTransition(ITransition transition, boolean bFirstRound) throws Exception {
		IVertex sourceState = transition.getSource();
		String eventName = sourceState.getName();
		m_originTrans = transition;
		String containerName = m_iLocalStm.getName();
		String stateContainer = m_iLocalStm.getStateMachineDiagram().getName();
		if (sourceState.getContainer() != null && isVertexFound(m_iLocalStm.getVertexes(), (IVertex)sourceState.getContainer())) {
			containerName = ((INamedElement)sourceState.getContainer()).getName();
		}
		String pathRow =  "default_trans";
		if (isSubExitPoint(sourceState)) {
			IState sourceContainer = (IState)sourceState.getContainer();
			if (sourceContainer != null && sourceContainer.getSubmachine() != null) {
				stateContainer = sourceContainer.getSubmachine().getStateMachineDiagram().getName();				
			}
		}else if (isJunctionPoint(sourceState)) {
			pathRow = "junction";
		}else if (isInitialPoint(sourceState)) {
			pathRow = "junction";
		}else {
			pathRow = "junction";
		}
		IClass eventParams = findEventParamsClass(m_iClass, eventName);
		if (bFirstRound) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, pathRow, "ext1st"), eventName, m_iClass.getName(), stateContainer, "", containerName, "", m_iLocalStm.getStateMachineDiagram().getName()));
		} else if (getGuardString(transition, 0).equalsIgnoreCase("else") == false) {			// case of internal transition and outgoing transition have the same name 
			m_writer.write(Utils.get(m_stxCsv.get(indent, pathRow, "extnxt"), eventName, m_iClass.getName(), stateContainer, "", containerName, "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
		
		indent++;
		// Check if orthogonal-state finished (substm_impl.ext1st)
		if (sourceState instanceof IState) {
			IState srcState = (IState)sourceState;
			for (int nRegion = 1; nRegion < srcState.getRegionSize(); nRegion++) { // for each region except the first region
				for (IVertex iVtx: srcState.getSubvertexes(nRegion)) {
					if (iVtx instanceof IState) {
						IState subMachine = (IState)iVtx;
						m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_impl", "ext1st"), 
							subMachine.getName(), 
							subMachine.getSubmachine().getStateMachineDiagram().getName(),
							m_iClass.getName(),
							subMachine.getName(),
							subMachine.getAlias1(),
							subMachine.getDefinition(),
							m_iLocalStm.getStateMachineDiagram().getName()
						));
					}
				}
			}
			if (srcState.isSubmachineState()) {
				IState subMachine = srcState;
				m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_impl", "ext1st"), 
					subMachine.getName(), 
					subMachine.getSubmachine().getStateMachineDiagram().getName(),
					m_iClass.getName(),
					subMachine.getName(),
					subMachine.getAlias1(),
					subMachine.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}
		}
		indent--;
		
		// Declare parameters
		if (eventParams != null && getGuardString(transition, 0).equalsIgnoreCase("else") == false) {
			containerName = "";
			if (eventParams.getOwner() instanceof INamedElement) {
				containerName = ((INamedElement)eventParams.getOwner()).getName();
			}
			m_writer.write(Utils.get(m_stxCsv.get(indent+1, "action", "name"), eventName, m_iClass.getName(), containerName));
		}

		// Enumerate branches or guard of the transition
		if (getGuardString(transition, 0).equalsIgnoreCase("else")) {
			traverseBranch(transition, 0, "Next", transition.getAction());	// Enumerate branches or guard of the transition
		} else {
			traverseBranch(transition, 0, "If", transition.getAction() );	// Enumerate branches or guard of the transition
		}
	}		

	/**
	 * printEventProc
	 * @param iVertex
	 * @throws IOException
	 * @throws Exception
	 */
	public void printEventProc(IVertex iVertex) throws IOException, Exception {
		ITransition[] transitions = iVertex.getOutgoings();
		
		List<ITransition> sortedTransitions = Arrays.asList(transitions);

		Collections.sort(sortedTransitions,
			new Comparator<ITransition>() {
				public int compare(ITransition lhs, ITransition rhs) {
					if (lhs.getEvent().equalsIgnoreCase(rhs.getEvent())) {
						try {
							if (getGuardString(lhs, 0).equalsIgnoreCase("else")) {
								return 1;
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return -1;
					}
					return lhs.getEvent().compareToIgnoreCase(rhs.getEvent());
				}
			}
		);

		INamedElement container = (INamedElement)iVertex.getContainer();
		m_path = "trans_top";
		if (container != null && isVertexFound(m_iLocalStm.getVertexes(), (IVertex)container)) {
			m_path = "transition";
		}
		
		m_bFirstRound = true;
		indent++;
		for	(ITransition transition: sortedTransitions) {
			if (transition.getEvent().isEmpty() == false) {
				printTransition(transition);
			}
		}
		
        if (!m_bFirstRound) {
            m_writer.write(
                Utils.get(
                    m_stxCsv.get(indent, m_path, "begin"), 
                    "",
                    m_iClass.getName(),
                    "",
                    "",
                    "",
                    "",
                    m_iLocalStm.getStateMachineDiagram().getName()
                )
            );
        }

        indent--;
	}
}
