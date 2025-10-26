package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.VisibilityKind;

/**
 * TNestedClsGenerator generates nested class definitions using UML2 API.
 */
public class TNestedClsGenerator extends TBaseGenerator {
    private ArrayList<Class> m_nestedClasses = new ArrayList<Class>();
    
    /**
     * Constructor
     * @param stxCsv   syntax CSV configuration
     * @param umlClass the UML Class to process
     * @param writer   output writer
     */
    public TNestedClsGenerator(SyntaxCsv stxCsv, Class umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
        // Collect nested classes
        for (org.eclipse.uml2.uml.Classifier nested : umlClass.getNestedClassifiers()) {
            if (nested instanceof Class) {
                m_nestedClasses.add((Class) nested);
            }
        }
        Collections.sort(m_nestedClasses, new Comparator<Class>() {
            public int compare(Class lhs, Class rhs) {
                // Consider generalizations: if lhs specializes rhs or vice versa
                for (Generalization gen : lhs.getGeneralizations()) {
                    if (gen.getGeneral() == rhs) {
                        return 1;
                    } else if (gen.getSpecific() == lhs) {
                        return 1;
                    }
                }
                // If lhs has an attribute of type rhs
                for (Property prop : lhs.getOwnedAttributes()) {
                    if (prop.getName() != null && !prop.getName().isEmpty()
                            && prop.getType() == rhs) {
                        return 1;
                    }
                }
                for (Generalization gen : rhs.getGeneralizations()) {
                    if (gen.getGeneral() == lhs) {
                        return -1;
                    } else if (gen.getSpecific() == rhs) {
                        return -1;
                    }
                }
                for (Property prop : rhs.getOwnedAttributes()) {
                    if (prop.getName() != null && !prop.getName().isEmpty()
                            && prop.getType() == lhs) {
                        return -1;
                    }
                }
                return 0;
            }
        });
    }
    
    /**
     * Print nested classes definitions
     */
    public void printNestedClasses() throws IOException, Exception {
        for (Class nestedClass : m_nestedClasses) {
            // Only process if a stereotype is applied
            if (!nestedClass.getAppliedStereotypes().isEmpty()) {
                // Get the first applied stereotype's name
                Stereotype umlStereo = nestedClass.getAppliedStereotypes().get(0);
                String stereotype = umlStereo.getName();
                // Prefix based on visibility
                VisibilityKind vis = nestedClass.getVisibility();
                if (vis == VisibilityKind.PUBLIC_LITERAL) {
                    stereotype = "b_" + stereotype;
                } else if (vis == VisibilityKind.PROTECTED_LITERAL) {
                    stereotype = "c_" + stereotype;
                } else {
                    stereotype = "i_" + stereotype;
                }
                
                // Find nested class's super class (inherited via generalization)
                Classifier nestedClassSuper = findSuperClass(nestedClass);
                
                // Prepare syntax template for class name or begin
                String syntax = m_stxCsv.get(indent, stereotype, "name");
                if (nestedClassSuper != null) {
                    syntax = m_stxCsv.get(indent, stereotype, "begin");
                }
                String desc = "";
                if (!syntax.isEmpty()) {
                    desc = fillComment(nestedClass, false);
                }
                
                // Write class declaration (name line)
                m_writer.write(Utils.get(
                        syntax,
                        nestedClass.getName(),
                        nestedClassSuper != null ? nestedClassSuper.getName() : "",
                        m_iClass.getName(),
                        "",
                        stereotype,
                        desc
                ));
                
                // Print attributes of nested class
                indent++;
                String path = m_stxCsv.get(indent, stereotype, "ext1st");
                for (Property prop : nestedClass.getOwnedAttributes()) {
                    if (prop.getName() != null && !prop.getName().isEmpty()) {
                        String attrDesc = "";
                        if (!path.isEmpty()) {
                            attrDesc = fillComment(prop, true);
                        }
                        // Type name (and optional modifier if needed)
                        String typeName = (prop.getType() != null ? prop.getType().getName() : "");
                        // Owning class name (the nestedClass itself, but use getClass_() to be safe)
                        String ownerName = "";
                        if (prop.getClass_() != null) {
                            ownerName = prop.getClass_().getName();
                        }
                        m_writer.write(Utils.get(
                                path,
                                prop.getName(),
                                typeName,
                                ownerName,
                                findAttrInitValue(prop, m_language),
                                findMultiplicity(prop),
                                attrDesc
                        ));
                        path = m_stxCsv.get(indent, stereotype, "extnxt");
                    }
                }
                indent--;
                
                // Print end of nested class
                m_writer.write(Utils.get(
                        m_stxCsv.get(indent, stereotype, "end"),
                        nestedClass.getName(),
                        nestedClassSuper != null ? nestedClassSuper.getName() : "",
                        m_iClass.getName(),
                        "",
                        stereotype,
                        desc
                ));
            }
        }
    }
}
