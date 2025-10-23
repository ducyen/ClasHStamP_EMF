package stm;

import java.io.IOException;
import java.io.Writer;

import com.change_vision.jude.api.inf.model.IAttribute;
import com.change_vision.jude.api.inf.model.IClass;

/**
 * TPropGenerator
 * @author 3140327
 *
 *   isDerived   constraint  initValue -> ctorParam  userDefined attribute
 *       -           -           -            o           x           o
 *       -           -           o            -           x           o
 *       -           o           -            o           x           o
 *       -           o           o            -           x           o
 *       o           -           -            o           -           o
 *       o           -           o            -           -           o
 *       o           o           -            o           o           -  also used for forced-constructor-parameter (if constraint is blank but not empty)
 *       o           o           o            -           o           -
 */
public class TPropGenerator extends TBaseGenerator {

	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TPropGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
	}

	/**
	 * printProperties
	 * @throws IOException
	 * @throws Exception
	 */
	public void printProperties() throws IOException, Exception {
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			if (!iAttr.getName().isEmpty() && findPropertyCode(iAttr, m_language) != null) {
				String attr_kind = findAttrPath(iAttr);
				attr_kind = "p" + attr_kind.substring(1);
				indent++;
				// acquire user mutator code
				Holder<String> mutatorScope = new Holder<String>();
				String mutatorCode = findMutatorCode(iAttr, m_language, mutatorScope);
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
				String accessorCode = findAccessorCode(iAttr, m_language, accessorScope);
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
				// acquire default accessor
				String userAccessorCode = Utils.get(m_stxCsv.get(indent, attr_kind, "begin"), iAttr.getName(), iAttr.getTypeExpression(), m_iClass.getName());
				formattedAccessorCode = (accessorCode == null || accessorCode.trim().isEmpty()) ? userAccessorCode : formattedAccessorCode; 
				// acquire default mutator
				String userMutatorCode = Utils.get(m_stxCsv.get(indent, attr_kind, "end"), iAttr.getName(), iAttr.getTypeExpression(), m_iClass.getName());
				formattedMutatorCode = (mutatorCode == null || mutatorCode.trim().isEmpty()) ? userMutatorCode : formattedMutatorCode;

				indent--;
				
				String propertyCode = "";
				// if readable
				if (accessorCode != null) {
					// print p__attr.ext1st
					propertyCode += Utils.get(
						m_stxCsv.get(indent, attr_kind, "ext1st"), 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()), 
						m_iClass.getName(),
						formattedAccessorCode,
						iAttr.getTypeModifier(),
						iAttr.getDefinition(),
						accessorScope.value
					);
				}
				// if writable
				if (mutatorCode != null) {
					// print p__attr.extnxt
					propertyCode += Utils.get(
						m_stxCsv.get(indent, attr_kind, "extnxt"), 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()), 
						m_iClass.getName(),
						formattedMutatorCode,
						iAttr.getTypeModifier(),
						iAttr.getDefinition(),
						mutatorScope.value
					);
				}
				
				// if having language specific property implementation)
				if (hasLangSpecPropStx(iAttr)) {
					// print-out p__attr.name
					m_writer.write(Utils.get(
						m_stxCsv.get(indent, attr_kind, "name"), 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()), 
						m_iClass.getName(),
						propertyCode,
						iAttr.getTypeModifier(),
						iAttr.getDefinition(),
						getVisibility(iAttr)
					));
				} else { // if not have specific implementation
					m_writer.write(propertyCode);
				}
			}
		}
	}
}
