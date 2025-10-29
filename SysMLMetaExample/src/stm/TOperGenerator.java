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

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.OperationOwner;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.VisibilityKind;
import org.eclipse.uml2.uml.ParameterDirectionKind;

/**
 * TOperGenerator (UML2 version)
 */
public class TOperGenerator extends TBaseGenerator {
    // Map operations to their extracted code (if any)
    private static HashMap<Operation, String> m_operCode = new HashMap<Operation, String>();
    private boolean optimizedForExternCode = System.getenv("EXT_CODE_OPT").equalsIgnoreCase("y");

    /**
     * hasVFunc: check for virtual (non-static, non-leaf) operations
     */
    public boolean hasVFunc() {
        if (m_iClass instanceof OperationOwner) {
	        for (Operation operation : ((OperationOwner)m_iClass).getOwnedOperations()) {
	            if (!operation.isLeaf() && !operation.isStatic()) {
	                return true;
	            }
	        }
        }
        return false;
    }

    /**
     * Constructor
     * @param stxCsv
     * @param umlClass   the UML2 Class (replacing Astah IClass)
     * @param writer
     */
    public TOperGenerator(SyntaxCsv stxCsv, Classifier umlClass, Writer writer) {
        super(stxCsv, umlClass, writer);
        // UML2 constructor stub
    }

    /**
     * printVirtualTbl
     */
    public void printVirtualTbl() throws Exception {
        String vOpers = "";
        String path = "vptr_decl";
        if (m_iClass instanceof OperationOwner) {
	        for (Operation operation : ((OperationOwner)m_iClass).getOwnedOperations()) {
	            if (!operation.isLeaf() && !operation.isStatic()) {
	                String modifier = operation.isStatic() ? "static" : "";
	                path = operation.isAbstract() ? "vptr_decl" : "vptr_impl";
	                String syntax = m_stxCsv.get(indent, path, "begin");
	                String desc = "";
	                // Use operation.getName() and getType() for return type
	                vOpers += Utils.get(
	                    syntax,
	                    operation.getName(),
	                    getTypeLiteral((Classifier)operation.getType()), // return type literal
	                    m_iClass.getName(),
	                    "",
	                    modifier,
	                    desc
	                );
	                indent++;
	                boolean firstRound = true;
	                for (Parameter param : operation.getOwnedParameters()) {
	                    String paramDir;
	                    // Determine parameter direction using UML2 enum
	                    if (param.getDirection() == ParameterDirectionKind.OUT_LITERAL) {
	                        paramDir = "ext1st";
	                    } else if (param.getDirection() == ParameterDirectionKind.INOUT_LITERAL) {
	                        paramDir = "extnxt";
	                    } else {
	                        paramDir = "name";
	                    }
	                    vOpers += Utils.get(
	                        firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
	                        param.getName(),
	                        getTypeLiteral((Classifier)param.getType()),
	                        "",
	                        "",
	                        "", // UML2 Parameter has no typeModifier property
	                        // Use default value as alias/comment if present
	                        fillComment(param.getDefault(), true),
	                        m_stxCsv.get("param_dir", paramDir)
	                    );
	                    firstRound = false;
	                }
	                indent--;
	                vOpers += Utils.get(
	                    m_stxCsv.get(indent, path, "end"),
	                    operation.getName(),
	                    getTypeLiteral((Classifier)operation.getType()),
	                    m_iClass.getName(),
	                    "",
	                    modifier,
	                    desc
	                );
	            }
	        }
        }
        if (hasVFunc()) {
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
     * getOperationPath: determines path string based on modifiers
     * (static, abstract, visibility)
     */
    public static String getOperationPath(Operation operation) {
        String path = "";
        boolean overridable = false;
        
        if (operation.isStatic()) {
            path += "s";
        } else if (operation.isLeaf()) {
            path += "l";
        } else {
            overridable = true;
            path += "_";
        }
        
        if (operation.isAbstract()) {
            path += "a";
        } else {
            path += "_";
        }
        
        // Visibility: use VisibilityKind
        VisibilityKind vis = operation.getVisibility();
        if (vis == VisibilityKind.PACKAGE_LITERAL) {
            path += "c";
        } else if (vis == VisibilityKind.PRIVATE_LITERAL) {
            if (overridable) {
                System.out.println("[Error] Private operation " 
                    + operation.getName() 
                    + " must be static or leaf");
                System.exit(1);
            }
            path += "i";
        } else if (vis == VisibilityKind.PROTECTED_LITERAL) {
            path += "c";
        } else if (vis == VisibilityKind.PUBLIC_LITERAL) {
            path += "b";
        }
        
        path += "_oper";
        return path;
    }

    /**
     * printConcreteOpers
     */
    public void printConcreteOpers(boolean isPublic) throws IOException, Exception {
        if (m_iClass instanceof OperationOwner) {
	        for (Operation operation : ((OperationOwner)m_iClass).getOwnedOperations()) {
	            VisibilityKind vis = operation.getVisibility();
	            boolean isPriv = (vis == VisibilityKind.PRIVATE_LITERAL);
	            boolean isPub = (vis == VisibilityKind.PUBLIC_LITERAL);
	            // Match original logic: include public or private if isPublic==true, else protected/package
	            if (((isPub == isPublic) && !isPriv) || (isPublic && isPriv)
	                && (!operation.isAbstract() || (!operation.isLeaf() && !operation.isStatic()))
	            ) {
	                String modifier = operation.isStatic() ? "static" : "";
	                String path = getOperationPath(operation);
	                String syntax = m_stxCsv.get(indent, path, "name");
	                String desc = "";
	                if (!syntax.isEmpty() && isCodeFile()) {
	                    desc = fillComment(operation, false);
	                }
	                m_writer.write(
	                    Utils.get(
	                        syntax,
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        (!operation.isAbstract() && !operation.isLeaf() && !operation.isStatic()) ? m_iAncestor.getName() : "",
	                        modifier,
	                        desc,
	                        // Append visibility string
	                        operation.getVisibility().toString()
	                    )
	                );
	                
	                indent++;
	                syntax = m_stxCsv.get(indent, path, "ext1st");
	                for (Parameter param : operation.getOwnedParameters()) {
	                    String paramDir;
	                    if (param.getDirection() == ParameterDirectionKind.OUT_LITERAL) {
	                        paramDir = "ext1st";
	                    } else if (param.getDirection() == ParameterDirectionKind.INOUT_LITERAL) {
	                        paramDir = "extnxt";
	                    } else {
	                        paramDir = "name";
	                    }
	                    m_writer.write(
	                        Utils.get(
	                            syntax,
	                            param.getName(),
	                            getTypeLiteral((Classifier)param.getType()),
	                            "",
	                            "",
	                            "", // no typeModifier
	                            fillComment(param.getDefault(), true),
	                            m_stxCsv.get("param_dir", paramDir)
	                        )
	                    );
	                    syntax = m_stxCsv.get(indent, path, "extnxt");
	                }
	                indent--;
	                
	                m_writer.write(
	                    Utils.get(
	                        m_stxCsv.get(indent, path, "begin"),
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        "", // optional ancestor
	                        modifier,
	                        desc
	                    )
	                );
	                // Virtual pointer call (C-specific)
	                if (operation.isAbstract() && !m_stxCsv.get(indent, "vptr_call", "name").isEmpty()) {
	                    if (isStandardType((Classifier)operation.getType())
	                    	&& operation.getType() != null
	                        && operation.getType().getName().equalsIgnoreCase("void") 
	                        && modifier.isEmpty()) {
	                        m_writer.write(
	                            Utils.get(
	                                m_stxCsv.get(indent, "vptr_call", "begin"),
	                                operation.getName(),
	                                getTypeLiteral((Classifier)operation.getType()),
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
	                                operation.getName(),
	                                getTypeLiteral((Classifier)operation.getType()),
	                                m_iClass.getName(),
	                                "",
	                                modifier,
	                                desc
	                            )
	                        );
	                    }
	                    syntax = m_stxCsv.get(indent, "vptr_call", "ext1st");
	                    for (Parameter param : operation.getOwnedParameters()) {
	                        String paramDir;
	                        if (param.getDirection() == ParameterDirectionKind.OUT_LITERAL) {
	                            paramDir = "ext1st";
	                        } else if (param.getDirection() == ParameterDirectionKind.INOUT_LITERAL) {
	                            paramDir = "extnxt";
	                        } else {
	                            paramDir = "name";
	                        }
	                        m_writer.write(
	                            Utils.get(
	                                syntax,
	                                param.getName(),
	                                getTypeLiteral((Classifier)param.getType()),
	                                "",
	                                "",
	                                "",
	                                "", // no alias
	                                m_stxCsv.get("param_dir", paramDir)
	                            )
	                        );
	                        syntax = m_stxCsv.get(indent, "vptr_call", "extnxt");
	                    }
	                    m_writer.write(
	                        Utils.get(
	                            m_stxCsv.get(indent, "vptr_call", "end"),
	                            operation.getName(),
	                            getTypeLiteral((Classifier)operation.getType()),
	                            m_iClass.getName(),
	                            "",
	                            modifier,
	                            desc
	                        )
	                    );
	                } else {
	                    // Print user-provided body if present
	                    String theCode = m_operCode.get(operation);
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
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        "",
	                        modifier,
	                        desc
	                    )
	                );
	            }
	        }
        }
    }
    
    /**
     * printFreeFuncPrototypes
     */
    public void printFreeFuncPrototypes() throws IOException, Exception {
        if (m_iClass instanceof OperationOwner) {
	        for (Operation operation : ((OperationOwner)m_iClass).getOwnedOperations()) {
	            if (operation.isAbstract() && (operation.isLeaf() || operation.isStatic())) {
	                String modifier = operation.isStatic() ? "static" : "";
	                String path = getOperationPath(operation);
	                String syntax = m_stxCsv.get(indent, path, "name");
	                String desc = "";
	                m_writer.write(
	                    Utils.get(
	                        syntax,
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        "",
	                        modifier,
	                        desc,
	                        operation.getVisibility().toString()
	                    )
	                );
	                
	                indent++;
	                boolean firstRound = true;
	                for (Parameter param : operation.getOwnedParameters()) {
	                    String paramDir;
	                    if (param.getDirection() == ParameterDirectionKind.OUT_LITERAL) {
	                        paramDir = "ext1st";
	                    } else if (param.getDirection() == ParameterDirectionKind.INOUT_LITERAL) {
	                        paramDir = "extnxt";
	                    } else {
	                        paramDir = "name";
	                    }
	                    m_writer.write(
	                        Utils.get(
	                            firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
	                            param.getName(),
	                            getTypeLiteral((Classifier)param.getType()),
	                            "",
	                            "",
	                            "", // no typeModifier
	                            fillComment(param.getDefault(), true),
	                            m_stxCsv.get("param_dir", paramDir)
	                        )
	                    );
	                    firstRound = false;
	                }
	                indent--;
	                
	                m_writer.write(
	                    Utils.get(
	                        m_stxCsv.get(indent, path, "begin"),
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        findOperConstraintCode(operation, m_language),
	                        modifier,
	                        desc
	                    )
	                );
	                m_writer.write(
	                    Utils.get(
	                        m_stxCsv.get(indent, path, "end"),
	                        operation.getName(),
	                        getTypeLiteral((Classifier)operation.getType()),
	                        m_iClass.getName(),
	                        "",
	                        modifier,
	                        desc
	                    )
	                );
	            }
	        }
        }
    }

    /**
     * parseCode: read user-written code from file and attach to operations
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
                readContent += (char)aChar;
            }
            readContent = readContent.replaceAll("\\r?\\n", System.getProperty("line.separator"));

            // Begin transaction (not needed for UML2; omitted or implement as needed)
            // TransactionManager.beginTransaction();
            if (m_iClass instanceof OperationOwner) {
	            for (Operation operation : ((OperationOwner)m_iClass).getOwnedOperations()) {
	                String modifier = operation.isStatic() ? "static" : "";
	                String path = getOperationPath(operation);
	                String syntax = m_stxCsv.get(indent, path, "name");
	                String desc = "";
	                String operationBegin = Utils.get(
	                    syntax,
	                    operation.getName(),
	                    getTypeLiteral((Classifier)operation.getType()),
	                    m_iClass.getName(),
	                    (!operation.isAbstract() && !operation.isLeaf() && !operation.isStatic()) ? m_iAncestor.getName() : "",
	                    modifier,
	                    desc,
	                    operation.getVisibility().toString()
	                );
	                
	                boolean firstRound = true;
	                for (Parameter param : operation.getOwnedParameters()) {
	                    String paramDir;
	                    if (param.getDirection() == ParameterDirectionKind.OUT_LITERAL) {
	                        paramDir = "ext1st";
	                    } else if (param.getDirection() == ParameterDirectionKind.INOUT_LITERAL) {
	                        paramDir = "extnxt";
	                    } else {
	                        paramDir = "name";
	                    }
	                    operationBegin += Utils.get(
	                        firstRound ? m_stxCsv.get(indent, path, "ext1st") : m_stxCsv.get(indent, path, "extnxt"),
	                        param.getName(),
	                        getTypeLiteral((Classifier)param.getType()),
	                        "",
	                        "",
	                        "",
	                        fillComment(param.getDefault(), true),
	                        m_stxCsv.get("param_dir", paramDir)
	                    );
	                    firstRound = false;
	                }
	                
	                operationBegin += Utils.get(
	                    m_stxCsv.get(indent, path, "begin"),
	                    operation.getName(),
	                    getTypeLiteral((Classifier)operation.getType()),
	                    m_iClass.getName(),
	                    findOperConstraintCode(operation, m_language),
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
	                operationBegin = operationBegin.replaceAll("\\\\s\\*\\z", lastWhitespaces);     // trim right
	                operationBegin = operationBegin.replaceAll("\\\\s\\+\\z", lastWhitespaces);     // trim right
	                
	                String operationEnd = Utils.get(
	                    m_stxCsv.get(indent, path, "end"),
	                    operation.getName(),
	                    getTypeLiteral((Classifier)operation.getType()),
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
	                        System.out.println("Cannot parse method: " + operation.getName());
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
	                        m_operCode.put(operation, Utils.trimEnd(excerpt));
	                    }
	                }
	            }
	            // End transaction (not needed for UML2)
	            // TransactionManager.endTransaction();
	        }
        }
    }
}
