package stm;

import java.io.IOException;
import java.io.Writer;

import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IClassifierTemplateParameter;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IRealization;

public class TClassGenerator extends TBaseGenerator {

	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TClassGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
		
	}

	/**
	 * [template]   @C++								@Java					@C#
	 *   ext1st     template<[type] [name]				<[name]					<[name]
	 *   extnxt     , [type] [name]						, [name]				, [name]
	 *   begin      template<[type] [name] = [value]	<[name] extends [type]
	 *   end        , [type] [name] = [value]			, [name] extends [type]
	 *   name       >									>						>
	 * [inherit]								C++				Java				C#
	 *   name									public [name]	extends [name]		[name]
	 *   ext1st									public [name]	implements [name]	[name]
	 *   extnxt									, public [name]	, [name]			, [name]
	 *   begin[if have any base or interface]	:									:
	 *   end[if have both base and interface]	,									,
	 *   format: [begin] [name] [end] [ext1st] [extnxt]
	 * 
	 * [class@C++]
	 *   [mODIFIER]
	 *   class [nAME]
	 *              
	 *   template< [class|BASE0] T0 [= V0], [class|BASE1] T1 [= V1} >
	 * [value@C#]
	 * 
	 * class [nAME]<[mODIFIER]>: [tYPE] [vALUE] 
	 * 
	 * 
	 *       template                                        inheritance                              class
	 *       name     ext1st       extnxt...       begin/end name     ext1st       extnxt...begin/end name ext1st extnxt...         begin   end
	 * C++:  template<X T = x    , I U = i    , J V = j    > class A: public B,    public I, public J                                  { ... }
	 * C#:   class A <T          , U          , V          >        :        B,           I,        J where T: X where U: I where V: J { ... }
	 * Java: class A <T extends X, U extends I, V extends J>         extends B implements I,        J                                  { ... }
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
		String templateParams = "";
		boolean bFirstRound = true;
		for (IClassifierTemplateParameter templParam: m_iClass.getTemplateParameters()) {
			String templateParam = "";
			if (templParam.getType() == null) {
				templateParam = Utils.get(m_stxCsv.get(indent, "template", "begin"), 
					templParam.getName(), 
					"",
					m_iClass.getName(),
					templParam.getDefaultValue() != null ? " = " + templParam.getDefaultValue().toString() : "",
					templParam.getTypeModifier(),
					templParam.getDefinition()
				);
			} else {
				templateParam = Utils.get(m_stxCsv.get(indent, "template", "end"), 
					templParam.getName(), 
					templParam.getType().getName(),
					m_iClass.getName(),
					templParam.getDefaultValue() != null ? " = " + templParam.getDefaultValue().toString() : "",
					templParam.getTypeModifier(),
					templParam.getDefinition()
				);
			}
			templateParams += Utils.get(m_stxCsv.get(indent, "template", bFirstRound ? "ext1st" : "extnxt"), 
				templateParam, 
				templParam.getType() != null ? templParam.getType().getName() : "",
				m_iClass.getName(),
				templParam.getDefaultValue() != null ? " = " + templParam.getDefaultValue().toString() : "",
				templParam.getDefaultValue() != null ? templParam.getTypeModifier() : "",
				templParam.getDefinition()
			);
			bFirstRound = false;
		}		
		// generate template parameters
		String templateParamsAll = "";
		if (!bFirstRound) {
			templateParamsAll = Utils.get(m_stxCsv.get(indent, "template", "name"), 
				templateParams, 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_namespaceSeparator),
				"",
				m_iClass.getAlias1(),
				desc
			);
		}
		// generate template parameters bindings (C#-like)
		String templateParamBinds = "";
		bFirstRound = true;
		for (IClassifierTemplateParameter templParam: m_iClass.getTemplateParameters()) {
			if (templParam.getType() != null) {
				templateParamBinds += Utils.get(m_stxCsv.get(indent, "class", bFirstRound ? "ext1st" : "extnxt"), 
					templParam.getName(), 
					templParam.getType() != null ? templParam.getType().getName() : "",
					m_iClass.getName(),
					templParam.getDefaultValue() != null ? templParam.getDefaultValue().toString() : "",
					templParam.getTypeModifier(),
					templParam.getDefinition()
				);
			}
			bFirstRound = false;
		}	
		
		/*--------------------------------------- class inheritance --------------------------------------*/
		// generate inheritance code
		String inheritance = "";
		if (m_iSuperClass != null || m_iClass.getClientRealizations().length > 0) {
			// begin
			inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "begin"), 
				m_iClass.getName(), 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_namespaceSeparator),
				"",
				m_iClass.getAlias1(),
				desc
			);
		}
			
		// generate [name] if have extension
		if (m_iSuperClass != null) {
			inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "name"), 
				m_iClass.getName(), 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_namespaceSeparator),
				"",
				m_iClass.getAlias1(),
				desc
			);
		}
			
		// generate [end] if have extension and interface implementation
		if (m_iSuperClass != null && m_iClass.getClientRealizations().length > 0) {
			// end
			inheritance += Utils.get(m_stxCsv.get(indent, "inheritance", "end"), 
				m_iClass.getName(), 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_namespaceSeparator),
				"",
				m_iClass.getAlias1(),
				desc
			);
		}
			
		// generate [ext1st] [extnxt] if have implementation
		String path = m_stxCsv.get(indent, "inheritance", "ext1st");
		for (IRealization iRealization: m_iClass.getClientRealizations()) {
			INamedElement containerIfc = (INamedElement)iRealization.getSupplier().getContainer();
			inheritance += Utils.get(path, 
				iRealization.getSupplier().getName(),
				containerIfc != null ? containerIfc.getName() : "",
				iRealization.getSupplier().getFullNamespace(m_namespaceSeparator),
				"",
				iRealization.getSupplier().getAlias1(),
				iRealization.getSupplier().getDefinition()
			);
			path = m_stxCsv.get(indent, "inheritance", "extnxt");
		}
		
		/*--------------------------------------- class body --------------------------------------*/
		// Print class implementation name
		m_writer.write(Utils.get(syntax, 
			m_iClass.getName(), 
			inheritance,
			m_iClass.getFullNamespace(m_namespaceSeparator),
			templateParamBinds,
			templateParamsAll,
			desc,
			getVisibility(m_iClass)
		));
		
        //   print class begin
		m_writer.write(Utils.get(m_stxCsv.get(indent, "class", "begin"), 
			m_iClass.getName(), 
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			m_iClass.getFullNamespace(m_namespaceSeparator),
			"",
			m_iClass.getAlias1(),
			desc,
			getVisibility(m_iClass)
		));
		
		indent++;
	}
		
	/**
	 * printClassFooter
	 * @throws IOException
	 * @throws Exception
	 */
	public void printClassFooter() throws IOException, Exception {
		indent--;

        //   print class end
		m_writer.write(Utils.get(m_stxCsv.get(indent, "class", "end"), 
			m_iClass.getName(), 
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			m_iClass.getFullNamespace(m_namespaceSeparator),
			"",
			m_iClass.getAlias1(),
			"",
			getVisibility(m_iClass)
		));
	}
	
}
