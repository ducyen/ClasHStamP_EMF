package stm;

import java.io.IOException;
import java.io.Writer;

import com.change_vision.jude.api.inf.model.IClass;

import rfc.RStmGenerator;

public class TCtorGenerator extends TBaseGenerator {

	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TCtorGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
	}

	/**
	 * printCtorName
	 * @param attrGen
	 * @param hasVtbl
	 * @param stmGen
	 * @throws IOException
	 * @throws Exception
	 */
	public void printConstructor(TAttrGenerator attrGen, boolean hasVTbl, RStmGenerator stmGen) throws IOException, Exception {
		// Print constructor comment
		fillCommentByDefinition(m_iClass, false);
		
		// Print constructor implementation name
		m_writer.write(
			Utils.get(m_stxCsv.get(indent, "constructor", "name"), 
			m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			"",
			"",
			m_iClass.getDefinition()
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
				m_iClass.getDefinition()
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
					m_iClass.getDefinition()
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
				m_iClass.getDefinition()
			));
			// Print virtual table initialization if necessary
			if (hasVTbl) {
				m_writer.write(Utils.get(m_stxCsv.get(indent, "constructor", "ext1st"), 
					m_iClass.getName(),
					m_iClass.getName(),
					m_iClass.getName(),
					"",
					"",
					m_iClass.getDefinition()
				));
			}
		}
		
		indent++;
		attrGen.printMemberInitializations();
		if (stmGen != null) {
			stmGen.printStmInitialization();
		}
		// Constructor: Print user code
		//for (String line : getCommentWithStereotype(m_iClass.getComments(), "code").split("\\r?\\n")) {
		//	if (!line.isEmpty()) {
		//		m_writer.write(line + "\n");
		//	}
		//}		
		//String userCode = findCtorUserCode(m_iClass, m_language);
		//String formattedUserCode = "";
		//if (!userCode.isEmpty()) {
		//	for (String line : userCode.split("\\r?\\n")) {
		//		if (!line.isEmpty()) {
		//			formattedUserCode += Utils.get(m_stxCsv.get(indent, "action", "extnxt"), line);
		//		}
		//	}
		//}

		indent--;
		
		// Print constructor end
		m_writer.write(Utils.get(m_stxCsv.get(indent, "constructor", "end"), 
			m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			m_iAncestor != null ? m_iAncestor.getName() : m_iClass.getName(),
			parameters,
			attrGen.collectCtorCallParams(),
			m_iClass.getDefinition()
		));
	}
}
