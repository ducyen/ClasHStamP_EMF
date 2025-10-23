package stm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;

import com.change_vision.jude.api.inf.model.IAttribute;
import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IDependency;
import com.change_vision.jude.api.inf.model.INamedElement;
import com.change_vision.jude.api.inf.model.IRealization;
import com.change_vision.jude.api.inf.model.IStateMachine;

public class TFileGenerator extends TBaseGenerator {
	private File file;
	private File inputFile;
	private ArrayList<String> m_doneList = new ArrayList<String>();
	private ArrayList<IClass> m_doneAttr = new ArrayList<IClass>();
	
	public TFileGenerator(SyntaxCsv stxCsv, IClass iClass) throws Exception {
		super(stxCsv, iClass, null);
		String outputPath = System.getenv("OUTPUT") + "/" + 
		    Utils.get(m_stxCsv.get("file", "name"),
		    	m_iClass.getName(), 
		    	m_iClass.getTypeModifier(),
		    	m_iClass.getFullNamespace("/"),
		    	"",
		    	m_iClass.getAlias1(),
		    	m_iClass.getDefinition()
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
			    	m_iClass.getTypeModifier(),
			    	m_iClass.getFullNamespace("/"),
			    	"",
			    	m_iClass.getAlias1(),
			    	m_iClass.getDefinition()
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
	 * getFile
	 * @return
	 */
	public File getFile() {
		return file;
	}
	
	/**
	 * getInputFile
	 * @return
	 */
	public File getInputFile() {
		return inputFile;
	}
	
	/**
	 * openFile
	 * @return
	 * @throws Exception
	 */
	public Writer openFile() throws Exception {
		String encoding = System.getenv("ENCODING");
		System.out.println("Encoding " + encoding);
		if (encoding != null && !encoding.isEmpty()) {
			m_writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
		} else {
			m_writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			m_writer.write('\ufeff');
		}
		return m_writer;
	}
	
	/**
	 * printHeader
	 * @throws IOException
	 * @throws Exception
	 */
	public void printHeader() throws IOException, Exception {
		m_writer.write(
			Utils.get(m_stxCsv.get("file", "begin"), 
			m_iClass.getName(),
			m_iClass.getTypeModifier(),
			m_iClass.getFullNamespace(m_pkgPathSeparator),
			"",
			m_iClass.getAlias1(),
			m_iClass.getDefinition()
			)
		);
	}
	
	/**
	 * findOwner
	 */
	private IClass findOwner(IClass theType) {
		if (theType.getOwner() instanceof IClass) {		// if the super-class is a nested class
			return (IClass)theType.getOwner();		// then include its containing class
		}
		return theType;
	}
	
	/**
	 * printInclude
	 * @param path
	 * @param theType
	 * @throws IOException
	 * @throws Exception
	 */
	private void printInclude(String path, IClass theType, INamedElement descElem) throws IOException, Exception  {
		String desc = "";
		if (!descElem.getDefinition().isEmpty()) {
			desc = fillComment(descElem, true);
		}		
		String includeContent = Utils.get(path, 
			theType.getName(), 
			theType.getTypeModifier(),
			theType.getFullNamespace(m_pkgPathSeparator),
			descElem.getName(),
			theType.getAlias1(),
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
	 * printInclude
	 * @throws IOException
	 * @throws Exception
	 */
	public void printIncludes(IStateMachine iMainStm) throws IOException, Exception {
		m_doneList.clear();
		m_doneAttr.clear();
		
		/////////////////////////// internal includes ///////////////////////////
		String impDepStx = m_stxCsv.get("file", "ext1st");
		// print superClass include
		if (m_iSuperClass != null) {
			IClass theType = findOwner(m_iSuperClass);
			IClass iGen = findGeneralization(m_iClass);
			printInclude(impDepStx, theType, iGen);
		}		
		
        //   print implemented interface
		for (IRealization iRealization: m_iClass.getClientRealizations()) {
			IClass theType = findOwner((IClass)iRealization.getSupplier());
			printInclude(impDepStx, theType, iRealization.getSupplier());
		}
		
        // print external types include (for not-standard-type and this-class's attribute )
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			IClass theType = findOwner(iAttr.getType());
			if (!isStandardType(iAttr.getType()) && theType != m_iClass && 
				!iAttr.getName().isEmpty() && iAttr.getType() != m_iSuperClass
			) {
				if (findAttrKind(iAttr) == AttrKind.OBJECT && !hasStereotype(iAttr.getType().getStereotypes(), "struct")) {
					printInclude(impDepStx, theType, iAttr);
				}
			}
		}
		
		/////////////////////////// limited include /////////////////////////////////
		String useDepStx = m_stxCsv.get("file", "ext1st");
		String callDepStx = m_stxCsv.get("file", "extnxt");
        // print external types include (for not-standard-type and this-class's attribute )
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			IClass theType = findOwner(iAttr.getType());
			if (!isStandardType(iAttr.getType()) && theType != m_iClass && 
				!iAttr.getName().isEmpty() && iAttr.getType() != m_iSuperClass
			) {
				if (findAttrKind(iAttr) != AttrKind.OBJECT && !hasStereotype(iAttr.getType().getStereotypes(), "struct")) {
					printInclude(useDepStx, theType, iAttr);
				}
			}
		}		
		
        // print dependencies include
		for (IDependency iDependency: m_iClass.getClientDependencies()) {
			if (iDependency.getSupplier() instanceof IClass) {
				IClass iSupplier = (IClass)iDependency.getSupplier();
				IClass theType = findOwner(iSupplier);
				if (!isStandardType(iSupplier) && iSupplier != m_iSuperClass && theType != m_iClass) {
					if (!hasStereotype(iDependency.getStereotypes(), "friend")) {
						String path = callDepStx;
						if (hasStereotype(iDependency.getStereotypes(), "use")) {
							path = useDepStx;
						}
						printInclude(path, theType, iDependency);
					}
				}				
			}			
		}
		
		// print state-machine dependency include
		if (iMainStm != null && isCodeFile()) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "name"), 
				m_iClass.getName(), 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_pkgPathSeparator),
				"",
				m_iClass.getAlias1(),
				""
			));
		}
		
	}

	/**
	 * printFriendIncludes
	 * @throws IOException
	 * @throws Exception
	 */
	public void printFriendsDecl() throws IOException, Exception {
		m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "ext1st"), m_iClass.getName()));
		m_doneList.clear();
		m_doneAttr.clear();
		String path = m_stxCsv.get(indent, "friend", "extnxt");
        // print dependencies include
		for (IDependency iDependency: m_iClass.getClientDependencies()) {
			if (iDependency.getSupplier() instanceof IClass) {
				IClass iSupplier = (IClass)iDependency.getSupplier();
				IClass theType = findOwner(iSupplier);
				if (!isStandardType(iSupplier) && iSupplier != m_iSuperClass && theType != m_iClass) {
					if (hasStereotype(iDependency.getStereotypes(), "friend")) {
						printInclude(path, theType, iDependency);
					}
				}				
			}
		}
		//m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "name"), m_iClass.getName()));
		//m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "begin"), m_iClass.getName()));
		m_writer.write(Utils.get(m_stxCsv.get(indent, "friend", "end"), m_iClass.getName()));
	}

	/**
	 * printInternalIncludes
	 * @param iMainStm
	 * @throws IOException
	 * @throws Exception
	 */
	public void printInternalIncludes(IStateMachine iMainStm) throws IOException, Exception {
		m_doneList.clear();
		m_doneAttr.clear();
		String impDepStx = m_stxCsv.get("friend", "name");
		String impRefStx = m_stxCsv.get("friend", "begin");
		// print superClass include
		if (m_iSuperClass != null) {
			IClass theType = findOwner(m_iSuperClass);
			IClass iGen = findGeneralization(m_iClass);
			printInclude(impDepStx, theType, iGen);
		}
		
        //   print implemented interface
		for (IRealization iRealization: m_iClass.getClientRealizations()) {
			IClass theType = findOwner((IClass)iRealization.getSupplier());
			printInclude(impDepStx, theType, iRealization.getSupplier());
		}		
		
        // print external types include (for not-standard-type and this-class's attribute )
		for (IAttribute iAttr: m_iClass.getAttributes()) {
			IClass theType = findOwner(iAttr.getType());
			if (!isStandardType(iAttr.getType()) && theType != m_iClass && 
				!iAttr.getName().isEmpty() && iAttr.getType() != m_iSuperClass) {
				if (findAttrKind(iAttr) == AttrKind.OBJECT && !hasStereotype(iAttr.getType().getStereotypes(), "struct")) {
					printInclude(impDepStx, theType, iAttr);
				} else if (findAttrKind(iAttr) == AttrKind.REFERENCE && !hasStereotype(iAttr.getType().getStereotypes(), "struct")) {
					printInclude(impRefStx, theType, iAttr);
				}
			}
		}

		// print state-machine include
		if (iMainStm != null && !isCodeFile()) {
			m_writer.write(Utils.get(m_stxCsv.get(indent, "statemachine", "name"), 
				m_iClass.getName(), 
				m_iSuperClass != null ? m_iSuperClass.getName() : m_iClass.getName(),
				m_iClass.getFullNamespace(m_pkgPathSeparator),
				"",
				m_iClass.getAlias1(),
				""
			));
		}
	}
	
	/**
	 * printFooter
	 * @throws IOException
	 * @throws Exception
	 */
	public void printFooter() throws IOException, Exception {
		m_writer.write(
			Utils.get(m_stxCsv.get("file", "end"), 
				m_iClass.getName(),
				m_iClass.getTypeModifier(),
				m_iClass.getFullNamespace(m_pkgPathSeparator),
				"",
				m_iClass.getAlias1(),
				m_iClass.getDefinition()
			)
		);		
	}
	
	public void closeFile() throws IOException {
		m_writer.close();
	}
}
