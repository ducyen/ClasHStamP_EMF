package stm;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Comment;

import rfc.RStmGenerator;

public class TCtorGenerator extends TBaseGenerator {

        /**
         * Constructor
         * @param stxCsv
         * @param umlClass
         * @param writer
         */
        public TCtorGenerator(SyntaxCsv stxCsv, Class umlClass, Writer writer) {
                super(stxCsv, umlClass, writer);
        }

        /**
         * printCtorName
         * @param attrGen
         * @param hasVTbl
         * @param stmGen
         * @throws IOException
         * @throws Exception
         */
        public void printConstructor(TAttrGenerator attrGen, boolean hasVTbl, 
                                     RStmGenerator stmGen) throws IOException, Exception {
                // Print constructor comment
                fillCommentByDefinition(m_iClass, false);

                // Retrieve the class definition (first owned Comment) for templates
                String definition = "";
                for (Comment comment : m_iClass.getOwnedComments()) {
                        definition = comment.getBody();
                        break;
                }

                // Print constructor implementation name
                m_writer.write(
                        Utils.get(m_stxCsv.get(indent, "constructor", "name"), 
                                  m_iClass.getName(),
                                  m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                  m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                  "",
                                  "",
                                  definition
                        ));

                // print not-initialized attributes as input parameter
                String parameters = attrGen.collectCtorDeclParams();
                m_writer.write(parameters);

                if (m_iSuperClass != null) {
                        // print superClass not-initialized attributes as input parameter
                        String ctorCallParams = attrGen.collectSuperCtorCallParams();

                        // print superClass constructor call
                        m_writer.write(
                                Utils.get(m_stxCsv.get(indent, "ctor_call", "name"), 
                                          m_iClass.getName(),
                                          m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                          m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                          ctorCallParams,
                                          "",
                                          definition
                                ));

                        // Print virtual table setting if necessary
                        if (hasVTbl) {
                                m_writer.write(
                                        Utils.get(m_stxCsv.get(indent, "constructor", "extnxt"), 
                                                  m_iClass.getName(),
                                                  m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                                  m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                                  "",
                                                  "",
                                                  definition
                                        ));
                        }
                } else {
                        // Print constructor implementation begin
                        m_writer.write(Utils.get(m_stxCsv.get(indent, "constructor", "begin"), 
                                                 m_iClass.getName(),
                                                 m_iClass.getName(),
                                                 m_iClass.getName(),
                                                 "",
                                                 "",
                                                 definition
                        ));
                        // Print virtual table initialization if necessary
                        if (hasVTbl) {
                                m_writer.write(Utils.get(m_stxCsv.get(indent, "constructor", "ext1st"), 
                                                         m_iClass.getName(),
                                                         m_iClass.getName(),
                                                         m_iClass.getName(),
                                                         "",
                                                         "",
                                                         definition
                                ));
                        }
                }

                indent++;
                attrGen.printMemberInitializations();
                if (stmGen != null) {
                        stmGen.printStmInitialization();
                }
                indent--;

                // Print constructor end
                m_writer.write(Utils.get(m_stxCsv.get(indent, "constructor", "end"), 
                                         m_iClass.getName(),
                                         m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                                         m_iAncestor != null ? m_iAncestor.getName() : m_iClass.getName(),
                                         parameters,
                                         attrGen.collectCtorCallParams(),
                                         definition
                ));
        }
}
