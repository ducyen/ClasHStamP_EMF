package stm;

import java.awt.geom.Rectangle2D;
import java.io.Writer;
import java.util.Map;
import java.util.HashMap;

import com.change_vision.jude.api.inf.exception.InvalidEditingException;
import com.change_vision.jude.api.inf.exception.InvalidExportImageException;
import com.change_vision.jude.api.inf.exception.InvalidUsingException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IComment;
import com.change_vision.jude.api.inf.model.IConstraint;
import com.change_vision.jude.api.inf.model.IDependency;
import com.change_vision.jude.api.inf.model.IDiagram;
import com.change_vision.jude.api.inf.model.IElement;
import com.change_vision.jude.api.inf.model.IHyperlink;
import com.change_vision.jude.api.inf.model.IRealization;
import com.change_vision.jude.api.inf.model.IState;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IStateMachineDiagram;
import com.change_vision.jude.api.inf.model.ITaggedValue;
import com.change_vision.jude.api.inf.model.ITransition;
import com.change_vision.jude.api.inf.model.IUsage;
import com.change_vision.jude.api.inf.model.IVertex;
import com.change_vision.jude.api.inf.presentation.IPresentation;

public class TRgnGenerator extends TBaseGenerator {
	
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 * @param iMainStm
	 */
	public TRgnGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer, IStateMachine iMainStm) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
	}
	
	/* Treat Region as IStateMachine */
	public static class Region implements IStateMachine{
		private int m_regionIndex;
		private IState m_parentState;
		private IStateMachineDiagram m_diagram;
		//private IStateMachine m_iContainingStm;
		public Region(IState parentState, int regionIndex, IStateMachineDiagram diagram) {
			m_parentState = parentState;
			m_regionIndex = regionIndex;
			m_diagram = diagram;
		}
		public IState getParentState() { return m_parentState; }
		public int getRegionIndex() { return m_regionIndex; }
		public IVertex[] getVertexes() {
			try {
				return m_parentState.getSubvertexes(m_regionIndex);
			} catch (InvalidUsingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
		public String getName() {
			return m_parentState.getName() /*+ "_Top" + m_regionIndex*/;
		}
		@Override
		public String getAlias1() {
			// TODO Auto-generated method stub
			return getName();
		}
		@Override
		public String getAlias2() {
			// TODO Auto-generated method stub
			return getName();
		}
		@Override
		public IDependency[] getClientDependencies() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IRealization[] getClientRealizations() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IUsage[] getClientUsages() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IConstraint[] getConstraints() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String getDefinition() {
			// TODO Auto-generated method stub
			return getName();
		}
		@Override
		public IDiagram[] getDiagrams() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String getFullName(String arg0) {
			// TODO Auto-generated method stub
			return getName();
		}
		@Override
		public String getFullNamespace(String arg0) {
			// TODO Auto-generated method stub
			return getName();
		}
		@Override
		public IDependency[] getSupplierDependencies() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IRealization[] getSupplierRealizations() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IUsage[] getSupplierUsages() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public boolean isPackageVisibility() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public boolean isPrivateVisibility() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public boolean isProtectedVisibility() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public boolean isPublicVisibility() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public void setAlias1(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setAlias2(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setDefinition(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setName(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setVisibility(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void addStereotype(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public IComment[] getComments() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IElement getContainer() {
			// TODO Auto-generated method stub
			try {
				return TStmGenerator.findStmOf(m_parentState);
			} catch (InvalidUsingException e) {
				// TODO Auto-generated catch block
				return null;
			}
		}
		@Override
		public IElement[] getContainers() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String getId() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IElement getOwner() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IPresentation[] getPresentations() throws InvalidUsingException {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String[] getStereotypes() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String getTaggedValue(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public ITaggedValue[] getTaggedValues() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public String getTypeModifier() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public boolean hasStereotype(String arg0) {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public boolean isReadOnly() {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public void removeStereotype(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setTypeModifier(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public IHyperlink createElementHyperlink(IElement arg0, String arg1) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IHyperlink createFileHyperlink(String arg0, String arg1, String arg2) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IHyperlink createURLHyperlink(String arg0, String arg1) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public void deleteHyperlink(IHyperlink arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public IHyperlink[] getHyperlinks() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public IStateMachineDiagram getStateMachineDiagram() {
			// TODO Auto-generated method stub
			return m_diagram;
		}
		@Override
		public IState[] getStates() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public ITransition[] getTransitions() {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	public static class RegionDiagram implements IStateMachineDiagram {
		
		private int m_regionIndex;
		private IState m_parentState;
		private IStateMachineDiagram m_containingDiagram;

		public RegionDiagram(IState parentState, int regionIndex, IStateMachineDiagram contaningDiagram) {
			m_parentState = parentState;
			m_regionIndex = regionIndex;	
			m_containingDiagram = contaningDiagram;
		}

		@Override
		public String exportImage(String arg0, String arg1, double arg2)
				throws InvalidUsingException, InvalidExportImageException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Rectangle2D getBoundRect() {
			// TODO Auto-generated method stub
			return m_containingDiagram.getBoundRect();
		}

		@Override
		public IPresentation[] getPresentations() throws InvalidUsingException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public HashMap getProperties() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getProperty(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String[] getText() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setProperties(Map arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setProperty(String arg0, String arg1) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public String getAlias1() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getAlias2() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IDependency[] getClientDependencies() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IRealization[] getClientRealizations() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IUsage[] getClientUsages() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IConstraint[] getConstraints() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getDefinition() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IDiagram[] getDiagrams() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getFullName(String arg0) {
			// TODO Auto-generated method stub
			return m_containingDiagram.getFullName(arg0);
		}

		@Override
		public String getFullNamespace(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return m_parentState.getName() + "_Region" + m_regionIndex;
		}

		@Override
		public IDependency[] getSupplierDependencies() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IRealization[] getSupplierRealizations() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IUsage[] getSupplierUsages() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isPackageVisibility() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isPrivateVisibility() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isProtectedVisibility() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isPublicVisibility() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void setAlias1(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setAlias2(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setDefinition(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setName(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setVisibility(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void addStereotype(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public IComment[] getComments() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IElement getContainer() {
			// TODO Auto-generated method stub
			return m_containingDiagram;
		}

		@Override
		public IElement[] getContainers() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getId() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IElement getOwner() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String[] getStereotypes() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getTaggedValue(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ITaggedValue[] getTaggedValues() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getTypeModifier() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean hasStereotype(String arg0) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isReadOnly() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void removeStereotype(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setTypeModifier(String arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public IHyperlink createElementHyperlink(IElement arg0, String arg1) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IHyperlink createFileHyperlink(String arg0, String arg1, String arg2) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IHyperlink createURLHyperlink(String arg0, String arg1) throws InvalidEditingException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void deleteHyperlink(IHyperlink arg0) throws InvalidEditingException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public IHyperlink[] getHyperlinks() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IStateMachine getStateMachine() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
}
