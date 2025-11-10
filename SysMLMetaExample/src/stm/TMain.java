/**
 * 
 */
package stm;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.StateMachine;
import org.eclipse.gmf.runtime.notation.NotationPackage;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.papyrus.infra.gmfdiag.style.StylePackage;
import org.eclipse.papyrus.infra.gmfdiag.style.impl.StylePackageImpl;

import rfc.RStmGenerator;

/**
 * @author duc
 * 
 */
public class TMain {
	public static Resource notationResource;
	
    private static int getMajorJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dotIndex = version.indexOf(".");
            version = dotIndex != -1 ? version.substring(0, dotIndex) : version;
        }
        return Integer.parseInt(version);
    }

    // Helper method to get full package name with "/" separators.
    /*
    private static String getFullName(Package pkg) {
        String name = pkg.getName();
        String fullName = (name != null ? name : "");
        Package parent = pkg.getNestingPackage();
        while (parent != null) {
            String parentName = parent.getName();
            if (parentName != null && !parentName.isEmpty()) {
                fullName = parentName + "/" + fullName;
            }
            parent = parent.getNestingPackage();
        }
        return fullName;
    }
    */

    // Find a package in the model by its full name.
    private static Package findPackage(Package pkg, String fullName) {
    	System.out.println("Checking " + pkg.getQualifiedName());
        if (pkg.getQualifiedName().equals(fullName)) {
            return pkg;
        }
        for (PackageableElement elem : pkg.getPackagedElements()) {
            if (elem instanceof Package) {
                Package found = findPackage((Package) elem, fullName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * @param args
     * 
     * Environment Variable is necessary:
     *  PROJECT  UML model file path
     *  OUTPUT   Output source code directory path
     *  PACKAGE  Package path inside model (only classes inside this package are code generated)
     *  SYNTAX   Syntax definition CSV file path
     *  LANGUAGE Programming language to generate
     */
    public static void main(String[] args) {
        try {
            String javaVersion = System.getProperty("java.version");
            System.out.println("Java version: " + javaVersion);

            // If you want to parse and check the major version
            int majorVersion = getMajorJavaVersion();
            System.out.println("Java major version: " + majorVersion);

            SyntaxCsv stxCsv = new SyntaxCsv(System.getenv("SYNTAX"));

            if (System.getenv("NEWLINE_LF") != null && !System.getenv("NEWLINE_LF").isEmpty()) {
                System.setProperty("line.separator", "\n");
            }

            // Load UML model from file
            String projectPath = System.getenv("PROJECT");
            ResourceSet resourceSet = new ResourceSetImpl();
            UMLResourcesUtil.init(resourceSet);
            
            // 🧩 Add this before loading .notation
            resourceSet.getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("notation", new XMIResourceFactoryImpl());
            NotationPackage.eINSTANCE.eClass();
            StylePackage.eINSTANCE.eClass();
            
            // 1️⃣ Load UML model
            URI umlUri = URI.createFileURI(projectPath);
            Resource resource = resourceSet.getResource(umlUri, true);
            
            // 2️⃣ Load NOTATION model (diagram layout)
            URI notationUri = umlUri.trimFileExtension().appendFileExtension("notation");
            notationResource = resourceSet.getResource(notationUri, true);

            // 3️⃣ (Optional) Load DI model (diagram metadata)
            // URI diUri = umlUri.trimFileExtension().appendFileExtension("di");
            // Resource diResource = resourceSet.getResource(diUri, true);

            // ✅ Now you can traverse the diagram layout
            System.out.println("Notation resource loaded: " + notationResource);            
            
            Package model = (Package) resource.getContents().get(0);

            // find specified package
            final String pkgFullname = System.getenv("PACKAGE");
            Package targetPkg = findPackage(model, pkgFullname);
            if (targetPkg == null) {
                System.out.println("Package " + pkgFullname + " not found!");
                System.exit(0);
            }

            // Create code files for each class in the package
            for (PackageableElement pkgElement : targetPkg.getPackagedElements()) {
                if (pkgElement instanceof Classifier) {
                    Classifier umlClass = (Classifier) pkgElement;
                    System.out.println("----------- Generating code for class " + umlClass.getName() + " ------------");
                    TFileGenerator fileGen = new TFileGenerator(stxCsv, umlClass);
                    TOperGenerator operPrs = new TOperGenerator(stxCsv, umlClass, null);

                    operPrs.parseCode(fileGen.getInputFile());
                    Writer writer = fileGen.openFile();
                    // find Main State-machine
                    RStmGenerator _stmGen = new RStmGenerator(stxCsv, umlClass, writer);
                    StateMachine mainStateMachine = _stmGen.getMainStm();
                    if (mainStateMachine == null) {
                        _stmGen = null;
                    }
                    /*
                    for (IDiagram iDgr: iClass.getDiagrams()) {
                        ...
                    }
                    */
                    /*
                    if (mainStateMachine != null) {
                        ...
                    }
                    */
                    fileGen.printHeader();
                    fileGen.printIncludes(mainStateMachine);
                    TClassGenerator classGen = new TClassGenerator(stxCsv, umlClass, writer);
                    classGen.printClassHeader();
                    TNestedClsGenerator nestedClsGen = new TNestedClsGenerator(stxCsv, umlClass, writer);
                    nestedClsGen.printNestedClasses();

                    TOperGenerator operGen = new TOperGenerator(stxCsv, umlClass, writer);
                    TAttrGenerator attrGen = new TAttrGenerator(stxCsv, umlClass, writer);
                    attrGen.printStaticAttrDecls();
                    //new TObjectGenerator(stxCsv, umlClass, writer); TODO

                    operGen.printFreeFuncPrototypes();
                    operGen.printConcreteOpers(true);

                    TPropGenerator propGen = new TPropGenerator(stxCsv, umlClass, writer);
                    propGen.printProperties();

                    if (mainStateMachine != null) {
                        _stmGen.printEventDecl();
                    }

                    fileGen.printFriendsDecl();
                    fileGen.printInternalIncludes(mainStateMachine);

                    operGen.printConcreteOpers(false);

                    /*
                    for (IDiagram iDgr: iClass.getDiagrams()) {
                        ...
                    }
                    */
                    if (mainStateMachine != null) {
                        _stmGen.printStmImpls();
                    }

                    TCtorGenerator ctorGen = new TCtorGenerator(stxCsv, umlClass, writer);
                    ctorGen.printConstructor(attrGen, operGen.hasVFunc(), _stmGen);
                    operGen.printVirtualTbl();

                    attrGen.printAttrDeclarations(operGen.hasVFunc(), _stmGen);

                    classGen.printClassFooter();

                    fileGen.printFooter();
                    fileGen.closeFile();
                }
            }

            System.out.println( "Program ended" );
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
