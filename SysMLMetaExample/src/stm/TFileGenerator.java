package stm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;

import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Dependency;
import org.eclipse.uml2.uml.EncapsulatedClassifier;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Realization;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.uml2.uml.Type;

public class TFileGenerator extends TBaseGenerator {
    private File file;
    private File inputFile;
    private ArrayList<String> m_doneList = new ArrayList<String>();
    private ArrayList<Class> m_doneAttr = new ArrayList<Class>();
    
    public TFileGenerator(SyntaxCsv stxCsv, Classifier m_iClass) throws Exception {
        super(stxCsv, m_iClass, null);
        // Construct output path (using qualified name for namespace path)
        String qualifiedName = getFullNamespace(m_iClass).replace("::", "/");
        String outputPath = System.getenv("OUTPUT") + "/" +
            Utils.get(m_stxCsv.get("file", "name"),
                m_iClass.getName(),
                "",
                qualifiedName,
                "",
                "",
                ""
            );
        file = new File(outputPath);
        File directoryPath = file.getParentFile();
        if (!directoryPath.exists()) {
            directoryPath.mkdirs();
        }
        String inputPathEnv = System.getenv("INPUT");
        if (inputPathEnv != null && !inputPathEnv.isEmpty()) {
            String inputPath = inputPathEnv + "/" + 
                Utils.get(m_stxCsv.get("file", "name"),
                    m_iClass.getName(),
                    "",
                    qualifiedName,
                    "",
                    "",
                    ""
                );
            inputFile = new File(inputPath);
            directoryPath = inputFile.getParentFile();
            if (!directoryPath.exists()) {
                directoryPath.mkdirs();
            }
        } else {
            inputFile = file;
        }
    }
    
    /**
     * getInputFile
     */
    public File getInputFile() {
        return inputFile;
    }
    
    /**
     * openFile
     */
    public Writer openFile() throws Exception {
        String encoding = System.getenv("ENCODING");
        System.out.println("Encoding " + encoding);
        if (encoding != null && !encoding.isEmpty()) {
            m_writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
        } else {
            m_writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            // Write BOM for UTF-8
            m_writer.write('\ufeff');
        }
        return m_writer;
    }
    
    /**
     * printHeader
     */
    public void printHeader() throws IOException, Exception {
        // Use qualified name with package separator for namespace
        String ns = m_iClass.getQualifiedName().replace("::", m_pkgPathSeparator);
        m_writer.write(
            Utils.get(m_stxCsv.get("file", "begin"),
                m_iClass.getName(),
                "",
                ns,
                "",
                "",
                ""
            )
        );
    }
    
    public void printFooter() throws IOException, Exception {
        m_writer.write(Utils.get(m_stxCsv.get("file", "end"), m_iClass.getName()));
    }

    public void closeFile() throws IOException {
        if (m_writer != null) {
            m_writer.flush();
            m_writer.close();
            m_writer = null;
        }
    }    
    
    /**
     * findOwner
     * If the classifier is nested inside another Class, return the outer Class.
     */
    private Class findOwner(Class theType) {
        // UML2: use getOwner() (returns Element) and check if it's a Class
        if (theType.getOwner() instanceof Class) {
            return (Class) theType.getOwner();
        }
        return theType;
    }
    
    /**
     * printInclude
     */
    private void printInclude(String path, Class theType, NamedElement descElem) throws IOException, Exception {
        // Prepare description if any owned comment exists (skipped here)
        String desc = "";
        // Build namespace path (qualified namespace of theType)
        String namespacePath = "";
        if (theType.getNamespace() != null) {
            String qn = ((NamedElement)theType.getNamespace()).getQualifiedName();
            namespacePath = qn.replace("::", m_pkgPathSeparator);
        }
        String includeContent = Utils.get(path,
                theType.getName(),
                "",
                namespacePath,
                descElem.getName(),
                "",
                desc,
                m_iClass.getName()
        );
        if (!m_doneList.contains(includeContent) && !m_doneAttr.contains(theType)) {
            m_writer.write(includeContent);
            m_doneList.add(includeContent);
            m_doneAttr.add(theType);
        }
    }
    
    /**
     * printIncludes (for external/internal type includes)
     */
    public void printIncludes(StateMachine iMainStm) throws IOException, Exception {
        m_doneList.clear();
        m_doneAttr.clear();
        
        /////////////////////////// internal includes ///////////////////////////
        String impDepStx = m_stxCsv.get("file", "ext1st");
        // print superClass include if exists
        if (m_iSuperClass != null && m_iSuperClass instanceof Class) {
            Class theType = findOwner((Class)m_iSuperClass);
            Classifier iGen = findGeneralization(m_iClass);
            if (iGen == null) {
                // If no Astah generalization method, we skip or handle differently
                iGen = m_iSuperClass;
            }
            printInclude(impDepStx, theType, iGen);
        }               
        
        // print realized interfaces (client Realizations)
        for (Dependency dep : m_iClass.getClientDependencies()) {
            if (dep instanceof Realization) {
                Realization umlReal = (Realization) dep;
                for (NamedElement supplier : umlReal.getSuppliers()) {
                    if (supplier instanceof Class) {
                        Class theType = findOwner((Class) supplier);
                        printInclude(impDepStx, theType, supplier);
                    }
                }
            }
        }
                
        // print external types include (non-primitive attributes of this class)
        if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property iAttr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            Type type = iAttr.getType();
	            if (type instanceof Class) {
	                Class theType = findOwner((Class) type);
	                // skip primitive types
	                if (!(type instanceof PrimitiveType)
	                        && theType != m_iClass
	                        && !iAttr.getName().isEmpty()
	                        && type != m_iSuperClass) {
	                    // omitted: stereotype checks
	                    printInclude(impDepStx, theType, iAttr);
	                }
	            }
	        }
        }
        
        /////////////////////////// limited include ///////////////////////////
        String useDepStx = m_stxCsv.get("file", "ext1st");
        String callDepStx = m_stxCsv.get("file", "extnxt");
        // print external types include (second pass)
        if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property iAttr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            Type type = iAttr.getType();
	            if (type instanceof Class) {
	                Class theType = findOwner((Class) type);
	                if (!(type instanceof PrimitiveType)
	                        && theType != m_iClass
	                        && !iAttr.getName().isEmpty()
	                        && type != m_iSuperClass) {
	                    printInclude(useDepStx, theType, iAttr);
	                }
	            }
	        }               
        }
        
        // print dependencies include (other client dependencies)
        for (Dependency iDependency : m_iClass.getClientDependencies()) {
            for (NamedElement supplier : iDependency.getSuppliers()) {
                if (supplier instanceof Class) {
                    Class iSupplier = (Class) supplier;
                    Class theType = findOwner(iSupplier);
                    if (!(iSupplier instanceof PrimitiveType)
                            && iSupplier != m_iSuperClass
                            && theType != m_iClass) {
                        String path = callDepStx;
                        printInclude(path, theType, iDependency);
                    }
                }
            }
        }
        
        // print state-machine include (if coding state machines)
        if (iMainStm != null && isCodeFile()) {
            // Use similar template for state machine name
            String ns = m_iClass.getQualifiedName().replace("::", m_pkgPathSeparator);
            m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "name"),
                    m_iClass.getName(),
                    m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                    ns,
                    "",
                    "",
                    ""
            ));
        }            
    }

    /**
     * printFriendIncludes
     */
    public void printFriendsDecl() throws IOException, Exception {
        m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "ext1st"), m_iClass.getName()));
        m_doneList.clear();
        m_doneAttr.clear();
        String path = m_stxCsv.get(indent, "friend", "extnxt");
        // treat 'friend' dependencies similarly (omitting stereotype logic)
        for (Dependency iDependency : m_iClass.getClientDependencies()) {
            for (NamedElement supplier : iDependency.getSuppliers()) {
                if (supplier instanceof Class) {
                    Class iSupplier = (Class) supplier;
                    Class theType = findOwner(iSupplier);
                    if (!(iSupplier instanceof PrimitiveType)
                            && iSupplier != m_iSuperClass
                            && theType != m_iClass) {
                        printInclude(path, theType, iDependency);
                    }
                }
            }
        }
        m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "end"), m_iClass.getName()));
    }

    /**
     * printInternalIncludes
     */
    public void printInternalIncludes(StateMachine iMainStm) throws IOException, Exception {
        m_doneList.clear();
        m_doneAttr.clear();
        String impDepStx = m_stxCsv.get("friend", "name");
        String impRefStx = m_stxCsv.get("friend", "begin");
        // print superClass include
        if (m_iSuperClass != null && m_iSuperClass instanceof Class) {
            Class theType = findOwner((Class)m_iSuperClass);
            Classifier iGen = findGeneralization(m_iClass);
            if (iGen == null) {
                iGen = m_iSuperClass;
            }
            printInclude(impDepStx, theType, iGen);
        }
                
        // print realized interfaces
        for (Dependency dep : m_iClass.getClientDependencies()) {
            if (dep instanceof Realization) {
                Realization umlReal = (Realization) dep;
                for (NamedElement supplier : umlReal.getSuppliers()) {
                    if (supplier instanceof Class) {
                        Class theType = findOwner((Class) supplier);
                        printInclude(impDepStx, theType, supplier);
                    }
                }
            }
        }               
                
        // print external attributes include (both object and reference kinds)
        if (m_iClass instanceof EncapsulatedClassifier) {
	        for (Property iAttr : ((EncapsulatedClassifier)m_iClass).getOwnedAttributes()) {
	            Type type = iAttr.getType();
	            if (type instanceof Class) {
	                Class theType = findOwner((Class) type);
	                if (!(type instanceof PrimitiveType)
	                        && theType != m_iClass
	                        && !iAttr.getName().isEmpty()
	                        && type != m_iSuperClass) {
	                    // print for object kind
	                    printInclude(impDepStx, theType, iAttr);
	                    // print for reference kind
	                    printInclude(impRefStx, theType, iAttr);
	                }
	            }
	        }
        }
        // print state-machine include (non-code state machine)
        if (iMainStm != null && !isCodeFile()) {
            String ns = m_iClass.getQualifiedName().replace("::", m_pkgPathSeparator);
            m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "name"),
                    m_iClass.getName(),
                    m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
                    ns,
                    "",
                    "",
                    ""
            ));
        }
    }
}
