package stm;

import java.io.Writer;
import java.util.StringTokenizer;

import com.change_vision.jude.api.inf.exception.InvalidUsingException;
import com.change_vision.jude.api.inf.model.IAttribute;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IComment;
import com.change_vision.jude.api.inf.model.IConstraint;
import com.change_vision.jude.api.inf.model.IFinalState;
import com.change_vision.jude.api.inf.model.IGeneralization;
import com.change_vision.jude.api.inf.model.IMultiplicityRange;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IOperation;
import com.change_vision.jude.api.inf.model.IPseudostate;
import com.change_vision.jude.api.inf.model.IState;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.ITransition;
import com.change_vision.jude.api.inf.model.IVertex;

import stm.TRgnGenerator.Region;

public class TBaseGenerator {
	protected static SyntaxCsv m_stxCsv;
	protected IClass m_iClass;
	protected IClass m_iSuperClass = null;
	protected IClass m_iAncestor = null;
	protected static Writer m_writer;
	protected static String m_namespaceSeparator;
	protected static String m_pkgPathSeparator;
	protected static String m_language = null;
	protected static int indent = 0;
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TBaseGenerator(
		SyntaxCsv stxCsv, 
		IClass iClass,
		Writer writer
	) {
		m_stxCsv = stxCsv;
		m_iClass = iClass;
		
		String extraCsvFilePath = null;
		if (hasStereotype(m_iClass.getStereotypes(), "baseClass")) {
			extraCsvFilePath = System.getenv("SYNTAX_BASECLASS");
		} else if (hasStereotype(m_iClass.getStereotypes(), "interface")) {
			extraCsvFilePath = System.getenv("SYNTAX_INTERFACE");
		} else if (m_iClass.isAbstract() || hasStereotype(m_iClass.getStereotypes(), "abstract")) {
			extraCsvFilePath = System.getenv("SYNTAX_ABSTRACT");
		}

		if (!isNullOrEmpty(extraCsvFilePath)) {
			m_stxCsv = new SyntaxCsv(extraCsvFilePath);			
		}
		
		m_writer = writer;
		IClass iGen = findGeneralization(iClass);
		if (iGen != null) {
			m_iSuperClass = iGen;
		}
		while (iGen != null) {
			m_iAncestor = iGen;
			iGen = findGeneralization(m_iAncestor);
		}
		if (m_language == null) {
			try {
				indent = Integer.parseInt(stxCsv.get("param_dir", "begin"));
			} catch (NumberFormatException e) {
				indent = 0;
			}
			m_namespaceSeparator = m_stxCsv.get("api_call", "end");
			m_pkgPathSeparator = m_stxCsv.get("param_dir", "end");
			m_language = System.getenv("LANGUAGE");
			System.out.println("Target language is " + m_language);
		}
	}
	protected IClass findGeneralization(IClass iClass) {
		for (IGeneralization iGeneralization: iClass.getGeneralizations()) {
			if (iGeneralization.getSuperType() != iClass) {
				return iGeneralization.getSuperType();
			}
		}
		// if no super class
		if (m_stxCsv.get(indent, "inheritance", "ext1st").trim().isEmpty()) {	// if no interface syntax, super class will be the first interface
			if (iClass.getClientRealizations().length > 0) {
				return (IClass)iClass.getClientRealizations()[0].getSupplier();
			}
		}
		return null;
	}
	/**
	 * hasStereotype
	 * @param stereotypes
	 * @param targetStereotype
	 * @return
	 */
	protected boolean hasStereotype(String[] stereotypes, String targetStereotype) {
		for (String stereotype: stereotypes) {
			if (stereotype.equalsIgnoreCase(targetStereotype)) {
				return true;
			}
		}
		return false;
		
	}

	/**
	 * getCommentWithStereotype
	 * @param comments
	 * @param targetStereotype
	 * @return
	 */
	protected String getCommentWithStereotype(IComment[] comments, String targetStereotype) {
		for (IComment comment: comments) {
			if (hasStereotype(comment.getStereotypes(), targetStereotype)) {
				return comment.getBody().replace("\\n", "\\\\n");
			}
		}
		return "";
	}
	
	/**
	 * fillComment
	 * @param e
	 * @param multiLineOnly
	 * @return
	 * @throws Exception
	 */
	protected String fillComment(INamedElement e, boolean multiLineOnly) throws Exception  {
		String comment = e.getDefinition() + getCommentWithStereotype(e.getComments(), "comment");
		return fillComment(comment, multiLineOnly);
	}
	
	/**
	 * fillCommentByDefinition
	 * @param e
	 * @param multiLineOnly
	 * @return
	 * @throws Exception
	 */
	protected String fillCommentByDefinition(INamedElement e, boolean multiLineOnly) throws Exception  {
		String comment = e.getDefinition();
		return fillComment(comment, multiLineOnly);
	}

	/**
	 * fillCommentByNote
	 * @param e
	 * @param multiLineOnly
	 * @return
	 * @throws Exception
	 */
	protected String fillCommentByNote(INamedElement e, boolean multiLineOnly) throws Exception  {
		String comment = getCommentWithStereotype(e.getComments(), "comment");
		return fillComment(comment, multiLineOnly);
	}	

	/**
	 * fillCommentByTaggedValue
	 * @param e
	 * @param name
	 * @param multiLineOnly
	 * @return
	 * @throws Exception
	 */
	protected String fillCommentByTaggedValue(INamedElement e, boolean multiLineOnly) throws Exception  {
		String[] items = { "brief", "notes", "sa", "details" };
		String comment = "";
		for (String item: items) {
			String taggedName = item;
			String taggedValue = e.getTaggedValue(taggedName);
			if (taggedValue != null && !taggedValue.isEmpty()) {
				comment += taggedValue + "\n";
			}
		}
		comment = comment.replace("\\n", System.getProperty("line.separator"));
		return fillComment(comment, multiLineOnly);
	}
	
	/**
	 * fillComment
	 * @param comment
	 * @param multiLineOnly
	 * @return
	 * @throws Exception
	 */
	protected String fillComment(String comment, boolean multiLineOnly) throws Exception  {
		String[] commentLines = comment.split("\\r?\\n");
		String desc = "";
		if (comment.isEmpty()) {
		}else if (commentLines.length == 1 && multiLineOnly) {
			desc = Utils.get(m_stxCsv.get(indent, "comment", "name"), commentLines[0]);
		} else {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "comment", "begin")));
			for (String line : commentLines) {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "comment", "extnxt"), line));
			}
			m_writer.write(Utils.get(m_stxCsv.get(indent, "comment", "end")));
		}
		return desc;
	}
	
	/**
	 * findSuperClass
	 * @param iClass
	 * @return
	 */
	protected IClass findSuperClass(IClass iClass) {
		for (IGeneralization iGeneralization: iClass.getGeneralizations()) {
			if (iGeneralization.getSuperType() != iClass) {
				return iGeneralization.getSuperType();
			}
		}

		return null;
	}
	
	/**
	 * findMultiplicity
	 * @param iAttr
	 * @return
	 */
	protected String findMultiplicity(IAttribute iAttr) {
		for (IMultiplicityRange multiRange: iAttr.getMultiplicity()) {
			if (multiRange.getUpper() > 1) {
				return String.valueOf(multiRange.getUpper());
			} else if (multiRange.getUpper() == IMultiplicityRange.UNLIMITED) {
				return " ";
			} else {
				return multiRange.getUpperString();
			}
		}
		return "";
	}
	
	/**
	 * findAttrInitValue
	 * @param attr
	 * @param lang
	 * @return
	 */
	protected String findAttrInitValue(IAttribute attr, String lang) {
		lang += ".init";
		String result = null;
		
		String eol = System.getProperty("line.separator");
		for (IConstraint constraint: attr.getConstraints()) {
			String precond = constraint.getName().replaceAll("\\r?\\n", eol);
			int eolIdx = precond.indexOf(eol);
			if (eolIdx > 0) {
				String key = precond.substring(0, eolIdx);
				String value = precond.substring(eolIdx + eol.length());
				if (key.equals(lang)) {
					result = value;
				}
			} else {
				if (precond.equals(lang)) {		/* empty initialization */
					result = " ";
				}
			}
		}		
		
		if (result == null) {
			result = attr.getInitialValue();
			result.trim();
		}
		
		String lastResult = "";
		String[] lines = result.split("\\r?\\n");
		int i = 0;
		for (String line : lines) {
			if (!line.isEmpty()) {
				lastResult += line;
				if (i < lines.length - 1) {
					lastResult += "\n";
				}
			}
			i++;
		}
		
		return lastResult;
	}
	
	/**
	 * findCtorUserCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	protected String findCtorUserCode(IClass ctor, String lang) {
		String result = "";
		
		String eol = System.getProperty("line.separator");
		for (IConstraint constraint: ctor.getConstraints()) {
			String precond = constraint.getName().replaceAll("\\r?\\n", eol);
			int eolIdx = precond.indexOf(eol);
			if (eolIdx > 0) {
				String key = precond.substring(0, eolIdx);
				String value = precond.substring(eolIdx + eol.length());
				if (key.equals(lang)) {
					result = value;
				}
			}
		}		
		
		String lastResult = "";
		String[] lines = result.split("\\r?\\n");
		int i = 0;
		for (String line : lines) {
			if (!line.isEmpty()) {
				lastResult += line;
				if (i < lines.length - 1) {
					lastResult += "\n";
				}
			}
			i++;
		}
		
		return lastResult;
	}

	/**
	 * findOperConstraintCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	protected String findOperConstraintCode(IOperation iOper, String lang) {
		String result = "";
		
		String eol = System.getProperty("line.separator");
		for (IConstraint constraint: iOper.getConstraints()) {
			String precond = constraint.getName().replaceAll("\\r?\\n", eol);
			int eolIdx = precond.indexOf(eol);
			if (eolIdx > 0) {
				String key = precond.substring(0, eolIdx);
				String value = precond.substring(eolIdx + eol.length());
				if (key.equals(lang)) {
					result = value;
				}
			}
		}		
		
		String lastResult = "";
		String[] lines = result.split("\\r?\\n");
		int i = 0;
		for (String line : lines) {
			if (!line.isEmpty()) {
				lastResult += line;
				if (i < lines.length - 1) {
					lastResult += "\n";
				}
			}
			i++;
		}
		
		return lastResult;
	}
	
	/**
	 * findAttrUserCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	private String findAttrUserCode(IAttribute attr, String lang) {
		for (IConstraint constraint: attr.getConstraints()) {
			String[] contents = constraint.getName().split("\r?\n", 2);
			if (contents.length == 2) {
				String key = contents[0];
				String content = contents[1];
				if (key.equals(lang)) {
					return content;
				}
			} else if (contents.length == 1) {
				String key = contents[0];
				String content = "\n";
				if (key.equals(lang)) {
					return content;
				}
			} else {
				;
			}
		}
		return null;
	}
    
    /* String utilities */	
	protected boolean isNullOrEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
	protected boolean notNullButEmpty(String s) {
		return s != null && s.trim().isEmpty();
	}
	
	/**
	 * findPropertyCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	public String findPropertyCode(IAttribute attr, String lang) {
		Holder<String> scope = new Holder<String>();
		String mutatorCode = findMutatorCode(attr, lang, scope);
		String accessorCode = findAccessorCode(attr, lang, scope);
		if (mutatorCode == null && accessorCode == null) {
			return null;
		}
		String result = "";
		if (mutatorCode != null) {
			result += mutatorCode;
		}
		if (accessorCode != null) {
			result += accessorCode;
		}
		return result.trim();
	}

	class Holder<T> {
	    public T value;
	}
	/**
	 * findMutatorCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	public String findMutatorCode(IAttribute attr, String lang, Holder<String> scope) {
		String theCode = findAttrUserCode(attr, lang + ".set");
		if (theCode != null) {
			scope.value = "";
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".set+");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "begin");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".set-");
		if (theCode != null) {
			scope.value =  m_stxCsv.get("visibility", "end");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".set#");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "extnxt");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".set~");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "name");
			return theCode.trim();
		}
		return theCode;
	}
	/**
	 * findAccessorCode
	 * @param attr
	 * @param lang
	 * @return
	 */
	public String findAccessorCode(IAttribute attr, String lang, Holder<String> scope) {
		String theCode = findAttrUserCode(attr, lang + ".get");
		if (theCode != null) {
			scope.value = "";
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".get+");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "begin");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".get-");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "end");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".get#");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "extnxt");
			return theCode.trim();
		}
		theCode = findAttrUserCode(attr, lang + ".get~");
		if (theCode != null) {
			scope.value = m_stxCsv.get("visibility", "name");
			return theCode.trim();
		}
		return theCode;
	}
	
	/**
	 * AttrKind
	 * @author 3140327
	 *
	 */
	protected enum AttrKind {
		STDTYPE,
		OBJECT,
		REFERENCE
	}
	
	/**
	 * 
	 */
	protected boolean isStandardType(IClass iClass) {
		boolean bResult = iClass.isPrimitiveType() || 
			hasStereotype(iClass.getStereotypes(), "stdtype")/* ||
			(iClass.getContainer() != null && ((INamedElement)iClass.getContainer()).getName().compareToIgnoreCase("stdtype") == 0)*/;
		return bResult;
	}
	
	/**
	 * findAttrKind
	 * @return
	 */
	protected AttrKind findAttrKind(IAttribute iAttr) {
		if (iAttr.getAssociation() != null && iAttr.getAssociation().getMemberEnds().length == 2) {
			if (isStandardType(iAttr.getType()) ||
				hasStereotype(iAttr.getType().getStereotypes(), "enum")
			) {
				return AttrKind.STDTYPE;
			}
			IAttribute theOther = iAttr.getAssociation().getMemberEnds()[0];
			if (theOther == iAttr){			// if an association exists, check the other end to specify type
				theOther = iAttr.getAssociation().getMemberEnds()[1];
			}
			if (theOther.isComposite()) {
				return AttrKind.OBJECT;
			}
		} else {
			if (isStandardType(iAttr.getType()) ||
				hasStereotype(iAttr.getType().getStereotypes(), "enum")
			) {
				return AttrKind.STDTYPE;
			}
			if (iAttr.isComposite()) {
				return AttrKind.OBJECT;
			}
		}
		return AttrKind.REFERENCE;
	}
	
	/**
	 * findAttrPath
	 * @param iAttr
	 * @return
	 */
	protected String findAttrPath(IAttribute iAttr) {
		String path = "";
		
		if (iAttr.isStatic()) {
			path += "s";
		} else if (!iAttr.isChangeable() && findPropertyCode(iAttr, m_language) == null) {
			path += "c";
		} else {
			path += "_";
		}
		
		if (!findMultiplicity(iAttr).isEmpty()) {
			path += "m";
		} else {
			path += "_";
		}

		if (findAttrKind(iAttr) == AttrKind.REFERENCE) {
			path += "r";
		} else if (findAttrKind(iAttr) == AttrKind.OBJECT) {
			path += "o";
		} else {
			path += "s";		// standard type
		}
		path += "_attr";
		return path;
	}
	
	/**
	 * getTypeLiteral
	 * @param type
	 * @return
	 */
	protected String getTypeLiteral(IClass type) {
		if (type == null) {
			return "";
		}
		if (m_namespaceSeparator.isEmpty() || 
			type.getContainer() == m_iClass || 
			!(type.getContainer() instanceof IClass)
		) {
			return type.getName();
		} else {
			return type.getFullName(m_namespaceSeparator);
		}
	}
	
	/**
	 * isCodeFile
	 * @return
	 */
	protected boolean isCodeFile() {
		return !m_stxCsv.get("action", "ext1st").isEmpty();		
	}

	protected boolean hasLangSpecPropStx(IAttribute iAttr) {
		String attr_kind = findAttrPath(iAttr);
		String prop_kind = "p" + attr_kind.substring(1);
		return findPropertyCode(iAttr, m_language) != null && !(m_stxCsv.get(prop_kind, "name").isEmpty());		
	}
	
	/**
	 * collectActions
	 * @param level
	 * @param routine
	 * @return
	 * @throws Exception
	 */
	protected String collectActions(int level, String routine) throws Exception {
		String actions = "";
		int adjust = 1;
		try {
			if (Integer.parseInt(m_stxCsv.get("param_dir", "begin")) < 0) {
				adjust = 0;
			}
		} catch (NumberFormatException e) {
		}
		
		if (!isCodeFile()) {
			return actions;
		}
		StringTokenizer actionTok = new StringTokenizer(routine, "\n");
		while (actionTok.hasMoreTokens()) {
			String actionName = actionTok.nextToken();
			actionName = actionName.replaceAll("\\s+$","");	// trim right
			actions = actions.concat(Utils.get(m_stxCsv.get(/*indent +*/ level + adjust, "action", "extnxt"), actionName));
		}
		return actions;
	}
	
	/**
	 * isSimpleState
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException
	 */
	protected boolean isSimpleState(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IState)) {
			return false;
		}
		if (vertex instanceof IFinalState) {
			return false;
		}
		return !isCompositeState(vertex);
	}
	
	/**
	 * isCompositeState
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException
	 */
	protected boolean isCompositeState(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IState)) {
			return false;
		}
		IState state = (IState)vertex;
		if (state.isSubmachineState()) {
			return false;
		}	
		if (state.getSubvertexes(0).length > 0) {
			return true;
		}
		//for (IVertex iSubvtx: state.getSubvertexes(0)) {
		//	if (isSubEntryPoint(iSubvtx) || isSubExitPoint(iSubvtx)) {
		//		return true;
		//	}
		//}
		return false;
	}
	
	/**
	 * isSuperEntryPoint
	 * @param vertex
	 * @return
	 */
	protected boolean isSuperEntryPoint(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isEntryPointPseudostate() && state.getOutgoings().length > 0 && state.getContainer() == null) {
			return true;
		}
		return false;
	}

	/**
	 * isSubEntryPoint
	 * @param vertex
	 * @return
	 */
	protected static boolean isSubEntryPoint(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isEntryPointPseudostate() && state.getOutgoings().length == 0 && state.getContainer() != null || state.isStubState()) {
			return true;
		}
		return false;
	}
	
	/**
	 * isSuperExitPoint
	 * @param vertex
	 * @return
	 */
	protected boolean isSuperExitPoint(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isExitPointPseudostate() && state.getOutgoings().length == 0 && state.getContainer() == null) {
			return true;
		}
		return false;
	}

	/**
	 * isSubExitPoint
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException 
	 */
	protected static boolean isSubExitPoint(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isExitPointPseudostate() && state.getOutgoings().length > 0 && state.getContainer() != null || state.isStubState()) {
			return true;
		}
		return false;
	}
	
	/**
	 * isHistory
	 * @param vertex
	 * @return
	 */
	protected boolean isHistory(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if ((state.isShallowHistoryPseudostate() || state.isDeepHistoryPseudostate()) && state.getOutgoings().length > 0 && state.getContainer() != null) {
			return true;
		}
		return false;
	}
	
	/**
	 * isHistoryWithoutIncoming
	 * @param vertex
	 * @param historyType 1: Shallow; 2: Deep; 3: Both
	 * @return
	 */
	protected boolean isHistoryWithoutIncoming(IVertex vertex, int historyType) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (historyType == 3) {
			if ((state.isShallowHistoryPseudostate() || state.isDeepHistoryPseudostate()) && state.getIncomings().length == 0 && state.getContainer() != null) {
				return true;
			}
		} else if (historyType == 1) {
			if (state.isShallowHistoryPseudostate() && state.getIncomings().length == 0 && state.getContainer() != null) {
				return true;
			}
		} else if (historyType == 2) {
			if (state.isDeepHistoryPseudostate() && state.getIncomings().length == 0 && state.getContainer() != null) {
				return true;
			}
		} else {
			
		}
		return false;
	}
	
	/**
	 * isInitialPoint
	 * @param vertex
	 * @return
	 */
	protected boolean isInitialPoint(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isInitialPseudostate() && state.getOutgoings().length > 0) {
			return true;
		}
		return false;
	}

	/**
	 * isJunctionPoint
	 * @param vertex
	 * @return
	 */
	protected boolean isJunctionPoint(IVertex vertex) {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isJunctionPseudostate()) {
			return true;
		}
		return false;
	}
	
	/**
	 * isJoinBar
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException 
	 */
	protected boolean isJoinBar(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		IPseudostate state = (IPseudostate)vertex;
		if (state.isJoinPseudostate()) {
			return true;
		}
		return false;
	}

	/**
	 * isForkBar
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException 
	 */
	protected boolean isForkBar(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IPseudostate)) {
			return false;
		}
		if (isSuperExitPoint(vertex)) {
		    return false;
        }
		IPseudostate state = (IPseudostate)vertex;
		if (state.isForkPseudostate()) {
			return true;
		}
		return false;
	}
	
	/**
	 * findInitialPoint
	 * @param vertex
	 * @return
	 * @throws InvalidUsingException
	 */
	protected IVertex findInitialPoint(IVertex vertex) throws InvalidUsingException {
		if (!(vertex instanceof IState)) {
			return null;
		}
		IState state = (IState)vertex;
		for (IVertex iVtx: state.getSubvertexes(0)) {
			if (isInitialPoint(iVtx)) {
				return iVtx;
			}
		}
		return null;
	}
	
	protected static boolean isVertexFound(IVertex[] vertexes, IVertex target) throws InvalidUsingException {
		for (IVertex iVtx: vertexes) {
			if (iVtx == target) {
				return true;
			}
			if (iVtx instanceof IState) {
				IState iState = (IState)iVtx;
				if (isSubEntryPoint(target) || isSubExitPoint(target)) {
					if (isVertexFound(iState.getSubvertexes(), target)) {
						return true;
					}
				} else {
					if (isVertexFound(iState.getSubvertexes(0), target)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * findHistoryWithoutIncoming
	 * @param vertex
	 * @param historyType 1: Shallow; 2: Deep; 3: Both
	 * @return
	 * @throws InvalidUsingException
	 */
	protected IVertex findHistoryWithoutIncoming(IVertex vertex, IVertex subVertex, int historyType) throws InvalidUsingException {
		if (!(vertex instanceof IState)) {
			return null;
		}
		IState state = (IState)vertex;
		
		if (subVertex == null) {
			for (IVertex iVtx: state.getSubvertexes()) {
				if (isHistoryWithoutIncoming(iVtx, historyType)) {
					IStateMachine theStm = TStmGenerator.findStmOf(subVertex);
					if (theStm instanceof Region) {
						Region regionStm = (Region)theStm;
						if (regionStm.getParentState() == state) {
							for (IVertex regionVtx: state.getSubvertexes(regionStm.getRegionIndex())) {
								if (isHistoryWithoutIncoming(regionVtx, historyType)) {
									return regionVtx;
								}
							}
						}
					}
				}
			}
		} else {
			IStateMachine theStm = TStmGenerator.findStmOf(subVertex);
			if (theStm instanceof Region) {
				Region regionStm = (Region)theStm;
				if (regionStm.getParentState() == state) {
					for (IVertex iVtx: state.getSubvertexes(regionStm.getRegionIndex())) {
						if (isHistoryWithoutIncoming(iVtx, historyType)) {
							return iVtx;
						}
					}
					return null;
				}
			}
		}

		for (IVertex iVtx: state.getSubvertexes(0)) {
			if (isHistoryWithoutIncoming(iVtx, historyType)) {
				return iVtx;
			}
		}
		
		return null;
	}
	
	/**
	 * getVisibility
	 */
	protected String getVisibility(INamedElement iElem) {
		if (iElem.isPrivateVisibility()) {
			return 	m_stxCsv.get("visibility", "end");
		} else if (iElem.isProtectedVisibility()) {
			return 	m_stxCsv.get("visibility", "extnxt");
		} else if (iElem.isPublicVisibility()) {
			return 	m_stxCsv.get("visibility", "begin");
		} else if (iElem.isPackageVisibility()) {
			return 	m_stxCsv.get("visibility", "name");
		} else {
			return 	m_stxCsv.get("visibility", "ext1st");
		}
	}
	
}
