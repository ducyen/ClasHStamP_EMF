package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.EncapsulatedClassifier;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Generalization;

import rfc.RStmGenerator;

public class TAttrGenerator extends TBaseGenerator {
    /**
     * Constructor
     * @param stxCsv    Syntax CSV definitions
     * @param umlClass  UML2 Class element
     * @param writer    Output writer
     */
    public TAttrGenerator(SyntaxCsv stxCsv, Classifier umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
    }

    /**
     * AttrDeepTraverser
     * Traverses the inheritance hierarchy to collect relevant attributes
     */
    class AttrDeepTraverser {
        private HashMap<String, Property> m_attrMap = new LinkedHashMap<>();
        public HashMap<String, Property> getAttrMap() {
            return m_attrMap;
        }
        private void checking(Property attr) {
            // if attribute already in list ...
            if (m_attrMap.containsKey(attr.getName())) {
                // ... and now has an initialization, remove it (use the most-derived init)
                if (!findAttrInitValue(attr, m_language).isEmpty()) {
                    m_attrMap.remove(attr.getName());
                }
            } else {
                // if not in list: ensure it's not static, has no init, and no user-code getter/setter
                if (!attr.getName().isEmpty() 
                        && !attr.isStatic() 
                        && findAttrInitValue(attr, m_language).isEmpty() 
                        && isNullOrEmpty(findPropertyCode(attr, m_language))) {
                    // add it to list
                    m_attrMap.put(attr.getName(), attr);
                }
            }
        }
        private void traverse(Classifier cls) {
            Classifier superCls = findSuperClass(cls);
            if (superCls != null) {
                traverse(superCls);
            }
            if (cls instanceof EncapsulatedClassifier) {
	            for (Property attr : ((EncapsulatedClassifier)cls).getOwnedAttributes()) {
	                checking(attr);
	            }
            }
        }
        public AttrDeepTraverser(Classifier cls) {
            traverse(cls);
        }
    }

    /**
     * Collects constructor **declaration** parameter list (for generating constructor signature)
     */
    public String collectCtorDeclParams() throws IOException, Exception {
        AttrDeepTraverser attrTraverser = new AttrDeepTraverser(m_iClass);
        HashMap<String, Property> attrMap = attrTraverser.getAttrMap();
        String parameters = "";

        String column = "ext1st";
        for (Property attr : attrMap.values()) {
            String attr_kind = findAttrPath(attr);
            String syntax = m_stxCsv.get(indent, attr_kind, column);

            String desc = "";
            if (!attr.getOwnedComments().isEmpty()) {
                // Only add description comment if element has documentation
                desc = fillComment(attr, true);
            }
            parameters += Utils.get(
                    syntax,
                    attr.getName(),
                    getTypeLiteral(attr.getType()) + getTypeModifier(attr),
                    (attr.getOwner() instanceof NamedElement) 
                        ? ((NamedElement) attr.getOwner()).getName() : "",
                    findAttrInitValue(attr, m_language),
                    findMultiplicity(attr),
                    desc
            );
            column = "extnxt";
        }
        return parameters;
    }

    /**
     * Collects constructor **call** parameter list (for generating super() or this() calls)
     */
    public String collectCtorCallParams() throws IOException, Exception {
        AttrDeepTraverser attrTraverser = new AttrDeepTraverser(m_iClass);
        HashMap<String, Property> attrMap = attrTraverser.getAttrMap();
        String parameters = "";

        String column = "ext1st";
        for (Property attr : attrMap.values()) {
            String attr_kind = "ctor_call";
            String syntax = m_stxCsv.get(indent, attr_kind, column);

            String desc = "";
            if (!attr.getOwnedComments().isEmpty()) {
                desc = fillComment(attr, true);
            }
            parameters += Utils.get(
                    syntax,
                    attr.getName(),
                    getTypeLiteral(attr.getType()) + getTypeModifier(attr),
                    (attr.getOwner() instanceof NamedElement) 
                        ? ((NamedElement) attr.getOwner()).getName() : "",
                    // use the attribute name in constructor call syntax
                    Utils.get(m_stxCsv.get("ctor_call", "begin"), attr.getName()),
                    findMultiplicity(attr),
                    desc
            );
            column = "extnxt";
        }
        return parameters;
    }

    /**
     * Collects **super** constructor call parameters (for generating super(...) calls)
     */
    public String collectSuperCtorCallParams() throws IOException, Exception {
    	if (m_iSuperClass instanceof Class) {
    		return "";
    	}
        AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser((Class)m_iSuperClass);
        HashMap<String, Property> superAttrMap = superAttrTraverser.getAttrMap();
        String params = "";

        String column = "ext1st";
        for (Property attr : superAttrMap.values()) {
            String overriddenInit = "";
            boolean bFound = false;
            // check if subclass overrides initialization of this super attribute
            if (m_iClass instanceof EncapsulatedClassifier) {
	            for (Property myAttr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	                overriddenInit = findAttrInitValue(myAttr, m_language);
	                if (myAttr.getName().equals(attr.getName()) 
	                        && !overriddenInit.trim().isEmpty()) {
	                    bFound = true;
	                    break;
	                }
	            }
            }
            // default value is passing its own name (to be replaced in templates)
            String value = Utils.get(m_stxCsv.get("ctor_call", "begin"), attr.getName());
            String attr_kind = "ctor_call";
            if (bFound) {
                // if subclass has its own init, use that instead
                value = Utils.get(m_stxCsv.get("ctor_call", "end"), overriddenInit);
            }
            String syntax = m_stxCsv.get(indent, attr_kind, column);

            String desc = "";
            if (!attr.getOwnedComments().isEmpty()) {
                desc = fillComment(attr, true);
            }
            params += Utils.get(
                    syntax,
                    attr.getName(),
                    getTypeLiteral(attr.getType()) + getTypeModifier(attr),
                    (attr.getOwner() instanceof NamedElement) 
                        ? ((NamedElement) attr.getOwner()).getName() : "",
                    value,
                    findMultiplicity(attr),
                    desc
            );
            column = "extnxt";
        }
        return params;
    }

    /**
     * Prints member initializations inside constructors (for non-static attributes)
     */
    public void printMemberInitializations() throws IOException, Exception {
        HashMap<String, Property> superAttrMap;
        if (m_iSuperClass != null && m_iSuperClass instanceof Class) {
            // collect non-static inherited attributes that should *not* be reinitialized
            AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser((Class)m_iSuperClass);
            superAttrMap = superAttrTraverser.getAttrMap();
        } else {
            superAttrMap = new HashMap<>();
        }
        // Print members initialization
        boolean bFirstRound = true;
        if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property attr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            String propertyCode = findPropertyCode(attr, m_language);
	            if (!attr.isStatic() && !attr.getName().isEmpty() && isNullOrEmpty(propertyCode)) {
	                String attr_kind = findAttrPath(attr);
	                // use "end" syntax by default; may be overridden below for first element
	                String syntax = m_stxCsv.get(indent, attr_kind, "end");
	                if (m_iSuperClass == null && bFirstRound) {
	                    // if class has no superclass, the first attribute uses "begin" syntax
	                    syntax = m_stxCsv.get(indent, attr_kind, "begin");
	                }
	                String superRef = "";
	                // default value placeholder (calls default constructor call syntax with name)
	                String value = Utils.get(m_stxCsv.get("ctor_call", "begin"), attr.getName());
	                if (!findAttrInitValue(attr, m_language).isEmpty()) {
	                    // has its own initialization code
	                    if (superAttrMap.containsKey(attr.getName())) {
	                        // skip if it's an inherited attribute with its own init handled in super
	                        continue;
	                    }
	                    // use the actual initialization code and mark as end of list
	                    value = Utils.get(m_stxCsv.get("ctor_call", "end"), 
	                                      findAttrInitValue(attr, m_language));
	                    syntax = m_stxCsv.get(indent, attr_kind, "end");
	                    if (m_iSuperClass == null && bFirstRound) {
	                        syntax = m_stxCsv.get(indent, attr_kind, "begin");
	                    }
	                } else {
	                    // no init code
	                    if (superAttrMap.containsKey(attr.getName())) {
	                        // skip if it's an inherited attribute (no override and no init)
	                        continue;
	                    }
	                }
	                String desc = "";
	                if (!syntax.isEmpty()) {
	                    desc = fillComment(attr, true);
	                }
	                m_writer.write(
	                        Utils.get(
	                            syntax,
	                            attr.getName(),
	                            getTypeLiteral(attr.getType()) + getTypeModifier(attr),
	                            m_iClass.getName(),
	                            value,
	                            findMultiplicity(attr),
	                            desc,
	                            superRef
	                        )
	                );
	            }
	            bFirstRound = false;
	        }
        }
    }

    /**
     * Prints static attribute declarations
     */
    public void printStaticAttrDecls() throws IOException, Exception {
    	if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property attr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            if (attr.isStatic()) {
	                String attr_kind = findAttrPath(attr);
	                // default to "name" syntax
	                String syntax = m_stxCsv.get(indent, attr_kind, "name");
	                if (findAttrInitValue(attr, m_language).isEmpty()) {
	                    // if no initialization, use "end" syntax
	                    syntax = m_stxCsv.get(indent, attr_kind, "end");
	                }
	                if (attr.isReadOnly()) {  // not changeable (const):contentReference[oaicite:2]{index=2}
	                    // constant static attribute uses "ext1st" syntax
	                    syntax = m_stxCsv.get(indent, attr_kind, "ext1st");
	                }
	                String desc = "";
	                if (!syntax.isEmpty()) {
	                    desc = fillComment(attr, true);
	                }
	                m_writer.write(
	                        Utils.get(
	                            syntax,
	                            attr.getName(),
	                            getTypeLiteral(attr.getType()) + getTypeModifier(attr),
	                            m_iClass.getName(),
	                            findAttrInitValue(attr, m_language),
	                            findMultiplicity(attr),
	                            desc,
	                            getVisibility(attr)
	                        )
	                );
	            }
	        }
    	}
    }

    /**
     * Prints attribute declarations (instance attributes, possibly including v-table if any)
     * @param hasVtbl  whether virtual table is present (affects formatting)
     * @param stmGen   state-machine generator (for main state-machine declaration)
     */
    public void printAttrDeclarations(boolean hasVtbl, RStmGenerator stmGen) throws IOException, Exception {
        // Header of attribute group
        m_writer.write(
            Utils.get(m_stxCsv.get(indent, "attr_group", "name"),
                m_iClass.getName(),
                (m_iSuperClass != null ? m_iSuperClass.getName() : ""),
                (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                "",
                "",
                // use class documentation (definition) if available
                (!m_iClass.getOwnedComments().isEmpty() 
                    ? m_iClass.getOwnedComments().get(0).getBody() : "")
            )
        );

        HashMap<String, Property> superAttrMap;
        if (m_iSuperClass != null && m_iSuperClass instanceof Class) {
            AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser((Class)m_iSuperClass);
            superAttrMap = superAttrTraverser.getAttrMap();
        } else {
            superAttrMap = new HashMap<>();
        }

        // Choose syntax for first attribute group element depending on inheritance
        if (m_iSuperClass == null) {
            if (hasVtbl) {
                m_writer.write(
                    Utils.get(m_stxCsv.get(indent, "attr_group", "ext1st"),
                        m_iClass.getName(),
                        (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                        (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                        "",
                        "",
                        (!m_iClass.getOwnedComments().isEmpty() 
                            ? m_iClass.getOwnedComments().get(0).getBody() : "")
                    )
                );
            } else {
                m_writer.write(
                    Utils.get(m_stxCsv.get(indent, "attr_group", "begin"),
                        m_iClass.getName(),
                        (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                        (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                        "",
                        "",
                        (!m_iClass.getOwnedComments().isEmpty() 
                            ? m_iClass.getOwnedComments().get(0).getBody() : "")
                    )
                );
            }
        } else {
            // if there is a super class, use "extnxt" syntax for continuing attribute group
            m_writer.write(
                Utils.get(m_stxCsv.get(indent, "attr_group", "extnxt"),
                    m_iClass.getName(),
                    (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                    (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                    "",
                    "",
                    (!m_iClass.getOwnedComments().isEmpty() 
                        ? m_iClass.getOwnedComments().get(0).getBody() : "")
                )
            );
        }

        // Iterate through all instance (non-static) attributes of this class
        if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property attr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            String propertyCode = findPropertyCode(attr, m_language);
	            if (!attr.isStatic() && !attr.getName().isEmpty() 
	                    && isNullOrEmpty(propertyCode) && !hasLangSpecPropStx(attr)) {
	                String attr_kind = findAttrPath(attr);
	                String syntax = m_stxCsv.get(indent, attr_kind, "name");
	                if (superAttrMap.containsKey(attr.getName())) {
	                    // skip attributes inherited from superclass (already handled)
	                    continue;
	                }
	                String desc = "";
	                if (!syntax.isEmpty()) {
	                    desc = fillComment(attr, true);
	                }
	                m_writer.write(
	                    Utils.get(
	                        syntax,
	                        attr.getName(),
	                        getTypeLiteral(attr.getType()) + getTypeModifier(attr),
	                        m_iClass.getName(),
	                        findAttrInitValue(attr, m_language),
	                        findMultiplicity(attr),
	                        desc,
	                        getVisibility(attr)
	                    )
	                );
	            }
	        }
        }

        // If there is a state-machine generator, print main state-machine declaration
        if (stmGen != null) {
            stmGen.printMainStmDeclaration();
        }

        // Footer of attribute group
        m_writer.write(
            Utils.get(m_stxCsv.get(indent, "attr_group", "end"),
                m_iClass.getName(),
                (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                (m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName()),
                "",
                "",
                (!m_iClass.getOwnedComments().isEmpty() 
                    ? m_iClass.getOwnedComments().get(0).getBody() : "")
            )
        );
    }

    /** 
     * Determine if a language-specific property syntax exists for this attribute.
     * (This checks if user provided custom getter/setter code and corresponding syntax.)
     */
    protected boolean hasLangSpecPropStx(Property attr) {
        String attr_kind = findAttrPath(attr);
        String prop_kind = "p" + attr_kind.substring(1);  // replace leading indicator with 'p'
        return findPropertyCode(attr, m_language) != null 
                && !m_stxCsv.get(prop_kind, "name").isEmpty();
    }

    /**
     * Get the fully-qualified or simple type name of a classifier, depending on context.
     * (Replaces IClass.getName()/getFullName in Astah.)
     */
    protected String getTypeLiteral(org.eclipse.uml2.uml.Type type) {
        if (type == null) {
            return "";
        }
        if (m_namespaceSeparator.isEmpty() 
                || (type.eContainer() == m_iClass) 
                || !(type.eContainer() instanceof Class)) {
            // If no namespace qualifier needed, or type is inner class of current class, or type is not a class (e.g. in a package)
            return type.getName();
        } else {
            // Otherwise, return qualified name using the configured namespace separator
            String qualifiedName = type.getQualifiedName();  // e.g. "pkg::OuterClass::InnerClass"
            if (qualifiedName == null) {
                return type.getName();
            }
            return qualifiedName.replace("::", m_namespaceSeparator);
        }
    }

    /**
     * Determine any type modifier (pointer/array notation) for the given attribute.
     * (Replaces IAttribute.getTypeModifier in Astah.)
     */
    protected String getTypeModifier(Property attr) {
        // Pointer indicator for non-composite association ends; array indicator for multi-valued attributes
        if (attr.getAssociation() != null) {
            // If this attribute is an association end
            if (attr.getAssociation().getMemberEnds().size() == 2) {
                // binary association: check opposite end for composition
                Property opposite = attr.getAssociation().getMemberEnds().get(0);
                if (opposite == attr && attr.getAssociation().getMemberEnds().size() > 1) {
                    opposite = attr.getAssociation().getMemberEnds().get(1);
                }
                if (opposite.isComposite()) {
                    // composite aggregation -> treated as contained object (no pointer)
                    return "";
                }
            }
            // non-composite association end -> use pointer indicator (e.g., "*")
            return "*";
        } else if (attr.getUpper() == -1 || attr.getUpper() > 1) {
            // multi-valued attribute (unlimited or upper bound > 1) -> use array indicator (e.g., "[]")
            return "[]";
        }
        return "";
    }

    /**
     * Find the direct superclass (as a UML Class) of the given class.
     * (Follows generalization relationships; returns null if no superclass or if only interfaces are present.)
     */
    private Class findSuperClass(Class cls) {
        for (Generalization gen : cls.getGeneralizations()) {
            if (gen.getGeneral() instanceof Class && gen.getGeneral() != cls) {
                return (Class) gen.getGeneral();
            }
        }
        // Note: interfaces implemented (InterfaceRealizations) are not returned here since they are not UML Class.
        return null;
    }
}
