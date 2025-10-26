import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.*;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil;
 
public class SysMLMetaExample {
 
    public static void main(String[] args) throws Exception {
        ResourceSet resourceSet = new ResourceSetImpl();
        UMLResourcesUtil.init(resourceSet);
 
        URI modelURI = URI.createFileURI("data/SysML_Sample00.uml");
        Resource modelRes = resourceSet.getResource(modelURI, true);
        Model model = (Model) modelRes.getContents().get(0);

 
        listBlocks((Package) model);
    }
 
    private static void listBlocks(Package pkg) {
        for (PackageableElement pe : pkg.getPackagedElements()) {
            if (pe instanceof Class) {
                Class clazz = (Class) pe;
                System.out.println("Found Block/Class: " + clazz.getQualifiedName());
 
                // List attributes
                for (Property attr : clazz.getAllAttributes()) {
                    System.out.println("  Attribute: " + attr.getName() + " : " +
                        (attr.getType() != null ? attr.getType().getName() : "UnknownType"));
                }
 
                // List operations
                for (Operation op : clazz.getOwnedOperations()) {
                    System.out.print("  Method: " + op.getName() + "(");
                    for (int i = 0; i < op.getOwnedParameters().size(); i++) {
                        Parameter param = op.getOwnedParameters().get(i);
                        if (param.getDirection() == ParameterDirectionKind.IN_LITERAL) {
                            System.out.print(param.getName() + " : " +
                                (param.getType() != null ? param.getType().getName() : "UnknownType"));
                            if (i < op.getOwnedParameters().size() - 1) System.out.print(", ");
                        }
                    }
                    System.out.println(")");
                }
 
            } else if (pe instanceof Package) {
                listBlocks((Package) pe);
            } else if (pe instanceof Interface) {
                Interface ifc = (Interface) pe;
                System.out.println("Found Interface: " + ifc.getQualifiedName());
            }
        }
    }
}
 
 