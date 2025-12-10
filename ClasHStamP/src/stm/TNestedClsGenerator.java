package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.DataType;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.VisibilityKind;

/**
 * TNestedClsGenerator generates nested class definitions using UML2 API.
 */
public class TNestedClsGenerator extends TBaseGenerator {

    // store nested classifiers (classes / datatypes / enums)
    private final ArrayList<Classifier> m_nestedClasses = new ArrayList<>();

    /**
     * Constructor
     * 
     * @param stxCsv   syntax CSV configuration
     * @param umlClass the UML Class to process
     * @param writer   output writer
     */
    public TNestedClsGenerator(SyntaxCsv stxCsv, Classifier umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);

        // Collect nested classifiers if the root is a Class
        if (umlClass instanceof Class) {
            for (Classifier nested : ((Class) umlClass).getNestedClassifiers()) {
                // we only care about Class / DataType / Enumeration etc. – all are Classifier
                m_nestedClasses.add(nested);
            }
        }

        // Sort nested classes so that:
        // - superclasses come before subclasses
        // - owners of attributes come before their attribute types
        Collections.sort(m_nestedClasses, new Comparator<Classifier>() {
            @Override
            public int compare(Classifier lhs, Classifier rhs) {

                // 1) inheritance (generalization) order
                for (Generalization gen : lhs.getGeneralizations()) {
                    if (gen.getGeneral() == rhs) {
                        // lhs specializes rhs → lhs AFTER rhs
                        return 1;
                    }
                }
                for (Generalization gen : rhs.getGeneralizations()) {
                    if (gen.getGeneral() == lhs) {
                        // rhs specializes lhs → rhs AFTER lhs
                        return -1;
                    }
                }

                // 2) attribute dependency:
                //    if lhs has an attribute typed by rhs → lhs AFTER rhs
                for (Property prop : lhs.getAttributes()) {
                    if (prop.getName() != null && !prop.getName().isEmpty()
                            && prop.getType() == rhs) {
                        return 1;
                    }
                }
                //    if rhs has an attribute typed by lhs → rhs AFTER lhs
                for (Property prop : rhs.getAttributes()) {
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
        for (Classifier nestedClass : m_nestedClasses) {

            // Decide "kind" string for this nested classifier
            String stereotype;
            if (nestedClass instanceof Enumeration) {
                stereotype = "enum";
            } else {
                stereotype = "struct";
            }

            if (nestedClass instanceof NamedElement) {
                System.out.println("Nested class: " + stereotype + " "
                        + ((NamedElement) nestedClass).getName());
            }

            if (stereotype != null) {
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
                        desc));

                // Print attributes of nested class
                indent++;
                String path = m_stxCsv.get(indent, stereotype, "ext1st");
                for (Property prop : nestedClass.getAttributes()) {
                    if (prop.getName() != null && !prop.getName().isEmpty()) {
                        String attrDesc = "";
                        if (!path.isEmpty()) {
                            attrDesc = fillComment(prop, true);
                        }
                        // Type name
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
                                attrDesc));
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
                        desc));
            }
        }
    }
}
