package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;

import com.change_vision.jude.api.inf.exception.InvalidUsingException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IPseudostate;
import com.change_vision.jude.api.inf.model.IState;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IVertex;

import stm.TRgnGenerator.Region;
import stm.TRgnGenerator.RegionDiagram;

public class TStmGenerator extends TBaseGenerator {
	private IStateMachine m_iMainStm = null;
	public static class StmInstance {
		public IStateMachine m_type;
		public String m_name;
		public StmInstance(IStateMachine type, String name) {
			m_type = type;
			m_name = name;
		}
		public String getName() {
			return m_name;
		}
		public IStateMachine getSubmachine() {
			return m_type;
		}
	}
	private static HashMap<IStateMachine, List<StmInstance>> m_stmMap = new HashMap<IStateMachine, List<StmInstance>>();
	private static ArrayList<IStateMachine> m_stmSortedList;
	
	public static IStateMachine findStmOf(IVertex iVtx) throws InvalidUsingException {
		for (IStateMachine iStm: m_stmSortedList) {
			if (isVertexFound(iStm.getVertexes(), iVtx)) {
				return iStm;
			}
		}
		return null;
	}
	
	// Function to perform post-order traversal and collect nodes
	void postOrderTraversal(IStateMachine node, List<IStateMachine> result) {
	    // Traverse all children first
	    for (StmInstance instance : m_stmMap.get(node)) {
	        postOrderTraversal(instance.getSubmachine(), result);
	    }
	    // After all children are visited, add the current node
	    if (!result.contains(node)) {
	    	result.add(node);
	    }
	}	
	
	public static IStateMachine findRegion(IState parentState, int regionIndex) {
		for (IStateMachine iStm: m_stmSortedList) {
			if (iStm instanceof Region) {
				Region region = (Region)iStm;
				if (region.getParentState() == parentState && region.getRegionIndex() == regionIndex) {
					return iStm;
				}
			}
		}
		return null;
	}
	
	/**
	 * SubmachineStateDeepTraverser
	 * @author 3140327
	 *
	 */
	private class SubmachineStateDeepTraverser {
		private IStateMachine iContainingStm;
		private List<IStateMachine> visited = new ArrayList<IStateMachine>();
		protected void checkStm(IState iState) {
			if (!m_stmMap.containsKey(iContainingStm)) {
				m_stmMap.put(iContainingStm, new ArrayList<StmInstance>());
			}
			System.out.println( iContainingStm.getName() + " --> " + iState.getSubmachine().getName() );
			m_stmMap.get(iContainingStm).add(new StmInstance(iState.getSubmachine(), iState.getName()));
		}
		protected void checkStm(IStateMachine region) {
			if (!m_stmMap.containsKey(iContainingStm)) {
				m_stmMap.put(iContainingStm, new ArrayList<StmInstance>());
			}
			System.out.println( iContainingStm.getName() + " --> " + region.getName() );
			m_stmMap.get(iContainingStm).add(new StmInstance(region, region.getName()));
		}			
					
		private void traverse(IState iState) {
			if (iState.isSubmachineState()) {
				if (!visited.contains(iState.getSubmachine())) {
					new SubmachineStateDeepTraverser(iState.getSubmachine());
					visited.add(iState.getSubmachine());
				}
				checkStm(iState);
			}
			int rgnIdx = 1;
			while (rgnIdx > 0) {
				try {
					if (iState.getSubvertexes(rgnIdx).length > 0) {					/* region split exists */
						Region subRgn = new Region(iState, rgnIdx, new RegionDiagram(iState, rgnIdx, iContainingStm.getStateMachineDiagram()));
						new SubmachineStateDeepTraverser(subRgn);
						checkStm(subRgn);
					}
				} catch (InvalidUsingException e) {
					break;
				}
				rgnIdx++;
			}
			try {
				for (IVertex iVtx: iState.getSubvertexes(0)) {
					if (iVtx instanceof IState) {
						IState iSubstate = (IState)iVtx;
						traverse(iSubstate);
					}
				}
			} catch (InvalidUsingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		public SubmachineStateDeepTraverser(IStateMachine iStm) {
			iContainingStm = iStm;
			if (!m_stmMap.containsKey(iStm)) {
				m_stmMap.put(iStm, new ArrayList<StmInstance>());
			}
			for (IVertex iVtx: iStm.getVertexes()) {
				if (iVtx instanceof IState) {
					traverse((IState)iVtx);
				}
			}
		}
	}
	
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 * @param iMainStm
	 */
	public TStmGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer, IStateMachine iMainStm) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
		m_stmMap = new HashMap<IStateMachine, List<StmInstance>>();
		new SubmachineStateDeepTraverser(iMainStm);
		
	    m_stmSortedList = new ArrayList<IStateMachine>();
	    postOrderTraversal(iMainStm, m_stmSortedList);

		m_iMainStm = iMainStm;
	}
	
	/**
	 * getMainStm
	 * @return
	 */
	public IStateMachine getMainStm() {
		return m_iMainStm;
	}

	/**
	 * HistoryStateTraverser
	 * @author 3140327
	 *
	 */
	private abstract class HistoryStateTraverser {
		protected IStateMachine m_iStm;
		protected abstract void checkVertex(IVertex iVtx) throws IOException, Exception;
		private void traverse(IVertex iVtx) throws IOException, Exception {
			if (iVtx instanceof IState) {
				for (IVertex iSubvtx: ((IState)iVtx).getSubvertexes(0)) {
					traverse(iSubvtx);
				}
			}
			checkVertex(iVtx);
		}
		public HistoryStateTraverser(IStateMachine iStm) throws IOException, Exception {
			m_iStm = iStm;
			for (IVertex iVtx: iStm.getVertexes()) {
				traverse(iVtx);
			}
		}
	}
	
	/**
	 * printSubmachineTypes
	 * @param iStm
	 */
	public void printStmTypes() throws IOException, Exception {
		for (IStateMachine iStm: m_stmSortedList) {
			System.out.println("Generating stm " + iStm.getStateMachineDiagram().getName());
			
			//m_writer.write("//Generating stm " + iStm.getStateMachineDiagram().getName() + "\n");
			// Print internal regions, which is also treated as Statemachine 
			//TRgnGenerator rgnGen = new TRgnGenerator(m_stxCsv, m_iClass, m_writer, iStm);
			//rgnGen.printStmTypes();
			
			// Print header
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "statemachine", "begin"), 
				iStm.getStateMachineDiagram().getName(), 
				iStm.getName(), 
				m_iClass.getName(),
				"", "", ""

			));
			indent++;
			// Print sub-machine state declaration
			String path = m_stxCsv.get(indent, "substm_decl", "ext1st");
			for (StmInstance iSubSt: m_stmMap.get(iStm)) {
				m_writer.write(Utils.get(path, 
					iSubSt.getName(), 
					iSubSt.getSubmachine().getStateMachineDiagram().getName(), 
					iStm.getName(),
					"", "", ""

				));
			}
			// Print history-state declaration
			class HistoryStateTypes extends HistoryStateTraverser {
				public HistoryStateTypes(IStateMachine iStm)
						throws IOException, Exception {
					super(iStm);
					// TODO Auto-generated constructor stub
				}
				@Override
				protected void checkVertex(IVertex iVtx) throws IOException, Exception {
					if (iVtx instanceof IPseudostate) {
						if (((IPseudostate)iVtx).isShallowHistoryPseudostate() || ((IPseudostate)iVtx).isDeepHistoryPseudostate()) {
							IState state = (IState)iVtx.getContainer();
							if (state == null) {
								System.out.println("History state of " + m_iStm + " must be placed in a state, not in the machine directly");
								System.exit(1);
							}
							m_writer.write(Utils.get(m_stxCsv.get(indent, "history", "name"),
								state.getName(), 
								m_iClass.getName(),
								state.getName(),
								state.getName(),
								state.getAlias1(),
								state.getDefinition()
							));
							
						}
					}
				}			
			}
			new HistoryStateTypes(iStm);

			TStateGenerator stateGen = new TStateGenerator(m_stxCsv, m_iClass, m_writer, iStm);
			stateGen.printStateDeclarations();
			TTransGenerator transGen = null;
			transGen = new TTransGenerator(m_stxCsv, m_iClass, m_writer, stateGen.getLocalStm(), m_iMainStm);
			stateGen.printStateImplementations(transGen);
			stateGen.printStateToEntryExitMap();
			stateGen.printStatesDefaultTrans();
			stateGen.printTopEventProc();
			
			
			indent--;
			
			// Print state-machine constructor declarations
			String stmInitialization = "";
			
			indent++;
			// Print sub-machine state declaration
			path = m_stxCsv.get(indent, "substm_decl", "name");
			for (StmInstance iSubSt: m_stmMap.get(iStm)) {
				stmInitialization += Utils.get(path, 
					iSubSt.getName(), 
					iSubSt.getSubmachine().getStateMachineDiagram().getName(), 
					"",
					"", "", ""
				);
			}

			// Print history-state declaration
			class HistoryStateCtors extends HistoryStateTraverser {
				public String sResult = "";
				public HistoryStateCtors(IStateMachine iStm) throws IOException, Exception {
					super(iStm);
					// TODO Auto-generated constructor stub
				}
				@Override
				protected void checkVertex(IVertex iVtx) throws IOException, Exception {
					if (iVtx instanceof IPseudostate) {
						if (((IPseudostate)iVtx).isShallowHistoryPseudostate() || ((IPseudostate)iVtx).isDeepHistoryPseudostate()) {
							IState state = (IState)iVtx.getContainer();
							if (state == null) {
								System.out.println("History state of " + m_iStm + " must be placed in a state, not in the machine directly");
								System.exit(1);
							}
							sResult += Utils.get(m_stxCsv.get(indent, "history", "ext1st"),
								state.getName(), 
								m_iClass.getName(),
								state.getName(),
								state.getName(),
								state.getAlias1(),
								state.getDefinition(),
								m_iStm.getName()
							);
							
						}
					}
				}			
			}
			stmInitialization += (new HistoryStateCtors(iStm)).sResult;
		
			indent--;
			
			// Print footer
			m_writer.write(Utils.get(
				m_stxCsv.get(indent, "statemachine", "end"), 
				iStm.getStateMachineDiagram().getName(), 
				iStm.getName(),
				m_iClass.getName(),
				stmInitialization, "", ""
			));
			
		}
	}
	
	
	/**
	 * printStmDecl
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
	 * printStmAPIs
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStmAPIs() throws IOException, Exception {
		String initStateName = "";
		for (IVertex iVtx: m_iMainStm.getVertexes()) {
			if (isInitialPoint(iVtx)) {
				initStateName = iVtx.getName();
				break;
			}
		}
		m_writer.write(Utils.get(
			m_stxCsv.get(indent, "api_call", "name"), 
			m_iMainStm.getStateMachineDiagram().getName(), 
			m_iClass.getName(), 
			m_iClass.getName(), 
			initStateName, "", "",
			m_iMainStm.getStateMachineDiagram().getName()
		));
	}

	static IStateMachine m_iTargetMachine;
	private static boolean found(IStateMachine iContainingStm, IState iState) {
		IStateMachine iStm = iContainingStm;
		if (iState.isSubmachineState()) {
			iStm = iState.getSubmachine();
		}
		for (IVertex iVtx: iState.getSubvertexes()) {
			if (iVtx instanceof IState) {
				IState iSubstate = (IState)iVtx;
				if (iSubstate.isSubmachineState()) {
					Find(iSubstate.getSubmachine(), m_iTargetMachine);
				}
				if (found(iStm, iSubstate)) {
					return true;
				}
			}
		}
		if (iState.isSubmachineState()) {
			IStateMachine iSubMachine = iState.getSubmachine();
			if (iSubMachine == m_iTargetMachine) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
	
	public static boolean Find(IStateMachine iParentMachine, IStateMachine iTargetMachine) {
		m_iTargetMachine = iTargetMachine;
		for (IVertex iVtx: iParentMachine.getVertexes()) {
			if (iVtx instanceof IState) {
				if (found(iParentMachine, (IState)iVtx)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static boolean IsStmBelongToAnotherStm(IStateMachine iParentMachine, IStateMachine iTargetMachine) {
		for (StmInstance instance: m_stmMap.get(iParentMachine)) {
			if (instance.m_type == iTargetMachine) {
				return true;
			}
		}
		return false;
	}

}
