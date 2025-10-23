package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.change_vision.jude.api.inf.model.IAttribute;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IGeneralization;
import com.change_vision.jude.api.inf.model.INamedElement;

public class TNestedClsGenerator extends TBaseGenerator {
	private ArrayList<IClass> m_nestedClasses = new ArrayList<IClass>();
	
	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TNestedClsGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
		for (IClass nestedClass: iClass.getNestedClasses()) {
			m_nestedClasses.add(nestedClass);
		}
		Collections.sort(m_nestedClasses,
			new Comparator<IClass>() {
				public int compare(IClass lhs, IClass rhs) {
					for (IGeneralization iGen: lhs.getGeneralizations()) {
						if (iGen.getSuperType() == rhs) {
							return 1;
						} else if (iGen.getSubType() == lhs) {
							return 1;
						}
					}
					for (IAttribute iAttr: lhs.getAttributes()) {
						if (!iAttr.getName().isEmpty() && iAttr.getType() == rhs) {
							return 1;
						}
					}
					for (IGeneralization iGen: rhs.getGeneralizations()) {
						if (iGen.getSuperType() == lhs) {
							return -1;
						} else if (iGen.getSubType() == rhs) {
							return -1;
						}
					}
					for (IAttribute iAttr: rhs.getAttributes()) {
						if (!iAttr.getName().isEmpty() && iAttr.getType() == lhs) {
							return -1;
						}
					}
					return 0;
				}
			}
		);
	}
	
	/**
	 * printNestedClasses
	 * @throws IOException
	 * @throws Exception
	 */
	public void printNestedClasses() throws IOException, Exception {
		for (IClass nestedClass: m_nestedClasses) {
			if (nestedClass.getStereotypes().length > 0) {
				String stereotype = nestedClass.getStereotypes()[0];
				if (nestedClass.isPublicVisibility()) {
					stereotype = "b_" + stereotype;
				} else if (nestedClass.isProtectedVisibility()) {
					stereotype = "c_" + stereotype;
				} else {
					stereotype = "i_" + stereotype;
				}
				
				// Find nested class's super class
				IClass nestedClassSuper = findSuperClass(nestedClass);
				
				// Print name
				String syntax = m_stxCsv.get(indent, stereotype, "name");
				if (nestedClassSuper != null) {
					syntax = m_stxCsv.get(indent, stereotype, "begin");
				}
				String desc = "";
				if (!syntax.isEmpty()) {
					desc = fillComment(nestedClass, false);
				}
				m_writer.write(Utils.get(
					syntax, 
					nestedClass.getName(), 
					nestedClassSuper != null ? nestedClassSuper.getName() : "", 
					m_iClass.getName(),
					"",
					stereotype,
					desc
				));
				// Print attributes
				indent++;
				String path = m_stxCsv.get(indent, stereotype, "ext1st");
				for (IAttribute iAttr: nestedClass.getAttributes()) {
					if (!iAttr.getName().isEmpty()) {
						desc = "";
						if (!path.isEmpty()) {
							desc = fillComment(iAttr, true);
						}
						m_writer.write(Utils.get(path, 
							iAttr.getName(), 
							iAttr.getType() + iAttr.getTypeModifier(), 
							((INamedElement)iAttr.getContainer()).getName(),
							findAttrInitValue(iAttr, m_language),
							findMultiplicity(iAttr),
							desc
						));
						path = m_stxCsv.get(indent, stereotype, "extnxt");
					}
				}				
				indent--;
				
				// Print end
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
