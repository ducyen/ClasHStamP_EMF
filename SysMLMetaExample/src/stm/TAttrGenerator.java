package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.change_vision.jude.api.inf.model.IAttribute;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.INamedElement;

import rfc.RStmGenerator;

public class TAttrGenerator extends TBaseGenerator {
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TAttrGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
	}

	/**
	 * AttrDeepTraverser
	 * @author 3140327
	 *
	 */
	class AttrDeepTraverser {
		private HashMap<String, IAttribute> m_attrMap = new LinkedHashMap<String, IAttribute>();
		public HashMap<String, IAttribute> getAttrMap() {
			return m_attrMap;
		}
		private void checking(IAttribute iAttr) {
			// if attribute is in list, ...
			if (m_attrMap.containsKey(iAttr.getName())) {
				// ...and initialized, remove it
				if (!findAttrInitValue(iAttr, m_language).isEmpty()) {
					m_attrMap.remove(iAttr.getName());
				}
			} else {// if not in list, not-initialized, not-static, and not a user-code getter/setter ...
				if (!iAttr.getName().isEmpty() && 
					!iAttr.isStatic() &&
					findAttrInitValue(iAttr, m_language).isEmpty() &&
					isNullOrEmpty(findPropertyCode(iAttr, m_language))
				) {
					// ... add it to list
					m_attrMap.put(iAttr.getName(), iAttr);
				}
			}
		}
		private void traverse(IClass iClass) {
			IClass iSuperCls = findSuperClass(iClass);
			if (iSuperCls != null) {
				traverse(iSuperCls);
			}
			for (IAttribute iAttr: iClass.getAttributes()) {
				checking(iAttr);
			}
		}
		public AttrDeepTraverser(IClass iClass) {
			traverse(iClass);
		}
	}
	
	/**
	 * printCtorDeclParams
	 * @throws IOException
	 * @throws Exception
	 */
	public String collectCtorDeclParams() throws IOException, Exception {
		AttrDeepTraverser attrTraverser = new AttrDeepTraverser(m_iClass);
		HashMap<String, IAttribute> attrMap = attrTraverser.getAttrMap();
		String parameters = "";
		
		String column = "ext1st";
		for (IAttribute iAttr: attrMap.values()) {
			String attr_kind = findAttrPath(iAttr);
			String syntax = m_stxCsv.get(indent, attr_kind, column);
			String desc = "";
			if (!iAttr.getDefinition().isEmpty()) {
				desc = fillComment(iAttr, true);
			}
			parameters += Utils.get(
				syntax,
				iAttr.getName(),
				getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(),
				iAttr.getContainer() != null ? ((INamedElement)iAttr.getContainer()).getName() : "",
				findAttrInitValue(iAttr, m_language),
				findMultiplicity(iAttr),
				desc
			);			
			column = "extnxt";			
		}
		return parameters;
	}

	/**
	 * printCtorDeclParams
	 * @throws IOException
	 * @throws Exception
	 */
	public String collectCtorCallParams() throws IOException, Exception {
		AttrDeepTraverser attrTraverser = new AttrDeepTraverser(m_iClass);
		HashMap<String, IAttribute> attrMap = attrTraverser.getAttrMap();
		String parameters = "";
		
		String column = "ext1st";
		for (IAttribute iAttr: attrMap.values()) {
			String attr_kind = "ctor_call";
			String syntax = m_stxCsv.get(indent, attr_kind, column);
			String desc = "";
			if (!iAttr.getDefinition().isEmpty()) {
				desc = fillComment(iAttr, true);
			}
			parameters += Utils.get(
				syntax,
				iAttr.getName(),
				getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(),
				iAttr.getContainer() != null ? ((INamedElement)iAttr.getContainer()).getName() : "",
				Utils.get(m_stxCsv.get("ctor_call", "begin"), iAttr.getName()),
				findMultiplicity(iAttr),
				desc
			);			
			column = "extnxt";			
		}
		return parameters;
	}
	
	/**
	 * printSuperCtorCallParams
	 * @throws IOException
	 * @throws Exception
	 */
	public String collectSuperCtorCallParams() throws IOException, Exception {
		AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser(m_iSuperClass);
		HashMap<String, IAttribute> superAttrMap = superAttrTraverser.getAttrMap();
		String params = "";
		
		String column = "ext1st";
		for (IAttribute iAttr: superAttrMap.values()) {
			String overridedInit = "";
			boolean bFound = false;
			for (IAttribute iMyAttr: m_iClass.getAttributes()) {
				overridedInit = findAttrInitValue(iMyAttr, m_language);
				if (iMyAttr.getName().equals(iAttr.getName()) && !overridedInit.trim().isEmpty()) {
					bFound = true;
					break;
				}
			}
			String value = Utils.get(m_stxCsv.get("ctor_call", "begin"), iAttr.getName());
			String attr_kind = "ctor_call";
			if (bFound) {
				value = Utils.get(m_stxCsv.get("ctor_call", "end"), overridedInit);
			}
			String syntax = m_stxCsv.get(indent, attr_kind, column);
			String desc = "";
			if (!iAttr.getDefinition().isEmpty()) {
				desc = fillComment(iAttr, true);
			}
			params += Utils.get(
				syntax,
				iAttr.getName(),
				getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(),
				iAttr.getContainer() != null ? ((INamedElement)iAttr.getContainer()).getName() : "",
				value,
				findMultiplicity(iAttr),
				desc
			);		
			column = "extnxt";			
		}
		return params;
	}
	
	/**
	 * printMemberInitializations
	 * @throws IOException
	 * @throws Exception
	 */
	public void printMemberInitializations() throws IOException, Exception {
		HashMap<String, IAttribute> superAttrMap;
		if (m_iSuperClass != null) {
			AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser(m_iSuperClass);
			superAttrMap = superAttrTraverser.getAttrMap();
		} else {
			superAttrMap = new HashMap<String, IAttribute>();
		}
		// Print members initialization
		boolean bFirstRound = true;
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			String propertyCode = findPropertyCode(iAttr, m_language);
			if (!iAttr.isStatic() && !iAttr.getName().isEmpty() && isNullOrEmpty(propertyCode)){
				String attr_kind = findAttrPath(iAttr);
				String syntax = m_stxCsv.get(indent, attr_kind, "end");
				if (m_iSuperClass == null && bFirstRound) {
					syntax = m_stxCsv.get(indent, attr_kind, "begin");
				}
				String superRef = "";
				String value = Utils.get(m_stxCsv.get("ctor_call", "begin"), iAttr.getName());
				if (!findAttrInitValue(iAttr, m_language).isEmpty()) {		// has initialization code
					if (superAttrMap.containsKey(iAttr.getName())) {		// not a inherited attribute
						continue;
					}
					value = Utils.get(m_stxCsv.get("ctor_call", "end"), findAttrInitValue(iAttr, m_language));
					syntax = m_stxCsv.get(indent, attr_kind, "end");				
					if (m_iSuperClass == null && bFirstRound) {
						syntax = m_stxCsv.get(indent, attr_kind, "begin");
					}
				} else {
					// use for construction declaration only, without real existence of attribute
					if (superAttrMap.containsKey(iAttr.getName())) {
						continue;
					}
				}
				String desc = "";
				if (!syntax.isEmpty()) {
					desc = fillComment(iAttr, true);
				}
				m_writer.write(
					Utils.get(
						syntax, 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(), 
						m_iClass.getName(),
						value,
						findMultiplicity(iAttr),
						desc,
						superRef
					)
				);		
			}
			bFirstRound = false;
		}
	}
	
	/**
	 * printStaticAttrDecls
	 * @param hasVtbl
	 * @param stmGen
	 * @throws IOException
	 * @throws Exception
	 */
	public void printStaticAttrDecls() throws IOException, Exception {
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			if (iAttr.isStatic()) {
				String attr_kind = findAttrPath(iAttr);
				String syntax = m_stxCsv.get(indent, attr_kind, "name");
				if (findAttrInitValue(iAttr, m_language).isEmpty()) {
					syntax = m_stxCsv.get(indent, attr_kind, "end");
				}
				if (!iAttr.isChangeable()) {
					syntax = m_stxCsv.get(indent, attr_kind, "ext1st");
				}
				String desc = "";
				if (!syntax.isEmpty()) {
					desc = fillComment(iAttr, true);
				}
				m_writer.write(
					Utils.get(
						syntax, 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(), 
						m_iClass.getName(),
						findAttrInitValue(iAttr, m_language),
						findMultiplicity(iAttr),
						desc,
						getVisibility(iAttr)
					)
				);										
			}
		}	
	}
	/**
	 * printMemberInitializations
	 * @throws IOException
	 * @throws Exception
	 */
	public void printAttrDeclarations(boolean hasVtbl, RStmGenerator stmGen) throws IOException, Exception {
		
		m_writer.write(
			Utils.get(m_stxCsv.get(indent, "attr_group", "name"), 
			m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : "",
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			"",
			"",
			m_iClass.getDefinition()
		));
		
		HashMap<String, IAttribute> superAttrMap;
		if (m_iSuperClass != null) {
			AttrDeepTraverser superAttrTraverser = new AttrDeepTraverser(m_iSuperClass);
			superAttrMap = superAttrTraverser.getAttrMap();
		} else {
			superAttrMap = new HashMap<String, IAttribute>();
		}
		
		if (m_iSuperClass == null) {
			if (hasVtbl) {
				m_writer.write(
					Utils.get(m_stxCsv.get(indent, "attr_group", "ext1st"), 
					m_iClass.getName(),
					m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
					m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
					"",
					"",
					m_iClass.getDefinition()
				));
			} else {
				m_writer.write(
					Utils.get(m_stxCsv.get(indent, "attr_group", "begin"), 
					m_iClass.getName(),
					m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
					m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
					"",
					"",
					m_iClass.getDefinition()
				));
			}
		} else {
			m_writer.write(
				Utils.get(m_stxCsv.get(indent, "attr_group", "extnxt"), 
				m_iClass.getName(),
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				"",
				"",
				m_iClass.getDefinition()
			));
		}
		
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			String propertyCode = findPropertyCode(iAttr, m_language);
			if (!iAttr.isStatic() && !iAttr.getName().isEmpty() && isNullOrEmpty(propertyCode) && !hasLangSpecPropStx(iAttr)){
				String attr_kind = findAttrPath(iAttr);
				String syntax = m_stxCsv.get(indent, attr_kind, "name");
				if (superAttrMap.containsKey(iAttr.getName())) {		// not a inherited attribute
					continue;
				}
				String desc = "";
				if (!syntax.isEmpty()) {
					desc = fillComment(iAttr, true);
				}
				m_writer.write(
					Utils.get(
						syntax, 
						iAttr.getName(), 
						getTypeLiteral(iAttr.getType()) + iAttr.getTypeModifier(), 
						m_iClass.getName(),
						findAttrInitValue(iAttr, m_language),
						findMultiplicity(iAttr),
						desc,
						getVisibility(iAttr)
					)
				);		
			}
		}

		if (stmGen != null) {
			stmGen.printMainStmDeclaration();
		}
		
		m_writer.write(
			Utils.get(m_stxCsv.get(indent, "attr_group", "end"), 
			m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
			"",
			"",
			m_iClass.getDefinition()
		));
	
	}
}
