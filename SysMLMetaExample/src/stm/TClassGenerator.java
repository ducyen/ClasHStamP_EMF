package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.ClassifierTemplateParameter;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.InterfaceRealization;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.ParameterableElement;
import org.eclipse.uml2.uml.TemplateParameter;
import org.eclipse.uml2.uml.TemplateSignature;
import org.eclipse.uml2.uml.TemplateableElement;
import org.eclipse.uml2.uml.VisibilityKind;
import org.eclipse.uml2.uml.BehavioredClassifier;

public class TClassGenerator extends TBaseGenerator {

    /**
     * Constructor
     * @param stxCsv    Syntax CSV handler
     * @param umlClass  The UML Classifier (Class or Interface) to generate code for
     * @param writer    Output writer
     */
    public TClassGenerator(SyntaxCsv stxCsv, Class umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
    }

    /**
     * printClassHeader
     * Generates the class definition line (with template parameters, inheritance, etc.)
     */
    public void printClassHeader() throws IOException, Exception {
        // Find class comment
        String syntax = m_stxCsv.get(indent, "class", "name");
        String desc = "";
        if (!syntax.isEmpty()) {
            desc = fillCommentByNote(m_iClass, true);
        }

        /*--------------------------------------- template parameters --------------------------------------*/
        // generate template parameters
        TemplateSignature templateSig = null;
        if (m_iClass instanceof TemplateableElement) {
            templateSig = ((TemplateableElement) m_iClass).getOwnedTemplateSignature();
        }
        String templateParams = "";
        boolean bFirstRound = true;
        if (templateSig != null) {
            for (TemplateParameter templParam : templateSig.getOwnedParameters()) {
                String templateParamStr = "";
                // Determine template parameter name and type
                ParameterableElement paramElem = templParam.getParameteredElement();
                String paramName = (paramElem instanceof NamedElement) ? ((NamedElement) paramElem).getName() : "";
                Classifier boundType = null;
                if (templParam instanceof ClassifierTemplateParameter) {
                    List<Classifier> constrainingList = ((ClassifierTemplateParameter) templParam).getConstrainingClassifiers();
                    if (!constrainingList.isEmpty()) {
                        boundType = constrainingList.get(0);
                    }
                }
                String defaultValStr = "";
                ParameterableElement defaultElement = templParam.getDefault();
                if (defaultElement != null) {
                    if (defaultElement instanceof NamedElement) {
                        defaultValStr = ((NamedElement) defaultElement).getName();
                    } else {
                        defaultValStr = defaultElement.toString();
                    }
                }
                String typeModifier = "";
                if (defaultElement != null) {
                    typeModifier = "class";
                }
                String definition = (paramElem instanceof NamedElement) ? getDefinition((NamedElement) paramElem) : "";
                if (boundType == null) {
                    templateParamStr = Utils.get(m_stxCsv.get(indent, "template", "begin"),
                                                  paramName,
                                                  "",
                                                  m_iClass.getName(),
                                                  !defaultValStr.isEmpty() ? " = " + defaultValStr : "",
                                                  typeModifier,
                                                  definition
                    );
                } else {
                    templateParamStr = Utils.get(m_stxCsv.get(indent, "template", "end"),
                                                  paramName,
                                                  boundType.getName(),
                                                  m_iClass.getName(),
                                                  !defaultValStr.isEmpty() ? " = " + defaultValStr : "",
                                                  typeModifier,
                                                  definition
                    );
                }
                templateParams += Utils.get(m_stxCsv.get(indent, "template", bFirstRound ? "ext1st" : "extnxt"),
                                             templateParamStr,
                                             boundType != null ? boundType.getName() : "",
                                             m_iClass.getName(),
                                             !defaultValStr.isEmpty() ? " = " + defaultValStr : "",
                                             !defaultValStr.isEmpty() ? typeModifier : "",
                                             definition
                );
                bFirstRound = false;
            }
        }
        String templateParamsAll = "";
        if (!bFirstRound) {
            templateParamsAll = Utils.get(m_stxCsv.get(indent, "template", "name"),
                                           templateParams,
                                           m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                           m_iClass.getQualifiedName(),
                                           "",
                                           m_iClass.getLabel(),
                                           desc
            );
        }
        // generate template parameters bindings (C#-like)
        String templateParamBinds = "";
        bFirstRound = true;
        if (templateSig != null) {
            for (TemplateParameter templParam : templateSig.getOwnedParameters()) {
                if (templParam instanceof ClassifierTemplateParameter) {
                    List<Classifier> constrainingList = ((ClassifierTemplateParameter) templParam).getConstrainingClassifiers();
                    if (!constrainingList.isEmpty()) {
                        Classifier boundType = constrainingList.get(0);
                        ParameterableElement paramElem2 = templParam.getParameteredElement();
                        String paramName2 = (paramElem2 instanceof NamedElement) ? ((NamedElement) paramElem2).getName() : "";
                        ParameterableElement def2 = templParam.getDefault();
                        String defaultValStr2 = "";
                        if (def2 != null) {
                            defaultValStr2 = (def2 instanceof NamedElement) ? ((NamedElement) def2).getName() : def2.toString();
                        }
                        templateParamBinds += Utils.get(m_stxCsv.get(indent, "class", bFirstRound ? "ext1st" : "extnxt"),
                                                         paramName2,
                                                         boundType.getName(),
                                                         m_iClass.getName(),
                                                         !defaultValStr2.isEmpty() ? defaultValStr2 : "",
                                                         "",
                                                         (paramElem2 instanceof NamedElement) ? getDefinition((NamedElement) paramElem2) : ""
                        );
                        bFirstRound = false;
                    }
                }
            }
        }

        /*--------------------------------------- class inheritance --------------------------------------*/
        // generate inheritance code
        String inheritance = "";
        boolean hasInterfaces = false;
        if (m_iClass instanceof BehavioredClassifier) {
            hasInterfaces = !((BehavioredClassifier) m_iClass).getInterfaceRealizations().isEmpty();
        }
        if (m_iSuperClass != null || hasInterfaces) {
            // begin
            inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "begin"),
                                       m_iClass.getName(),
                                       m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                       m_iClass.getQualifiedName(),
                                       "",
                                       m_iClass.getLabel(),
                                       desc
            );
        }
        // generate [name] if have extension
        if (m_iSuperClass != null) {
            inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "name"),
                                       m_iClass.getName(),
                                       m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                       m_iClass.getQualifiedName(),
                                       "",
                                       m_iClass.getLabel(),
                                       desc
            );
        }
        // generate [end] if have extension and interface implementation
        if (m_iSuperClass != null && hasInterfaces) {
            inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "end"),
                                       m_iClass.getName(),
                                       m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                       m_iClass.getQualifiedName(),
                                       "",
                                       m_iClass.getLabel(),
                                       desc
            );
        }
        // generate [ext1st] [extnxt] if have implementation
        String path = m_stxCsv.get(indent, "inheritance", "ext1st");
        if (m_iClass instanceof BehavioredClassifier) {
            for (InterfaceRealization iRealization : ((BehavioredClassifier) m_iClass).getInterfaceRealizations()) {
                Interface contract = iRealization.getContract();
                NamedElement containerIfc = null;
                if (contract.getOwner() instanceof NamedElement) {
                    containerIfc = (NamedElement) contract.getOwner();
                }
                inheritance += Utils.get(path,
                                           contract.getName(),
                                           containerIfc != null ? containerIfc.getName() : "",
                                           contract.getQualifiedName(),
                                           "",
                                           contract.getLabel(),
                                           getDefinition(contract)
                );
                path = m_stxCsv.get(indent, "inheritance", "extnxt");
            }
        }

        /*--------------------------------------- class body --------------------------------------*/
        // Print class implementation name (signature line)
        m_writer.write(Utils.get(syntax,
                                 m_iClass.getName(),
                                 inheritance,
                                 m_iClass.getQualifiedName(),
                                 templateParamBinds,
                                 templateParamsAll,
                                 desc,
                                 getVisibility(m_iClass)
        ));
        // Print class begin
        m_writer.write(Utils.get(m_stxCsv.get(indent, "class", "begin"),
                                 m_iClass.getName(),
                                 m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                 m_iClass.getQualifiedName(),
                                 "",
                                 m_iClass.getLabel(),
                                 desc,
                                 getVisibility(m_iClass)
        ));

        indent++;
    }

    /**
     * printClassFooter
     */
    public void printClassFooter() throws IOException, Exception {
        indent--;
        // Print class end
        m_writer.write(Utils.get(m_stxCsv.get(indent, "class", "end"),
                                 m_iClass.getName(),
                                 m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                 m_iClass.getQualifiedName(),
                                 "",
                                 m_iClass.getLabel(),
                                 "",
                                 getVisibility(m_iClass)
        ));
    }
}
