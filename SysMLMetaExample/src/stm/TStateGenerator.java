package stm;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.change_vision.jude.api.inf.exception.InvalidUsingException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IDiagram;
import com.change_vision.jude.api.inf.model.IElement;
import com.change_vision.jude.api.inf.model.IFinalState;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IPseudostate;
import com.change_vision.jude.api.inf.model.IState;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IStateMachineDiagram;
import com.change_vision.jude.api.inf.model.ITransition;
import com.change_vision.jude.api.inf.model.IVertex;
import com.change_vision.jude.api.inf.presentation.INodePresentation;
import com.change_vision.jude.api.inf.presentation.IPresentation;

import stm.TRgnGenerator.Region;
import stm.TRgnGenerator.RegionDiagram;

public class TStateGenerator extends TBaseGenerator {
	private static IStateMachine m_iMainStm = null;
	private IStateMachine m_iLocalStm = null;
	private TTransGenerator m_transGen;
	
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 * @param iStm
	 */
	public TStateGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer, IStateMachine iStm) {
		super(stxCsv, iClass, writer);
		m_iLocalStm = iStm;
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * getLocalStm
	 * @return
	 */
	public IStateMachine getLocalStm() {
		return m_iLocalStm;
	}
	
	private static int m_nLeafStateCnt = 0;
	public static void ResetLeafStateCnt() {
		m_nLeafStateCnt = 0;
	}
	
	/**
	 * LeafStateTraverser
	 * @author Duc
	 *
	 */
	private class LeafStateTraverser {
		private List<String> traversedList = new ArrayList<String>();
		private int m_nPseudostateCnt = 0;
		private void traverse(IVertex iVtx) throws IOException, Exception {
			if (isSimpleState(iVtx) || isSuperEntryPoint(iVtx) || 
				isHistory(iVtx) || isInitialPoint(iVtx) || 
				isJunctionPoint(iVtx) || isSuperExitPoint(iVtx)
			) {
				String containerName = m_iLocalStm.getName();
				if (iVtx.getContainer() != null) {
					containerName = ((INamedElement)iVtx.getContainer()).getName();
				}
				if (false) {/* pseudo-state only */
					int value = m_nPseudostateCnt;
					m_nPseudostateCnt++;
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "begin"), 
						iVtx.getName(), 
						m_iClass.getName(),
						containerName,
						String.format("%2d", value),
						iVtx.getAlias1(),
						iVtx.getDefinition(),
						m_iLocalStm.getStateMachineDiagram().getName()
					));
				} else {	/* normal-leaf state */
					int value = m_nLeafStateCnt;
					m_nLeafStateCnt++;
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "name"), 
						iVtx.getName(), 
						m_iClass.getName(),
						containerName,
						String.format("%2d", value),
						iVtx.getAlias1(),
						iVtx.getDefinition(),
						m_iLocalStm.getStateMachineDiagram().getName()
					));
				}
			}
			if (iVtx instanceof IState) {
				for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
					if (!traversedList.contains(iSubvtx.getName())) {
						traverse(iSubvtx);
						traversedList.add(iSubvtx.getName());
					}
				}
				for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
					if (!traversedList.contains(iSubvtx.getName()) && isSuperExitPoint(iSubvtx)) {
						traverse(iSubvtx);
						traversedList.add(iSubvtx.getName());
					}
				}
			}
		}
		public LeafStateTraverser(IStateMachine iStm) throws IOException, Exception {
			for (IVertex iVtx: iStm.getVertexes()) {
				if (!traversedList.contains(iVtx.getName())) {
					traverse(iVtx);
					traversedList.add(iVtx.getName());
				}
			}
		}
	}
	
	/**
	 * CompositeStateTraverser
	 * @author Duc
	 *
	 */
	private class CompositeStateTraverser {
		private void traverse(IVertex iVtx) throws IOException, Exception {
			if (iVtx instanceof IState) {
				for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
					traverse(iSubvtx);
				}
			}
			if (isCompositeState(iVtx)) {
				IState iState = (IState)iVtx;
				String containerName = m_iLocalStm.getName();
				if (iVtx.getContainer() != null) {
					containerName = ((INamedElement)iVtx.getContainer()).getName();
				}
				String path = m_stxCsv.get(indent, "state_decl", "ext1st");
				int value = m_nLeafStateCnt;
				//m_nLeafStateCnt++;
				for (IVertex iSubvtx: iState.getSubvertexes(0)) {
					if (isSimpleState(iSubvtx) || isCompositeState(iSubvtx) || isJunctionPoint(iSubvtx) || isInitialPoint(iSubvtx) || isSuperEntryPoint(iSubvtx)) {
						m_writer.write(Utils.get(path, 
							iState.getName(), 
							m_iClass.getName(), 
							containerName, 
							iSubvtx.getName(), 
							String.format("%2d", value), 
							iState.getDefinition(),
							m_iLocalStm.getStateMachineDiagram().getName()
						));
						path = m_stxCsv.get(indent, "state_decl", "extnxt");
					}
				}
				m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "end"), 
					iState.getName(), 
					m_iClass.getName(), 
					containerName, 
					"", 
					iState.getAlias1(), 
					iState.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));					
			}
		}
		public CompositeStateTraverser(IStateMachine iStm) throws IOException, Exception {
			for (IVertex iVtx: iStm.getVertexes()) {
				traverse(iVtx);
			}
		}
	}
	
	/**
	 * printStateDecl
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStateDeclarations() throws IOException, Exception {
		// print top state
		String path = m_stxCsv.get(indent, "state_decl", "ext1st");
		int value = m_nLeafStateCnt;
		m_nLeafStateCnt++;	
		for (IVertex iSubvtx: m_iLocalStm.getVertexes()) {
			if (isSimpleState(iSubvtx) || isCompositeState(iSubvtx) || isJunctionPoint(iSubvtx) || isInitialPoint(iSubvtx) || isSuperEntryPoint(iSubvtx)) {
				m_writer.write(Utils.get(path, 
					m_iLocalStm.getName(), 
					m_iClass.getName(), 
					"", 
					iSubvtx.getName(), 
					String.format("%2d", value), 
					m_iLocalStm.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
				path = m_stxCsv.get(indent, "state_decl", "extnxt");
			}
		}
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_decl", "end"), 
			m_iLocalStm.getName(), 
			m_iClass.getName(), 
			"", 
			"", 
			m_iLocalStm.getAlias1(), 
			m_iLocalStm.getDefinition(),
			m_iLocalStm.getStateMachineDiagram().getName()
		));					
		
		// print leaf states' declaration
		new LeafStateTraverser(m_iLocalStm);		
	
		// print exit-point state
		//new SubmstExitPtTraverser(m_iLocalStm);
		
		// print composite states
		new CompositeStateTraverser(m_iLocalStm);
	}

	/**
	 * AllStateTraverser
	 * @author 3140327
	 *
	 */
	private class AllStateTraverser {
		private void checking(IState state) throws Exception {
			printStateEntryProc(state);
			m_transGen.printEventProc(state);
			printStateExitProc(state);
		}
		private void traverse(IVertex iVtx) throws IOException, Exception {
			if (iVtx instanceof IState && !(iVtx instanceof IFinalState)) {
				checking((IState)iVtx);
				for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
					traverse(iSubvtx);
				}
			}
		}
		public AllStateTraverser(IStateMachine iStm) throws IOException, Exception {
			for (IVertex iVtx: iStm.getVertexes()) {
				traverse(iVtx);
			}
		}
	}
	
	/**
	 * printStateImplementations
	 * @param transGen
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStateImplementations(TTransGenerator transGen) throws IOException, Exception {
		m_transGen = transGen;
		new AllStateTraverser(m_iLocalStm);
	}
	
	/**
	 * saveShallowHistoryState
	 * @param state
	 * @return
	 * @throws Exception
	 */
	private String saveShallowHistoryState(IState state) throws Exception {
		IElement containerElem = state.getContainer();
		if (containerElem != null) {
			IState container = (IState)containerElem;
			IVertex iVtx = findHistoryWithoutIncoming(container, state, 1);
			if (iVtx != null) {
				String action = Utils.get(m_stxCsv.get(indent, "history", "extnxt"), 
					container.getName(), 
					m_iClass.getName(),
					container.getName(),
					state.getName(),
					state.getAlias1(),
					state.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				);
				return action;
			}
		}
		return "";
	}
	
	/**
	 * saveDeepHistoryStates
	 * @param state
	 * @return
	 * @throws Exception
	 */
	private String saveDeepHistoryStates(IState state, IState subState) throws Exception {
		String row = "history";
		IStateMachine stm = TStmGenerator.findStmOf(state);
		IStateMachine subStm = TStmGenerator.findStmOf(subState);
		if (stm != subStm) {
			row = "deep_hist";
		}
		IElement containerElem = state.getContainer();
		if (containerElem != null && containerElem instanceof IState) {
			IState container = (IState)containerElem;
			IVertex iVtx = findHistoryWithoutIncoming(container, state, 2);
			if (iVtx != null) {
				String action = Utils.get(m_stxCsv.get(indent, row, "extnxt"), 
					container.getName(), 							// name
					m_iClass.getName(),								// type
					subStm.getName(),								// class
					subState.getName(),								// value
					stm.getStateMachineDiagram().getName(),			// modifier
					subState.getDefinition(), 						// description
					m_iLocalStm.getStateMachineDiagram().getName()	// scope
				);
				return action + saveDeepHistoryStates(container, subState);
			}
			return saveDeepHistoryStates(container, subState);
		}
		return "";
	}

	/**
	 * loadDeepHistoryStatesss
	 * @param state
	 * @return
	 * @throws Exception
	 */
	private String loadDeepHistoryStates(IState state, IState subState, IStateMachine region) throws Exception {
		IStateMachine stm = TStmGenerator.findStmOf(state);
		IElement containerElem = state.getContainer();
		if (containerElem != null && containerElem instanceof IState) {
			IState container = (IState)containerElem;
			IVertex iVtx = findHistoryWithoutIncoming(container, state, 2);
			if (iVtx != null) {
				/*
				String action = Utils.get(m_stxCsv.get(indent, "deep_hist", "begin"), 
					container.getName(), 							// name
					m_iClass.getName(),								// type
					((INamedElement)iVtx.getContainer()).getName(),	// class
					subState.getName(),								// value
					stm.getStateMachineDiagram().getName(),			// modifier
					subState.getDefinition(), 						// description
					m_iLocalStm.getStateMachineDiagram().getName()	// scope
				);
				*/
				String action = Utils.get(m_stxCsv.get(indent, "deep_hist", "begin"), 
					container.getName(), 								// name
					region.getStateMachineDiagram().getName(),		// type
					m_iClass.getName(),								// class
					region.getName(),								// value
					stm.getStateMachineDiagram().getName(),			// modifier
					region.getDefinition(),							// description
					m_iLocalStm.getStateMachineDiagram().getName()	// scope
				);
				
				return action + loadDeepHistoryStates(container, subState, region);
			}
			return loadDeepHistoryStates(container, subState, region);
		}
		return "";
	}
	
	
	/**
	 * printStateEntryProc
	 * @param state
	 * @throws Exception
	 */
	String actions = "";
	private void printStateEntryProc(IState state) throws Exception {
		String containerName = m_iLocalStm.getName();
		INamedElement container = (INamedElement)state.getContainer();
		String m_path = "trans_top";
		if (container != null && isVertexFound(m_iLocalStm.getVertexes(), (IVertex)container)) {
			containerName = container.getName();
			m_path = "transition";
		}
		// Collect actions
		indent+=2;
		actions = collectActions(0, state.getEntry());

		// Reset sub-machines
		if (state.isSubmachineState()) {
			IState subMachine = state; 
			actions += Utils.get(m_stxCsv.get(indent, "substm_impl", "begin"), 
				subMachine.getName(), 
				subMachine.getSubmachine().getStateMachineDiagram().getName(),
				m_iClass.getName(),
				subMachine.getName(),
				subMachine.getAlias1(),
				subMachine.getDefinition(),
				m_iLocalStm.getStateMachineDiagram().getName()
			);
		}
		
        // for each orthogonal-state in this state
		for (int nRegion = 1; nRegion < state.getRegionSize(); nRegion++) { // for each region except the first region
			IStateMachine region = TStmGenerator.findRegion(state, nRegion);
			if (region != null) {
				String loadDeepHistAction = loadDeepHistoryStates(state, state, region);
				if (loadDeepHistAction.trim().equals("")) {
					actions += Utils.get(m_stxCsv.get(indent, "substm_impl", "begin"), 
						region.getName(), 
						region.getStateMachineDiagram().getName(),
						m_iClass.getName(),
						region.getName(),
						region.getAlias1(),
						region.getDefinition(),
						m_iLocalStm.getStateMachineDiagram().getName()
					);
				}else {
					// Find deep history states in parents
					actions += loadDeepHistAction;
				}
				
			}
		}		
		// Set history state
		actions += saveShallowHistoryState(state);
		
		// Find deep history states in parents
		actions += saveDeepHistoryStates(state, state);

		indent-=1;
		

		indent-=1;
		// Print Entry implementation
		Rectangle2D iRect = null;
		for (IPresentation iPresentxn : state.getPresentations()) {
			INodePresentation iNode = (INodePresentation)iPresentxn;
			iRect = iNode.getRectangle();
		}
		Rectangle2D iLocalStmRect = m_iLocalStm.getStateMachineDiagram().getBoundRect();
		String rectRatio = "";
		if (iRect != null && iLocalStmRect != null) {
			rectRatio = "" + Math.round(iRect.getX()) 
					+ "\t" + Math.round(iRect.getY())
					+ "\t" + Math.round(iRect.getWidth())
					+ "\t" + Math.round(iRect.getHeight())
					+ "\t" + Math.round(iLocalStmRect.getX())
					+ "\t" + Math.round(iLocalStmRect.getY())
					+ "\t" + Math.round(iLocalStmRect.getWidth())
					+ "\t" + Math.round(iLocalStmRect.getHeight());
		}
		
		m_writer.write(Utils.get(m_stxCsv.get(indent, m_path, "name"), 
			state.getName(), 
			m_iClass.getName(), 
			containerName, 
			actions, 
			"", 
			m_iLocalStm.getStateMachineDiagram().getFullName("/") + "\t" + rectRatio, 
			m_iLocalStm.getStateMachineDiagram().getName()
		));

	}
	
	/**
	 * printStateExitProc
	 * @param state
	 * @throws IOException
	 * @throws Exception
	 */
	private void printStateExitProc(IState state) throws IOException, Exception {
		String containerName = m_iLocalStm.getName();
		INamedElement container = (INamedElement)state.getContainer();
		String m_path = "trans_top";
		if (container != null && isVertexFound(m_iLocalStm.getVertexes(), (IVertex)container)) {
			containerName = container.getName();
			m_path = "transition";
		}
		// Print ExitPoint implementation
		indent+=2;
		String actions = "";

		// Reset sub-machines
		if (state.isSubmachineState()) {
			IState subMachine = state; 
			actions += Utils.get(m_stxCsv.get(indent, "substm_impl", "end"), 
				subMachine.getName(), 
				subMachine.getSubmachine().getStateMachineDiagram().getName(),
				m_iClass.getName(),
				subMachine.getName(),
				subMachine.getAlias1(),
				subMachine.getDefinition(),
				m_iLocalStm.getStateMachineDiagram().getName()
			);
		}
		
		// for each orthogonal-state in this state
		for (int nRegion = 1; nRegion < state.getRegionSize(); nRegion++) { // for each region except the first region
			IStateMachine region = TStmGenerator.findRegion(state, nRegion);
			actions += Utils.get(m_stxCsv.get(indent, "substm_impl", "end"), 
				region.getName(), 
				region.getStateMachineDiagram().getName(),
				m_iClass.getName(),
				region.getName(),
				region.getAlias1(),
				region.getDefinition(),
				m_iLocalStm.getStateMachineDiagram().getName()
			);
		}			

		actions += collectActions(0, state.getExit());

		indent-=2;

		// Print Exit implementation
		Rectangle2D iRect = null;
		for (IPresentation iPresentxn : state.getPresentations()) {
			INodePresentation iNode = (INodePresentation)iPresentxn;
			iRect = iNode.getRectangle();
		}
		Rectangle2D iLocalStmRect = m_iLocalStm.getStateMachineDiagram().getBoundRect();
		
		String rectRatio = "";
		if (iRect != null && iLocalStmRect != null) {
			rectRatio = "" + Math.round(iRect.getX()) 
					+ "\t" + Math.round(iRect.getY())
					+ "\t" + Math.round(iRect.getWidth())
					+ "\t" + Math.round(iRect.getHeight())
					+ "\t" + Math.round(iLocalStmRect.getX())
					+ "\t" + Math.round(iLocalStmRect.getY())
					+ "\t" + Math.round(iLocalStmRect.getWidth())
					+ "\t" + Math.round(iLocalStmRect.getHeight());
		}
		
		m_writer.write(Utils.get(m_stxCsv.get(indent, m_path, "end"), 
			state.getName(), 
			m_iClass.getName(), 
			containerName, 
			actions, 
			"", 
			m_iLocalStm.getStateMachineDiagram().getFullName("/") + "\t" + rectRatio, 
			m_iLocalStm.getStateMachineDiagram().getName()
		));
	}

	private String m_path = "trans_top";
	private boolean m_bFirstRound = true;
	/**
	 * printTopEventProc
	 * @throws IOException
	 * @throws Exception
	 */
	public void printTopEventProc() throws IOException, Exception {
		String initPtName = m_iLocalStm.getStateMachineDiagram().getName();
		for (IVertex vertex: m_iLocalStm.getVertexes()) {
			if (isInitialPoint(vertex)) {
				initPtName = vertex.getName();
				break;
			}
		}
		indent++;
		String resetCode = generateSubmachineResetCode();
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "name"), 
			m_iLocalStm.getName(), 
			m_iClass.getName(), 
			m_iLocalStm.getStateMachineDiagram().getName(), 
			resetCode, 
			initPtName, 
			"", 
			m_iLocalStm.getStateMachineDiagram().getName()
		));
		// Forward event to sub-machine if necessary
		m_path = "trans_top";
		m_bFirstRound = true;
		
		forwardEventToSubmachines();
		
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
		// Print State-To-EventProc map
		m_bFirstRound = true;
		class StateTraverser {
			private void checking(IState state) throws Exception {
				if (m_bFirstRound) {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "ext1st"), state.getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
					m_bFirstRound = false;
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "extnxt"), state.getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
				}
			}
			private void traverse(IVertex iVtx) throws IOException, Exception {
				if (iVtx instanceof IState && !(iVtx instanceof IFinalState)) {
					checking((IState)iVtx);
					for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
						traverse(iSubvtx);
					}
				}
			}
			public StateTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					traverse(iVtx);
				}
			}
		}
		new StateTraverser(m_iLocalStm);
		if (!m_bFirstRound) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "begin"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
		/*
		for (IState state: m_stmInfo.m_externMachineList.values()) {
			INamedElement owner = (INamedElement)state.getSubmachine().getStateMachineDiagram().getContainer();
			m_writer.write(Utils.get(m_stxCsv.get(indent, "ortho_state", "extnxt"), 
				state.getName(), 
				owner != null ? owner.getName() : "",
				m_iClass.getName(),
				"false",
				state.getAlias1(),
				state.getDefinition()
			));
		}
		*/						
		indent--;
		
		String isInCode = generateSubmachineIsInCode();// Tai sinh ma cho submachine reset
		
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_impl", "end"), m_iLocalStm.getName(), m_iClass.getName(), "", isInCode, "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		
	}

	/**
	 * printStateToEntryExitMap
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStateToEntryExitMap() throws IOException, Exception {
		// Print State-To-Entry map
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "name"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		indent++;
		m_bFirstRound = true;
		class StateEntryTraverser {
			private void checking(IVertex state) throws Exception {
				String containerName = m_iLocalStm.getName();
				if (isSimpleState(state) || isCompositeState(state)) {
					containerName = state.getName();
				} else if (state.getContainer() != null) {
					containerName = ((INamedElement)state.getContainer()).getName();
				}
				String myName = state.getName();
				
				if (m_bFirstRound) {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "ext1st"), myName, m_iClass.getName(), containerName, "Entry", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
					m_bFirstRound = false;
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "extnxt"), myName, m_iClass.getName(), containerName, "Entry", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
				}
			}
			private void traverse(IVertex iVtx) throws IOException, Exception {
				if (isSimpleState(iVtx) || isCompositeState(iVtx)) {
					checking(iVtx);
				}
				if (iVtx instanceof IState) {
					for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
						traverse(iSubvtx);
					}
				}
			}
			public StateEntryTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					traverse(iVtx);
				}
			}
		}
		new StateEntryTraverser(m_iLocalStm);

		if (!m_bFirstRound) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "begin"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "Entry", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_entry", "end"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "Entry", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		
		// Print State-To-Exit map
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "name"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "Exit", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		indent++;
		m_bFirstRound = true;

		class StateExitTraverser {
			private void checking(IVertex state) throws Exception {
				String containerName = m_iLocalStm.getName();
				if (isSimpleState(state) || isCompositeState(state)) {
					containerName = state.getName();
				} else if (state.getContainer() != null) {
					containerName = ((INamedElement)state.getContainer()).getName();
				}
				String myName = state.getName();
				if (m_bFirstRound) {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "ext1st"), myName, m_iClass.getName(), containerName, "Exit", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
					m_bFirstRound = false;
				} else {
					m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "extnxt"), myName, m_iClass.getName(), containerName, "Exit", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
				}
			}
			private void traverse(IVertex iVtx) throws IOException, Exception {
				if (isSimpleState(iVtx) || isCompositeState(iVtx)) {
					checking(iVtx);
				}
				if (iVtx instanceof IState) {
					for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
						traverse(iSubvtx);
					}
				}
			}
			public StateExitTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					traverse(iVtx);
				}
			}
		}
		new StateExitTraverser(m_iLocalStm);
		if (!m_bFirstRound) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "begin"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "Exit", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
		indent--;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "state_exit", "end"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), "", "Exit", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		
	}

	/**
	 * forwardEventToSubmachines() 
	 * @throws IOException
	 * @throws Exception
	 */
	private void forwardEventToSubmachines() throws IOException, Exception {
		class SubmachineStateShallowTraverser {
			protected void checkStm(IState state) throws IOException, Exception {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_impl", "extnxt"), 
					state.getName(), 
					state.getSubmachine().getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
			  		state.getAlias1(),
					state.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			protected void checkStm(IStateMachine stm) throws IOException, Exception {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_impl", "extnxt"), 
					stm.getName(), 
					stm.getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
					stm.getAlias1(),
					stm.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			private void traverse(IState iState) throws IOException, Exception {
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
				if (iState.isSubmachineState()) {
					checkStm(iState);
				}
				for (int rgnIdx = 1; rgnIdx < iState.getRegionSize(); rgnIdx++) {
					IStateMachine region = TStmGenerator.findRegion(iState, rgnIdx);
					if (region != null) {					/* region split exists */
						checkStm(region);
					}
				}				
			}
			public SubmachineStateShallowTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
		}
		new SubmachineStateShallowTraverser(m_iLocalStm);
	}
	
	/**
	 * generateIsInCode() 
	 * @throws IOException
	 * @throws Exception
	 */
	private String generateSubmachineIsInCode() throws IOException, Exception {
		class SubmachineStateShallowTraverser {
			public String value;
			protected void checkStm(IState state) throws IOException, Exception {
				value += (Utils.get(m_stxCsv.get(indent, "api_call", "extnxt"), 
					state.getName(), 
					state.getSubmachine().getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
			  		state.getAlias1(),
					state.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			protected void checkStm(IStateMachine stm) throws IOException, Exception {
				value += (Utils.get(m_stxCsv.get(indent, "api_call", "extnxt"), 
					stm.getName(), 
					stm.getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
			  		stm.getAlias1(),
					stm.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			private void traverse(IState iState) throws IOException, Exception {
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
				if (iState.isSubmachineState()) {
					checkStm(iState);
				}
				for (int rgnIdx = 1; rgnIdx < iState.getRegionSize(); rgnIdx++) {
					IStateMachine region = TStmGenerator.findRegion(iState, rgnIdx);
					if (region != null) {					/* region split exists */
						checkStm(region);
					}
				}					
			}
			public SubmachineStateShallowTraverser(IStateMachine iStm) throws IOException, Exception {
				value = "";
				for (IVertex iVtx: iStm.getVertexes()) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
		}
		return (new SubmachineStateShallowTraverser(m_iLocalStm)).value;
	}

	/**
	 * generateSubmachineResetCode() 
	 * @throws IOException
	 * @throws Exception
	 */
	private String generateSubmachineResetCode() throws IOException, Exception {
		class SubmachineStateShallowTraverser {
			public String value;
			protected void checkStm(IState state) throws IOException, Exception {
				value += (Utils.get(m_stxCsv.get(indent, "api_call", "begin"), 
					state.getName(), 
					state.getSubmachine().getName(),
					m_iClass.getName(),
					"",
			  		state.getAlias1(),
					state.getDefinition(),
					state.getSubmachine().getStateMachineDiagram().getName()
				));
			}			
			protected void checkStm(IStateMachine stm) throws IOException, Exception {
				value += (Utils.get(m_stxCsv.get(indent, "api_call", "begin"), 
					stm.getName(), 
					stm.getName(),
					m_iClass.getName(),
					"",
			  		stm.getAlias1(),
					stm.getDefinition(),
					stm.getStateMachineDiagram().getName()
				));
			}			
			private void traverse(IState iState) throws IOException, Exception {
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
				if (iState.isSubmachineState()) {
					checkStm(iState);
				}
				for (int rgnIdx = 1; rgnIdx < iState.getRegionSize(); rgnIdx++) {
					IStateMachine region = TStmGenerator.findRegion(iState, rgnIdx);
					if (region != null) {					/* region split exists */
						checkStm(region);
					}
				}				
			}
			public SubmachineStateShallowTraverser(IStateMachine iStm) throws IOException, Exception {
				value = "";
				for (IVertex iVtx: iStm.getVertexes()) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
		}
		return (new SubmachineStateShallowTraverser(m_iLocalStm)).value;
	}
	
	/**
	 * printInitialStates
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStatesDefaultTrans() throws IOException, Exception {
		class StateWithDefTransTraverser {
			protected void checkStm(IVertex state) throws IOException, Exception {
				for (ITransition iDefTrans: state.getOutgoings()) {
					if (iDefTrans.getEvent().trim().isEmpty() && isVertexFound(m_iLocalStm.getVertexes(), iDefTrans.getTarget())) {
						m_transGen.printDefaultTransition(iDefTrans, m_bFirstRound);
						m_bFirstRound = false;
					}
				}
			}			
			private void traverse(IState iState) throws IOException, Exception {
				for (IVertex iVtx: iState.getSubvertexes()) {
					if (isSubExitPoint(iVtx)) {
						checkStm(iVtx);
					}
				}
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (isSimpleState(iVtx) || isCompositeState(iVtx) || isJunctionPoint(iVtx) ||
						isSuperEntryPoint(iVtx) || isInitialPoint(iVtx) || isHistory(iVtx)
					) {
						checkStm(iVtx);
					} 
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
			public StateWithDefTransTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					if (isSimpleState(iVtx) || isCompositeState(iVtx) || isJunctionPoint(iVtx) || 
						isSuperEntryPoint(iVtx) || isInitialPoint(iVtx) || isHistory(iVtx)
					) {
						checkStm(iVtx);
					} 
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
		}
		m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "name"), m_iLocalStm.getStateMachineDiagram().getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), "Start", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		indent++;
		class SubmachineStateShallowTraverser {
			protected void checkStm(IState state) throws IOException, Exception {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_decl", "extnxt"), 
					state.getName(), 
					state.getSubmachine().getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
			  		state.getAlias1(),
					state.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			protected void checkStm(IStateMachine stm) throws IOException, Exception {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "substm_decl", "extnxt"), 
					stm.getName(), 
					stm.getStateMachineDiagram().getName(),
					m_iClass.getName(),
					"",
			  		stm.getAlias1(),
					stm.getDefinition(),
					m_iLocalStm.getStateMachineDiagram().getName()
				));
			}			
			private void traverse(IState iState) throws IOException, Exception {
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
				if (iState.isSubmachineState()) {
					checkStm(iState);
				}
				for (int rgnIdx = 1; rgnIdx < iState.getRegionSize(); rgnIdx++) {
					IStateMachine region = TStmGenerator.findRegion(iState, rgnIdx);
					if (region != null) {					/* region split exists */
						checkStm(region);
					}
				}								
			}
			public SubmachineStateShallowTraverser(IStateMachine iStm) throws IOException, Exception {
				for (IVertex iVtx: iStm.getVertexes()) {
					if (iVtx instanceof IState) {
						traverse((IState)iVtx);
					}
				}
			}
		}
		new SubmachineStateShallowTraverser(m_iLocalStm);
		m_bFirstRound = true;
		new StateWithDefTransTraverser(m_iLocalStm);
		//ee.enumEntryPoints(m_stm, false, m_entryPtImpl);
		if (!m_bFirstRound) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "begin"), m_iLocalStm.getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
		}
		indent --;
		m_writer.write(Utils.get(m_stxCsv.get(indent, "default_trans", "end"), m_iLocalStm.getName(), m_iClass.getName(), m_iLocalStm.getStateMachineDiagram().getName(), "", "", "", m_iLocalStm.getStateMachineDiagram().getName()));
	}
}
