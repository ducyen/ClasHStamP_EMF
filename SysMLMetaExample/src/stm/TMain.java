/**
 * 
 */
package stm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.change_vision.jude.api.inf.*;
import com.change_vision.jude.api.inf.exception.LicenseNotFoundException;
import com.change_vision.jude.api.inf.exception.NonCompatibleException;
import com.change_vision.jude.api.inf.exception.ProjectLockedException;
import com.change_vision.jude.api.inf.exception.ProjectNotFoundException;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IDiagram;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IPackage;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IStateMachineDiagram;
import com.change_vision.jude.api.inf.project.*;

import rfc.RStmGenerator;

/**
 * @author duc
 * 
 */
public class TMain {
    public static ProjectAccessor prjAccessor;
    
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
	/**
	 * @param args
	 * 
	 * Environment Variable is necessary:
	 *  PROJECT  Astah's file path
	 *  OUTPUT   Output source code directory path
	 *  PACKAGE  Package path inside Astah file (only classes inside this package are code generated)
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
			
			try {
			    prjAccessor = AstahAPI.getAstahAPI().getProjectAccessor();
		    } catch (ClassNotFoundException e) {
		        prjAccessor = ProjectAccessorFactory.getProjectAccessor();
		    }
			SyntaxCsv stxCsv = new SyntaxCsv(System.getenv("SYNTAX"));

			if (System.getenv("NEWLINE_LF") != null && !System.getenv("NEWLINE_LF").isEmpty()) {
				System.setProperty("line.separator", "\n");
			}
			
			// Open a project
			// param 1 : Project name
			// param 2 : true not to check model version
			// param 3 : false not to lock a project file
			// param 4 : true to open a project file with the read only mode if the file is locked.
			String projectPath = System.getenv("PROJECT");
			try {
				prjAccessor.open(projectPath, true, false, true);
			} catch (ProjectLockedException e) {
				System.out.println("Project cannot be oppend");
			}
			
			// find specified package
			final String pkgFullname = System.getenv("PACKAGE");
			INamedElement[] pkgs = prjAccessor.findElements(
				new ModelFinder() {					
					@Override
					public boolean isTarget(INamedElement arg0) {
						// TODO Auto-generated method stub
						return arg0.getFullName("/").equals(pkgFullname);						
					}
				}
			);
			// if package not found, abort the program
			if (pkgs.length == 0) {
				System.out.println("Package " + pkgFullname + " not found!");
				System.exit(0);
			}			
			
			// Create code files for each class in the package
			IPackage iPkg = (IPackage)pkgs[0];
			for (INamedElement pkgElement: iPkg.getOwnedElements()) {
				if (pkgElement instanceof IClass) {
					IClass iClass = (IClass)pkgElement;
					System.out.println("----------- Generating code for class " + iClass.getName() + " ------------");
					TFileGenerator fileGen = new TFileGenerator(stxCsv, iClass);
					TOperGenerator operPrs = new TOperGenerator(stxCsv, iClass, null);
					operPrs.parseCode(fileGen.getInputFile());
					Writer writer = fileGen.openFile();
					// find Main State-machine
					RStmGenerator _stmGen = new RStmGenerator(stxCsv, iClass, writer);
					IStateMachine iMainStm = _stmGen.getMainStm();
					if (iMainStm == null) {
						_stmGen = null;
					}
					/*
					for (IDiagram iDgr: iClass.getDiagrams()) {
						if (iDgr instanceof IStateMachineDiagram) {
							IStateMachineDiagram iStmDgr = (IStateMachineDiagram)iDgr;
							IStateMachine iStm = iStmDgr.getStateMachine();
							System.out.println("statemachine found " + iStm.getStateMachineDiagram().getName());

							// Find main state-machine, which is not sub-machine of any other state-machine
							iMainStm = iStm;
							for (IDiagram iContainingDgr: iClass.getDiagrams()) {
								if (iContainingDgr instanceof IStateMachineDiagram) {
									IStateMachineDiagram iContainingStmDgr = (IStateMachineDiagram)iContainingDgr;
									IStateMachine iContainingStm = iContainingStmDgr.getStateMachine();
									if (iContainingStm != iStm && TStmGenerator.Find(iContainingStm, iStm)) {
										System.out.println(iStm.getName() + " is sub-machine of " + iContainingStm);
										iMainStm = null;
									}
								}
							}
							
							if (iMainStm != null) {
								System.out.println("Main state-machine found: " + iMainStm.getStateMachineDiagram().getName());
								break;
							}
						}
					}
					*/
					TStmGenerator stmGen = null;
					/*
					if (iMainStm != null) {
						TStateGenerator.ResetLeafStateCnt();
						stmGen = new TStmGenerator(stxCsv, iClass, writer, iMainStm);
					}
					*/
					TClassGenerator classGen = new TClassGenerator(stxCsv, iClass, writer);
					/*
					List<TStateGenerator> stateGens = new ArrayList<TStateGenerator>();
					*/
					fileGen.printHeader();
					
					fileGen.printIncludes(iMainStm);
					
					classGen.printClassHeader();

					TNestedClsGenerator nestedClsGen = new TNestedClsGenerator(stxCsv, iClass, writer);
					nestedClsGen.printNestedClasses();					

					TOperGenerator operGen = new TOperGenerator(stxCsv, iClass, writer);
					TAttrGenerator attrGen = new TAttrGenerator(stxCsv, iClass, writer);
					attrGen.printStaticAttrDecls();
					new TObjectGenerator(stxCsv, iClass, writer);
					
					operGen.printFreeFuncPrototypes();					
					operGen.printConcreteOpers(true);

					TPropGenerator propGen = new TPropGenerator(stxCsv, iClass, writer);
					propGen.printProperties();
					
					TTransGenerator.clearEventLists();
					for (IDiagram iDgr: iClass.getDiagrams()) {
						if (iDgr instanceof IStateMachineDiagram) {
							IStateMachine iStm = ((IStateMachineDiagram)iDgr).getStateMachine();
							TTransGenerator.createEventLists(iStm);
						}
					}
					
					if (iMainStm != null) {
						_stmGen.printEventDecl();
					}
					

					fileGen.printFriendsDecl();
					fileGen.printInternalIncludes(iMainStm);
					
					operGen.printConcreteOpers(false);

					/*
					for (IDiagram iDgr: iClass.getDiagrams()) {
						if (iDgr instanceof IStateMachineDiagram) {
							IStateMachine iStm = ((IStateMachineDiagram)iDgr).getStateMachine();
							TStateGenerator stateGen = new TStateGenerator(stxCsv, iClass, writer, iStm);
							stateGens.add(stateGen);

							if (iMainStm == null) {
								System.out.println("Error: " + iClass.getName() + 
									" has some state-machine but doesn't have main state-machine, which is parent of all other machines");
								System.exit(1);
							}
						}
					}
					*/
					
					if (iMainStm != null) {
						_stmGen.printStmImpls();
						////stmGen.printStmCtors();
						//stmGen.printStmAPIs();
						////transGen.printEventAPIs(iClass);
					}
					
					TCtorGenerator ctorGen = new TCtorGenerator(stxCsv, iClass, writer);
					ctorGen.printConstructor(attrGen, operGen.hasVFunc(), _stmGen);
					operGen.printVirtualTbl();
					attrGen.printAttrDeclarations(operGen.hasVFunc(), _stmGen);

					classGen.printClassFooter();
					
					fileGen.printFooter();
					fileGen.closeFile();
					
				}				
			}
			
            //+parseOperationCode
            //+printFileHeader
            //+print superClass include
            //+print external types include (for attribute)
            //+print dependencies include
            //-print State-machine include (if available)
            //+print class name (including superClass inheritance)
            //+  print implemented interface
            //+  print class begin
            //+  if has state-machine
            //+    print event-enumeration (all machine's event and sub-machine-state exit-points)
            //+  for each state-machine
            //+    print state declarations (simple state, composite state and sub-machine-state entry-points)
			//+    (for source-file, print BgnTrans/EndTrans prototypes) 
            //+  print nested class (structure and enumeration)
            //+  print leaf abstract (static or not) operations (function prototypes of C)
            //+  print static attributes declaration and initialization
            //+  print virtual operations table (declaration for abstract class, and implementation for concrete class)
            //+  print constructor name
            //+    print not-initialized attributes as input parameter
            //+    for 2 times (one for initialization and one for declaration)
            //+      if has superClass
            //+        print superClass constructor call
            //+        print superClass not-initialized attributes as input parameter
            //+      otherwise
            //+        print constructor begin
            //+    print member initialization (for both in-initialized and initialized attributes)
            //+      if having state-machine
            //+        print external state-machine (sub-machine or orthogonal-state) initialization
            //+        print history pseudo-state initialization
            //+      print constructor end
            //+  print concrete operations
            //+  print default getter/setter
            //+  for each state-machine
            //     for each state
            //+      print state's entry
            //+        for each orthogonal-state in this state
            //+          reset that orthogonal-state	      (substm_impl.begin)
            //+      print state's eventProc
            //+        if state transition
            //+          if hit exit-point
            //+            fire corresponding event to top-machine
            //+          if hit sub-machine's entry-point
            //+            reset that sub-machine (with entry-point or stmTop specified)
			//+          if hit final-state
			//+            check if orthogonal-state finished (substm_impl.ext1st)
            //+      print state's exit
            //+        for each orthogonal-state in this state
            //+          abort that orthogonal-state	      (substm_impl.end)
            //+    print composite-state start
            //+      print initialization-pseudo-state transition
            //+      print entry-point transition
            //+    print composite-state done
            //+    print top event processor
            //+      forward event to external machine        (substm_impl.extnxt)
            //+      print state-to-eventProc map
            //+    print state-to-entry map (BgnTrans)
            //+    print state-to-exit map (EndTrans)
            //+    print junctions
            //   print API for main-state
            //+printFileFooter
			
			
			// Close a project
			prjAccessor.close();
	
			System.out.println( "Program ended" );
	    } catch (LicenseNotFoundException e) {
	        e.printStackTrace();
	    } catch (ProjectNotFoundException e) {
	        e.printStackTrace();
	    } catch (ProjectLockedException e) {
	        e.printStackTrace();
	    } catch (NonCompatibleException e) {
	        e.printStackTrace();
	    } catch (ClassNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    } catch (Throwable e) {
	        e.printStackTrace();
	    }
	}

}
