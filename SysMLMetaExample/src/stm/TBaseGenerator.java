package stm;

import java.io.Writer;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Namespace;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.InterfaceRealization;
import org.eclipse.uml2.uml.Pseudostate;
import org.eclipse.uml2.uml.PseudostateKind;
import org.eclipse.uml2.uml.Vertex;
import org.eclipse.uml2.uml.VisibilityKind;


public class TBaseGenerator {
    protected static SyntaxCsv m_stxCsv;
    protected Classifier m_iClass;
    protected Classifier m_iSuperClass = null;
    protected Classifier m_iAncestor = null;
    protected static Writer m_writer;
    protected static String m_namespaceSeparator;
    public static String m_pkgPathSeparator;
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
            Classifier iClass,
            Writer writer
    ) {
        m_stxCsv = stxCsv;
        m_iClass = iClass;

        String extraCsvFilePath = null;
        if (hasStereotype(m_iClass.getKeywords().toArray(new String[0]), "baseClass")) {
            extraCsvFilePath = System.getenv("SYNTAX_BASECLASS");
        } else if (hasStereotype(m_iClass.getKeywords().toArray(new String[0]), "interface")) {
            extraCsvFilePath = System.getenv("SYNTAX_INTERFACE");
        } else if (m_iClass.isAbstract() || hasStereotype(m_iClass.getKeywords().toArray(new String[0]), "abstract")) {
            extraCsvFilePath = System.getenv("SYNTAX_ABSTRACT");
        }

        if (!isNullOrEmpty(extraCsvFilePath)) {
            m_stxCsv = new SyntaxCsv(extraCsvFilePath);
        }

        m_writer = writer;
        Classifier iGen = findGeneralization(iClass);
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
    protected Classifier findGeneralization(Classifier classifier) {
        for (Generalization gen : classifier.getGeneralizations()) {
            if (gen.getGeneral() != classifier) {
                return gen.getGeneral();
            }
        }
        // if no super class
        if (m_stxCsv.get(indent, "inheritance", "ext1st").trim().isEmpty()) {  // if no interface syntax, super class will be the first interface
            if (classifier instanceof Class) {
                Class cls = (Class) classifier;
                if (!cls.getInterfaceRealizations().isEmpty()) {
                    return cls.getInterfaceRealizations().get(0).getContract();
                }
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
        for (String stereotype : stereotypes) {
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
    protected String getCommentWithStereotype(List<Comment> comments, String targetStereotype) {
        for (Comment comment : comments) {
            if (targetStereotype.equalsIgnoreCase("comment")) {
                // Return the first comment's body (assuming it contains the desired note)
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
    protected String fillComment(NamedElement e, boolean multiLineOnly) throws Exception {
        List<Comment> comments = e.getOwnedComments();
        String combinedComment = "";
        if (!comments.isEmpty()) {
            combinedComment = comments.get(0).getBody();
            if (comments.size() > 1) {
                // Append the body of the second comment (assumed to be the note) with escaped newlines
                combinedComment += comments.get(1).getBody().replace("\\n", "\\\\n");
            }
        }
        return fillComment(combinedComment, multiLineOnly);
    }

    /**
     * fillCommentByDefinition
     * @param e
     * @param multiLineOnly
     * @return
     * @throws Exception
     */
    protected String fillCommentByDefinition(NamedElement e, boolean multiLineOnly) throws Exception {
        String comment = "";
        List<Comment> comments = e.getOwnedComments();
        if (!comments.isEmpty()) {
            comment = comments.get(0).getBody();
        }
        return fillComment(comment, multiLineOnly);
    }

    /**
     * fillCommentByNote
     * @param e
     * @param multiLineOnly
     * @return
     * @throws Exception
     */
    protected String fillCommentByNote(NamedElement e, boolean multiLineOnly) throws Exception {
        String noteComment = getCommentWithStereotype(e.getOwnedComments(), "comment");
        return fillComment(noteComment, multiLineOnly);
    }

    /**
     * fillComment
     * @param comment
     * @param multiLineOnly
     * @return
     * @throws Exception
     */
    protected String fillComment(String comment, boolean multiLineOnly) throws Exception {
    	if (comment == null) {
    		return "";
    	}
        String[] commentLines = comment.split("\\r?\\n");
        String desc = "";
        if (comment.isEmpty()) {
            // no comment
        } else if (commentLines.length == 1 && multiLineOnly) {
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
    protected Classifier findSuperClass(Classifier iClass) {
        for (Generalization gen : iClass.getGeneralizations()) {
            if (gen.getGeneral() != iClass) {
                return gen.getGeneral();
            }
        }
        return null;
    }

    /**
     * findMultiplicity
     * @param iAttr
     * @return
     */
    protected String findMultiplicity(Property iAttr) {
        int upper = iAttr.getUpper();
        if (upper > 1) {
            return String.valueOf(upper);
        } else {
            return "";
        }
    }

    /**
     * findAttrInitValue
     * @param attr
     * @param lang
     * @return
     */
    protected String findAttrInitValue(Property attr, String lang) {
        lang += ".init";
        String result = null;

        String eol = System.getProperty("line.separator");
        // Search for a matching Constraint attached to this attribute
        Classifier owner = attr.getOwner() instanceof Classifier ? (Classifier) attr.getOwner() : null;
        if (owner != null) {
            for (Constraint constraint : owner.getOwnedRules()) {
                if (constraint.getConstrainedElements().contains(attr)) {
                    String precond = constraint.getName().replaceAll("\\r?\\n", eol);
                    int eolIdx = precond.indexOf(eol);
                    if (eolIdx > 0) {
                        String key = precond.substring(0, eolIdx);
                        String value = precond.substring(eolIdx + eol.length());
                        if (key.equals(lang)) {
                            result = value;
                        }
                    } else {
                        if (precond.equals(lang)) {  /* empty initialization */
                            result = " ";
                        }
                    }
                }
            }
        }

        if (result == null) {
            result = attr.getDefault();
            if (result != null) {
                result.trim();
            } else {
                result = "";
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
     * @param iOper
     * @param lang
     * @return
     */
    protected String findOperConstraintCode(Operation iOper, String lang) {
        String result = "";

        String eol = System.getProperty("line.separator");
        for (Constraint constraint : iOper.getOwnedRules()) {
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
    private String findAttrUserCode(Property attr, String lang) {
        Classifier owner = attr.getOwner() instanceof Classifier ? (Classifier) attr.getOwner() : null;
        if (owner != null) {
            for (Constraint constraint : owner.getOwnedRules()) {
                if (constraint.getConstrainedElements().contains(attr)) {
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
                        // no content
                    }
                }
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
    public String findPropertyCode(Property attr, String lang) {
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
     * @param scope
     * @return
     */
    public String findMutatorCode(Property attr, String lang, Holder<String> scope) {
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
            scope.value = m_stxCsv.get("visibility", "end");
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
     * @param scope
     * @return
     */
    public String findAccessorCode(Property attr, String lang, Holder<String> scope) {
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
     */
    protected enum AttrKind {
        STDTYPE,
        OBJECT,
        REFERENCE
    }

    /**
     * 
     */
    protected boolean isStandardType(Classifier cls) {
    	if (cls == null) {
    		return true;
    	}
        boolean bResult = (cls instanceof PrimitiveType) ||
                          hasStereotype(cls.getKeywords().toArray(new String[0]), "stdtype") /* ||
                          (cls.getOwner() != null && cls.getOwner() instanceof NamedElement && 
                           ((NamedElement) cls.getOwner()).getName().equalsIgnoreCase("stdtype"))*/;
        return bResult;
    }

    /**
     * findAttrKind
     * @return
     */
    protected AttrKind findAttrKind(Property iAttr) {
        Association assoc = iAttr.getAssociation();
        if (assoc != null && assoc.getMemberEnds().size() == 2) {
            Classifier attrType = (Classifier) iAttr.getType();
            if (attrType != null && (isStandardType(attrType) ||
                    attrType instanceof Enumeration ||
                    hasStereotype(attrType.getKeywords().toArray(new String[0]), "enum")
                )) {
                return AttrKind.STDTYPE;
            }
            Property theOther = assoc.getMemberEnds().get(0);
            if (theOther == iAttr) {  // if an association exists, check the other end to specify type
                theOther = assoc.getMemberEnds().get(1);
            }
            if (theOther.isComposite()) {
                return AttrKind.OBJECT;
            }
        } else {
            Classifier attrType = (Classifier) iAttr.getType();
            if (attrType != null && (isStandardType(attrType) ||
                    attrType instanceof Enumeration ||
                    hasStereotype(attrType.getKeywords().toArray(new String[0]), "enum")
                )) {
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
    protected String findAttrPath(Property iAttr) {
        String path = "";

        if (iAttr.isStatic()) {
            path += "s";
        } else if (iAttr.isReadOnly() && findPropertyCode(iAttr, m_language) == null) {
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
            path += "s";  // standard type
        }
        path += "_attr";
        return path;
    }

    /**
     * getTypeLiteral
     * @param type
     * @return
     */
    protected String getTypeLiteral(Classifier type) {
        if (type == null) {
            return "";
        }
        if (m_namespaceSeparator.isEmpty() ||
            type.getOwner() == m_iClass ||
            !(type.getOwner() instanceof Class)
        ) {
            return type.getName();
        } else {
            // Replace default UML separator "::" with the specified namespace separator
            String qualifiedName = type.getQualifiedName();
            if (qualifiedName == null) {
                return type.getName();
            }
            return qualifiedName.replace(NamedElement.SEPARATOR, m_namespaceSeparator);
        }
    }

    /**
     * isCodeFile
     * @return
     */
    protected boolean isCodeFile() {
        return !m_stxCsv.get("action", "ext1st").isEmpty();
    }

    protected boolean hasLangSpecPropStx(Property iAttr) {
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
            actionName = actionName.replaceAll("\\s+$", ""); // trim right
            actions = actions.concat(Utils.get(m_stxCsv.get(/*indent +*/ level + adjust, "action", "extnxt"), actionName));
        }
        return actions;
    }

    /**
     * isJoinBar
     * @param vertex
     * @return
     * @throws InvalidUsingException 
     */
    protected boolean isJoinBar(Vertex vertex) {
        if (!(vertex instanceof Pseudostate)) {
            return false;
        }
        Pseudostate state = (Pseudostate) vertex;
        if (state.getKind() == PseudostateKind.JOIN_LITERAL) {
            return true;
        }
        return false;
    }

    /**
     * getVisibility
     */
    protected String getVisibility(NamedElement iElem) {
        if (iElem.getVisibility() == VisibilityKind.PRIVATE_LITERAL) {
            return m_stxCsv.get("visibility", "end");
        } else if (iElem.getVisibility() == VisibilityKind.PROTECTED_LITERAL) {
            return m_stxCsv.get("visibility", "extnxt");
        } else if (iElem.getVisibility() == VisibilityKind.PUBLIC_LITERAL) {
            return m_stxCsv.get("visibility", "begin");
        } else if (iElem.getVisibility() == VisibilityKind.PACKAGE_LITERAL) {
            return m_stxCsv.get("visibility", "name");
        } else {
            return m_stxCsv.get("visibility", "ext1st");
        }
    }
    
    public static String getFullNamespace(NamedElement cls) {
        StringBuilder ns = new StringBuilder();
        for (Namespace namespace : cls.allNamespaces()) {
            if (namespace.getName() != null && !namespace.getName().isEmpty()) {
                ns.insert(0, namespace.getName() + "::");
            }
        }
        return ns.toString();
    }    

    /**
     * getDefinition
     * @param e
     * @return
     */
    protected String getDefinition(NamedElement e) {
    	return "";
    }
}
