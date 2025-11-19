package stm;

import java.io.IOException;
import java.io.Writer;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Comment;
import org.eclipse.uml2.uml.EncapsulatedClassifier;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.VisibilityKind;

/**
 * TPropGenerator (UML2 version)
 * Generates property (attribute) code for a UML2 Class using EMF API.
 */
public class TPropGenerator extends TBaseGenerator {

    /**
     * Constructor
     * @param stxCsv   syntax CSV for formatting
     * @param umlClass UML Class whose properties to generate
     * @param writer   output Writer
     */
    public TPropGenerator(SyntaxCsv stxCsv, Classifier umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
    }

    /**
     * printProperties
     * Iterates over UML2 properties (owned attributes) and writes code.
     */
    public void printProperties() throws IOException, Exception {
        // Assume m_iClass now holds a UML2 Class instance
        Classifier umlClass = m_iClass;  // or use a field named m_umlClass if defined
        if (umlClass instanceof EncapsulatedClassifier) {
	        for (Property prop : ((EncapsulatedClassifier)umlClass).getOwnedAttributes()) {
	            String name = prop.getName();
	            if (name != null && !name.isEmpty() && findPropertyCode(prop, m_language) != null) {
	                String attr_kind = findAttrPath(prop);
	                attr_kind = "p" + attr_kind.substring(1);
	                indent++;
	                // acquire user mutator code
	                Holder<String> mutatorScope = new Holder<String>();
	                String mutatorCode = findMutatorCode(prop, m_language, mutatorScope);
	                String formattedMutatorCode = "";
	                if (mutatorCode != null) {
	                    for (String line : mutatorCode.split("\\r?\\n")) {
	                        if (!line.isEmpty() && isCodeFile()) {
	                            if (formattedMutatorCode.isEmpty()) {
	                                formattedMutatorCode += Utils.get(m_stxCsv.get(indent, "action", "ext1st"), line);
	                            } else {
	                                formattedMutatorCode += Utils.get(m_stxCsv.get(indent, "action", "extnxt"), line);
	                            }
	                        }
	                    }
	                }
	                // acquire user accessor code
	                Holder<String> accessorScope = new Holder<String>();
	                String accessorCode = findAccessorCode(prop, m_language, accessorScope);
	                String formattedAccessorCode = "";
	                if (accessorCode != null) {
	                    for (String line : accessorCode.split("\\r?\\n")) {
	                        if (!line.isEmpty() && isCodeFile()) {
	                            if (formattedAccessorCode.isEmpty()) {
	                                formattedAccessorCode += Utils.get(m_stxCsv.get(indent, "action", "ext1st"), line);
	                            } else {
	                                formattedAccessorCode += Utils.get(m_stxCsv.get(indent, "action", "extnxt"), line);
	                            }
	                        }
	                    }
	                }
	                // acquire default accessor (if no user accessor provided)
	                String typeLiteral = "";
	                Type propType = prop.getType();
	                if (propType != null) {
	                    typeLiteral = getTypeLiteral((Classifier)propType); // assume getTypeLiteral handles UML Type
	                }
	                String userAccessorCode = Utils.get(
	                    m_stxCsv.get(indent, attr_kind, "begin"),
	                    name,
	                    typeLiteral,
	                    umlClass.getName()
	                );
	                if (accessorCode == null || accessorCode.trim().isEmpty()) {
	                    formattedAccessorCode = userAccessorCode;
	                }
	                // acquire default mutator (if no user mutator provided)
	                String userMutatorCode = Utils.get(
	                    m_stxCsv.get(indent, attr_kind, "end"),
	                    name,
	                    typeLiteral,
	                    umlClass.getName()
	                );
	                if (mutatorCode == null || mutatorCode.trim().isEmpty()) {
	                    formattedMutatorCode = userMutatorCode;
	                }
	
	                indent--;
	                // Build combined property code fragments
	                String propertyCode = "";
	                // If readable
	                if (accessorCode != null) {
	                    propertyCode += Utils.get(
	                        m_stxCsv.get(indent, attr_kind, "ext1st"),
	                        name,
	                        typeLiteral,
	                        umlClass.getName(),
	                        formattedAccessorCode,
	                        /*typeModifier*/ "",
	                        /*definition*/ getDefinition(prop),
	                        accessorScope.value
	                    );
	                }
	                // If writable
	                if (mutatorCode != null) {
	                    propertyCode += Utils.get(
	                        m_stxCsv.get(indent, attr_kind, "extnxt"),
	                        name,
	                        typeLiteral,
	                        umlClass.getName(),
	                        formattedMutatorCode,
	                        /*typeModifier*/ "",
	                        /*definition*/ getDefinition(prop),
	                        mutatorScope.value
	                    );
	                }
	
	                // If a language-specific property syntax exists
	                if (hasLangSpecPropStx(prop)) {
	                    // Write the name entry using the accumulated propertyCode
	                    m_writer.write(Utils.get(
	                        m_stxCsv.get(indent, attr_kind, "name"),
	                        name,
	                        typeLiteral,
	                        umlClass.getName(),
	                        propertyCode,
	                        /*typeModifier*/ "",
	                        /*definition*/ getDefinition(prop),
	                        getVisibilityString(prop)
	                    ));
	                } else {
	                    m_writer.write(propertyCode);
	                }
	            }
	        }
        }
    }

    /**
     * Helper to concatenate UML comments for the property (as definition).
     */
    private String getDefinition(Property prop) {
        StringBuilder sb = new StringBuilder();
        for (Comment comment : prop.getOwnedComments()) {
            String body = comment.getBody();
            if (body != null && !body.isEmpty()) {
                sb.append(body.replace("\n", "\\n"));
            }
        }
        return sb.toString();
    }

    /**
     * Maps UML VisibilityKind to the CSV-defined visibility string.
     */
    private String getVisibilityString(Property prop) {
        VisibilityKind vis = prop.getVisibility();
        if (vis == VisibilityKind.PUBLIC_LITERAL) {
            return m_stxCsv.get("visibility", "begin");
        } else if (vis == VisibilityKind.PROTECTED_LITERAL) {
            return m_stxCsv.get("visibility", "extnxt");
        } else if (vis == VisibilityKind.PRIVATE_LITERAL) {
            return m_stxCsv.get("visibility", "end");
        } else if (vis == VisibilityKind.PACKAGE_LITERAL) {
            return m_stxCsv.get("visibility", "name");
        } else {
            return m_stxCsv.get("visibility", "ext1st");
        }
    }
}
