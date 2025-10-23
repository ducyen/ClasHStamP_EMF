package stm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.change_vision.jude.api.inf.editor.TransactionManager;
import com.change_vision.jude.api.inf.exception.InvalidEditingException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IOperation;
import com.change_vision.jude.api.inf.model.IParameter;

/**
 * TOperGenerator
 * @author 3140327
 *
 */
public class TOperGenerator extends TBaseGenerator {
	private static HashMap<IOperation, String> m_iOperCode = new HashMap<IOperation, String>(); 
	private boolean optimizedForExternCode = System.getenv("EXT_CODE_OPT").equalsIgnoreCase("y");
	/**
	 * isVtblExisted
	 * @return
	 */
	public boolean hasVFunc() {
		for (IOperation iOper: m_iClass.getOperations()) {
			if (!iOper.isLeaf() && !iOper.isStatic()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Constructor
	 * @param stxCsv
	 * @param iClass
	 * @param writer
	 */
	public TOperGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) {
		super(stxCsv, iClass, writer);
		// TODO Auto-generated constructor stub
	}

	/**
	 * printVirtualTbl
	 * @throws Exception
	 */
	public void printVirtualTbl() throws Exception {
		String vOpers = "";
		String path = "vptr_decl";
		for (IOperation iOper: m_iClass.getOperations()) {
			if (!iOper.isLeaf() && !iOper.isStatic()) {
				String modifier = iOper.getTypeModifier();
				path = iOper.isAbstract() ? "vptr_decl" : "vptr_impl";
				String syntax = m_stxCsv.get(indent, path, "begin");
				String desc = "";
				vOpers += Utils.get(
					syntax,
					iOper.getName(),
					getTypeLiteral(iOper.getReturnType()),
					m_iClass.getName(),
					"",
					modifier,
					desc
				);
				
				indent++;
				boolean firstRound = true;
				for (IParameter param : iOper.getParameters()) {
					String paramDir = param.getDirection().equals("out") ? "ext1st" :
						param.getDirection().equals("inout") ? "extnxt" : "name";
					vOpers += Utils.get(
						firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
						param.getName(),
						getTypeLiteral(param.getType()),
						"",
						"",
						param.getTypeModifier(),
						fillComment(param.getAlias1().trim(), true),
						m_stxCsv.get("param_dir", paramDir)
					);
					firstRound = false;
				}
				indent--;
				
				vOpers += Utils.get(
					m_stxCsv.get(indent, path, "end"),
					iOper.getName(),
					getTypeLiteral(iOper.getReturnType()),
					m_iClass.getName(),
					"",
					modifier,
					desc
				);
			}
		}
		if (hasVFunc()){
			m_writer.write(
				Utils.get(
					m_stxCsv.get(indent, path, "name"),
					vOpers,
					m_iClass.getName(),
					m_iAncestor != null ? m_iAncestor.getName() : m_iClass.getName()
				)
			);
		}	
	}
	
	/**
	 * getOperationPath
	 * @param iOper
	 * @return
	 * static  abstract   leaf   function
	 *   -        -         -    virtual function
	 *   -        -         o    normal function
	 *   -        o         -    abstract function
	 *   -        o         o    normal function prototype
	 *   o        -         -    ---ERROR---
	 *   o        -         o    free function
	 *   o        o         -    ---ERROR---
	 *   o        o         o    free function prototype
	 */
	public static String getOperationPath(IOperation iOper){
		String path = "";
		boolean overridable = false;
		
		if (iOper.isStatic()) {
			path += "s";
		} else if (iOper.isLeaf()) {
			path += "l";
		} else {
			overridable = true;
			path += "_";
		}
		
		if (iOper.isAbstract()) {
			path += "a";
		} else {
			path += "_";
		}
		
		if (iOper.isPackageVisibility()) {
			path += "c";
		} else if (iOper.isPrivateVisibility()) {
			if (overridable) {
				System.out.println("[Error] Private operation " + iOper.getName() + " must be static or leaf");
				System.exit(1);
			}
			path += "i";
		} else if (iOper.isProtectedVisibility()) {
			path += "c";
		} else if (iOper.isPublicVisibility()) {
			path += "b";
		}
		
		path += "_oper";
		
		return path;
	}

	/**
	 * printConcreteOpers
	 * @throws IOException
	 * @throws Exception
	 */
	public void printConcreteOpers(boolean isPublic) throws IOException, Exception {
		for (IOperation iOper: m_iClass.getOperations()) {
			if ((iOper.isPublicVisibility() == isPublic && !iOper.isPrivateVisibility() || isPublic && iOper.isPrivateVisibility()) && 
				(!iOper.isAbstract() || (!iOper.isLeaf() && !iOper.isStatic()))
			) {
				String modifier = iOper.getTypeModifier();
				String path = getOperationPath(iOper);
				String syntax = m_stxCsv.get(indent, path, "name");
				String desc = "";
				if (!syntax.isEmpty() && isCodeFile()) {
					desc = fillComment(iOper, false);
				}
				m_writer.write(
					Utils.get(
						syntax,
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						( !iOper.isAbstract() && !iOper.isLeaf() && !iOper.isStatic() ) ? m_iAncestor.getName() : "",				/* value */
						modifier,
						desc,
						getVisibility(iOper)
					)
				);
				
				indent++;
				syntax = m_stxCsv.get(indent, path, "ext1st");
				for (IParameter param : iOper.getParameters()) {
					String paramDir = param.getDirection().equals("out") ? "ext1st" :
						param.getDirection().equals("inout") ? "extnxt" : "name";
					m_writer.write(
						Utils.get(
							syntax,
							param.getName(),
							getTypeLiteral(param.getType()),
							"",
							"",
							param.getTypeModifier(),
							fillComment(param.getAlias1().trim(), true),
							m_stxCsv.get("param_dir", paramDir)
						)
					);
					syntax = m_stxCsv.get(indent, path, "extnxt");
				}
				indent--;
				
				m_writer.write(
					Utils.get(
						m_stxCsv.get(indent, path, "begin"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						findOperConstraintCode(iOper, m_language)/*( !iOper.isAbstract() && !iOper.isLeaf() && !iOper.isStatic() ) ? m_iAncestor.getName() : ""*/,
						modifier,
						desc
					)
				);
				// Forward call to virtual pointer if necessary (C-specific)
				if (iOper.isAbstract() && !m_stxCsv.get(indent, "vptr_call", "name").isEmpty()) {
					if (isStandardType(iOper.getReturnType()) && iOper.getReturnType().getName().equalsIgnoreCase("void") && iOper.getTypeModifier().isEmpty()) {
						m_writer.write(
							Utils.get(
								m_stxCsv.get(indent, "vptr_call", "begin"),
								iOper.getName(),
								getTypeLiteral(iOper.getReturnType()),
								m_iClass.getName(),
								"",
								modifier,
								desc
							)
						);
					} else {
						m_writer.write(
							Utils.get(
								m_stxCsv.get(indent, "vptr_call", "name"),
								iOper.getName(),
								getTypeLiteral(iOper.getReturnType()),
								m_iClass.getName(),
								"",
								modifier,
								desc
							)
						);
					}
					syntax = m_stxCsv.get(indent, "vptr_call", "ext1st");
					for (IParameter param : iOper.getParameters()) {
						String paramDir = param.getDirection().equals("out") ? "ext1st" :
							param.getDirection().equals("inout") ? "extnxt" : "name";
						m_writer.write(
							Utils.get(
								syntax,
								param.getName(),
								getTypeLiteral(param.getType()),
								"",
								"",
								param.getTypeModifier(),
								"",
								m_stxCsv.get("param_dir", paramDir)
							)
						);
						syntax = m_stxCsv.get(indent, "vptr_call", "extnxt");
					}
					m_writer.write(
						Utils.get(
							m_stxCsv.get(indent, "vptr_call", "end"),
							iOper.getName(),
							getTypeLiteral(iOper.getReturnType()),
							m_iClass.getName(),
							"",
							modifier,
							desc
						)
					);
				} else {
					// Print content
					String theCode = m_iOperCode.get(iOper);
					if (theCode != null) {
						if (!optimizedForExternCode) {
							for (String line : theCode.split("\\r?\\n")) {
								if (!line.isEmpty() && isCodeFile()) {
									m_writer.write(Utils.get(m_stxCsv.get(indent + 1, "action", "extnxt"), line));
								}
							}
						} else if (isCodeFile()) {
							m_writer.write(theCode);
						}
					}
				}
				m_writer.write(
					Utils.get(
						m_stxCsv.get(indent, path, "end"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						"",
						modifier,
						desc
					)
				);
			}
		}		
	}
	
	/**
	 * printFreeFuncPrototypes
	 * @throws IOException
	 * @throws Exception
	 */
	public void printFreeFuncPrototypes() throws IOException, Exception {
		for (IOperation iOper: m_iClass.getOperations()) {
			if (iOper.isAbstract() && (iOper.isLeaf() || iOper.isStatic())) {
				String modifier = iOper.getTypeModifier();
				String path = getOperationPath(iOper);
				String syntax = m_stxCsv.get(indent, path, "name");
				String desc = "";
				//if (!syntax.isEmpty()) {
				//	desc = fillComment(iOper, false);
				//}
				m_writer.write(
					Utils.get(
						syntax,
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						"",
						modifier,
						desc,
						getVisibility(iOper)
					)
				);
				
				indent++;
				boolean firstRound = true;
				for (IParameter param : iOper.getParameters()) {
					String paramDir = param.getDirection().equals("out") ? "ext1st" :
						param.getDirection().equals("inout") ? "extnxt" : "name";
					m_writer.write(
						Utils.get(
							firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
							param.getName(),
							getTypeLiteral(param.getType()),
							"",
							"",
							param.getTypeModifier(),
							fillComment(param.getAlias1().trim(), true),
							m_stxCsv.get("param_dir", paramDir)
						)
					);
					firstRound = false;
				}
				indent--;
				
				m_writer.write(
					Utils.get(
						m_stxCsv.get(indent, path, "begin"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						findOperConstraintCode(iOper, m_language),
						modifier,
						desc
					)
				);
				m_writer.write(
					Utils.get(
						m_stxCsv.get(indent, path, "end"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						"",
						modifier,
						desc
					)
				);
				
			}
		}		
	}
	
	/**
	 * parseCode
	 * @throws Exception 
	 */
	public void parseCode(File file) throws Exception {
		Reader reader = null;
		try {
			String encoding = System.getenv("ENCODING");
			if (encoding != null && !encoding.isEmpty()) {
				reader = new InputStreamReader(new FileInputStream(file), encoding);
			} else {
				reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
			}
		} catch (IOException e) {
			reader = null;
		}
		int indent = 1;
		if (reader != null && isCodeFile()) {
			System.out.println("Parsing code from file");
			int aChar;
			String readContent = "";
			while ((aChar = reader.read()) != -1) {
				// convert to char and display it
				readContent += (char)aChar;
			}
			readContent = readContent.replaceAll("\\r?\\n", System.getProperty("line.separator"));
			//System.out.println(content);
			// Find if a function existed
			// Reader concrete operations
            // Begin transaction when creating or editing models
            TransactionManager.beginTransaction();
			for (IOperation iOper: m_iClass.getOperations()) {
				//if (!iOper.isAbstract()) {
					String modifier = iOper.getTypeModifier();
					String path = getOperationPath(iOper);
					String syntax = m_stxCsv.get(indent, path, "name");
					String desc = "";
					String operationBegin = Utils.get(
						syntax,
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						( !iOper.isAbstract() && !iOper.isLeaf() && !iOper.isStatic() ) ? m_iAncestor.getName() : "",				/* value */
						modifier,
						desc,
						getVisibility(iOper)
					);
					
					boolean firstRound = true;
					for (IParameter param : iOper.getParameters()) {
						String paramDir = param.getDirection().equals("out") ? "ext1st" : param.getDirection().equals("inout") ? "extnxt" : "name";
						operationBegin += Utils.get(
							firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
							param.getName(),
							getTypeLiteral(param.getType()),
							"",
							"",
							param.getTypeModifier(),
							fillComment(param.getAlias1(), true),
							m_stxCsv.get(indent, "param_dir", paramDir)
						);
						firstRound = false;
					}
					
					operationBegin += Utils.get(
						m_stxCsv.get(indent, path, "begin"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						findOperConstraintCode(iOper, m_language)/*( !iOper.isAbstract() && !iOper.isLeaf() && !iOper.isStatic() ) ? m_iAncestor.getName() : ""*/,
						modifier,
						desc
					);
					Matcher matcher;
					matcher = Pattern.compile("(\\s*)\\z").matcher(operationBegin);
					String lastWhitespaces = "";
					if (matcher.find()) {
						lastWhitespaces = operationBegin.substring(matcher.start(), matcher.end());
					}
					operationBegin = operationBegin.replaceAll("\\s*([\\p{Punct}^{}])\\s*", "\\\\s*\\\\$1\\\\s*");
					operationBegin = operationBegin.replaceAll("\\s+", "\\\\s+");
					operationBegin = operationBegin.replaceAll("\\\\s\\*\\z", lastWhitespaces);	// trim right
					operationBegin = operationBegin.replaceAll("\\\\s\\+\\z", lastWhitespaces);	// trim right
					String operationEnd = Utils.get(
						m_stxCsv.get(indent, path, "end"),
						iOper.getName(),
						getTypeLiteral(iOper.getReturnType()),
						m_iClass.getName(),
						"",
						modifier,
						desc
					);
					matcher = Pattern.compile("\\A(\\s*)").matcher(operationEnd);
					String firstWhitespaces = "";
					if (matcher.find()) {
						firstWhitespaces = operationEnd.substring(matcher.start(), matcher.end());
					}
					operationEnd = operationEnd.replaceAll("\\s*([\\p{Punct}^{}])\\s*", "\\\\s*\\\\$1\\\\s*");
					operationEnd = operationEnd.replaceAll("\\s+", "\\\\s+");
					operationEnd = operationEnd.replaceAll("\\A\\\\s\\*", firstWhitespaces); // trim left
					operationEnd = operationEnd.replaceAll("\\A\\\\s\\+", firstWhitespaces); // trim left
					matcher = Pattern.compile(operationBegin).matcher(readContent);
					
					if (matcher.find()) {
						int beginIndex = matcher.end();
						matcher = Pattern.compile(operationEnd).matcher(readContent);
						if (!matcher.find(beginIndex)) {
							System.out.println("************************ ERROR *************************");
							System.out.println("Cannot parse method: " + iOper);
							System.out.println("********************************************************");
							System.exit(1);
						}
						int endIndex = matcher.start();
						String excerpt = readContent.substring(beginIndex, endIndex);
						if (!optimizedForExternCode) {
							int indentSpaceNum = Integer.MAX_VALUE;
							String[] lines = excerpt.split("\\r?\\n");
							for (String line : lines) {
								if (!line.isEmpty()) {
									String ltrim = line.replaceAll("^[\\t|\\s]+","");
									if (indentSpaceNum > line.length() - ltrim.length()) {
										indentSpaceNum = line.length() - ltrim.length();
									}
								}
							}
							excerpt = "";
							for (String line : lines) {
								if (!line.isEmpty()) {
									String ltrim = line.replaceFirst("[\\t|\\s]{" + indentSpaceNum + "}","");
									excerpt += (ltrim + "\n");
								}
							}
						}
						if (!excerpt.isEmpty()) {
							m_iOperCode.put(iOper, Utils.trimEnd(excerpt));
						}
					}
				//}
			}
            // End transaction
            TransactionManager.endTransaction();				
		}
	}
}
