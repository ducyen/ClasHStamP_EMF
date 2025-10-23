package stm;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.change_vision.jude.api.inf.model.IClass;
import com.change_vision.jude.api.inf.model.IClassDiagram;
import com.change_vision.jude.api.inf.model.IDiagram;
import com.change_vision.jude.api.inf.model.IInstanceSpecification;
import com.change_vision.jude.api.inf.model.ISlot;
import com.change_vision.jude.api.inf.model.IStateMachine;
import com.change_vision.jude.api.inf.model.IStateMachineDiagram;
import com.change_vision.jude.api.inf.presentation.ILinkPresentation;
import com.change_vision.jude.api.inf.presentation.INodePresentation;
import com.change_vision.jude.api.inf.presentation.IPresentation;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMPathElement;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TObjectGenerator extends TBaseGenerator {
	
	private String getNodeAttrValue(Element element, String name) {
        // List all attributes
		NamedNodeMap attributes = element.getAttributes();
        for (int j = 0; j < attributes.getLength(); j++) {
            Node attribute = attributes.item(j);
            if (attribute.getNodeName().equals(name)) {
            	return attribute.getNodeValue();
            }            
        }
        return null;		
	}
	
    private List<double[]> parsePathData(String pathData) {
        List<double[]> vertices = new ArrayList<double[]>();
        PathParser pathParser = new PathParser();
        AWTPathProducer pathProducer = new AWTPathProducer();
        pathParser.setPathHandler(pathProducer);
        pathParser.parse(pathData);

        PathIterator pathIterator = pathProducer.getShape().getPathIterator(new AffineTransform());
        double[] coords = new double[6];
        while (!pathIterator.isDone()) {
            int segmentType = pathIterator.currentSegment(coords);
            if (segmentType != PathIterator.SEG_CLOSE) {
                vertices.add(new double[]{coords[0], coords[1]});
            }
            pathIterator.next();
        }
        return vertices;
    }
    
    private double getAngle(String svgFilePath) {
    	double angle = 0;
    	
        // Create a SAXSVGDocumentFactory instance
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);

        try {
            // Parse the SVG file
        	URI uriPath = new File(svgFilePath).toURI();
            Document document = factory.createDocument(uriPath.toString());
	    	NodeList groupElements = document.getElementsByTagName("g");
	        for (int i = 0; i < groupElements.getLength(); i++) {
	            Element groupElement = (Element) groupElements.item(i);
	            String transformAttr = groupElement.getAttribute("transform");
	
	            if (transformAttr != null && !transformAttr.isEmpty() && transformAttr.startsWith("rotate(")) {
	                // Extract the rotation angle and pivot point
	                String rotationString = transformAttr.substring(7, transformAttr.length() - 1);
	                String[] parts = rotationString.split(",");
	
	                angle = Double.parseDouble(parts[0]);
	                double cx = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.0;
	                double cy = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;
	
	                // Print the rotation details
	                System.out.println("Group " + groupElement.getAttribute("id") + " is rotated by " + angle + " degrees around point (" + cx + ", " + cy + ")");
	            }
	        }
        } catch (IOException e) {
            System.out.println("Not an SVG");
        }    	
    	return angle;    	
    }
    
    private String m_verticesCode;
    private double m_centerX = 0;
    private double m_centerY = 0;
    private int convertPathData(String svgFilePath) {
    	int vertsCnt = 0;
        // Create a SAXSVGDocumentFactory instance
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);

        m_verticesCode = "";
        try {
            // Parse the SVG file
        	URI uriPath = new File(svgFilePath).toURI();
            Document document = factory.createDocument(uriPath.toString());
            
            // Get the root element (which should be the <svg> element)
            Element rootElement = document.getDocumentElement();

            // Extract width and height attributes
            String viewBox = rootElement.getAttribute("viewBox");
            double viewBoxMinX = 0;
            double viewBoxMinY = 0;
            double viewBoxWidth = 1;
            double viewBoxHeight = 1;

            if (viewBox != null && !viewBox.isEmpty()) {
                // viewBox is in the format: "min-x min-y width height"
                String[] viewBoxValues = viewBox.split("\\s+");
                if (viewBoxValues.length == 4) {
                    viewBoxMinX = Double.parseDouble(viewBoxValues[0]);
                    viewBoxMinY = Double.parseDouble(viewBoxValues[1]);
                    viewBoxWidth = Double.parseDouble(viewBoxValues[2]);
                    viewBoxHeight = Double.parseDouble(viewBoxValues[3]);
                }
            }

            System.out.println("ViewBox: (" + viewBoxMinX + ", " + viewBoxMinY + ", " + viewBoxWidth + ", " + viewBoxHeight + ")");           
            
            // Extract the translation transformation
            NodeList groupElements = document.getElementsByTagName("g");
            double translateX = 0;
            double translateY = 0;
            for (int i = 0; i < groupElements.getLength(); i++) {
                Element groupElement = (Element) groupElements.item(i);
                if (groupElement.hasAttribute("transform")) {
                    String transform = groupElement.getAttribute("transform");
                    if (transform.startsWith("translate(")) {
                        String[] translateValues = transform.substring(10, transform.length() - 1).split(",");
                        translateX = Double.parseDouble(translateValues[0]);
                        translateY = Double.parseDouble(translateValues[1]);
                        break;
                    }
                }
            }

            System.out.println("Translation: (" + translateX + ", " + translateY + ")");           
            
            // Get the circle element
            Element element = document.getElementById("center");
            // Find center
            if (element != null) {
            	m_centerX = Double.parseDouble(getNodeAttrValue(element, "cx"));
            	m_centerY = Double.parseDouble(getNodeAttrValue(element, "cy"));
            }
            System.out.println("Center: (" + m_centerX + ", " + m_centerY + ")");
            
            // Get the path element
            element = document.getElementById("collider");
            // Find collision shape
            if (element != null) {
            	String pathData = getNodeAttrValue(element, "d");
                // Parse the path data to extract coordinates
                List<double[]> vertices = parsePathData(pathData);
                for (double[] vertex : vertices) {
                    // System.out.println("Vertex: (" + vertex[0] + ", " + vertex[1] + ")");
                    if (vertsCnt == 0){
                    	m_verticesCode = "{" + (vertex[0]-m_centerX)/viewBoxWidth + ", " + (vertex[1]-m_centerY)/viewBoxHeight + "}";
                    } else {
                    	m_verticesCode += ", {" + (vertex[0]-m_centerX)/viewBoxWidth + ", " +(vertex[1]-m_centerY)/viewBoxHeight + "}";
                    }
                    vertsCnt++;
                }
            }

            m_centerX = (m_centerX + translateX) / viewBoxWidth;
            m_centerY = (m_centerY + translateY) / viewBoxHeight;
        } catch (IOException e) {
            e.printStackTrace();
        }
    	return vertsCnt;
    }

	public TObjectGenerator(SyntaxCsv stxCsv, IClass iClass, Writer writer) throws IOException, Exception {
		super(stxCsv, iClass, writer);
		
        for (IDiagram diagram : iClass.getDiagrams()) {
			if (diagram instanceof IClassDiagram) {
				int indent = 0;
				
				IClassDiagram clsDgr = (IClassDiagram)diagram;
				Rectangle2D boundRect = clsDgr.getBoundRect();
				System.out.println("----------- Generating code for diagram " + diagram.getName() + " ------------");
				
				String attr_kind = "object";
				String pathObject = "begin";
				String objectString = "";
				IPresentation presentations[] = clsDgr.getPresentations();
				Arrays.sort(presentations, new Comparator<IPresentation>() {
					@Override
					public int compare(IPresentation arg0, IPresentation arg1) {
						// TODO Auto-generated method stub
						return arg1.getDepth() - arg0.getDepth();
					}
					
				});
				for (IPresentation present: presentations) {
					if (present instanceof INodePresentation && present.getModel() instanceof IInstanceSpecification) {
						indent++;
						
						System.out.println(present.getModel());
						
						String parameterString = "";
						
						INodePresentation nodePresent = (INodePresentation)present;
						
						IInstanceSpecification instSpec = (IInstanceSpecification)present.getModel();						
						
						// Collect parameters
						String pathParam = "ext1st";
						String rectCoord = "" + (nodePresent.getLocation().getX() - boundRect.getX()) / boundRect.getWidth() + ", " + 
												(nodePresent.getLocation().getY() - boundRect.getY()) / boundRect.getHeight() + ", " + 
												nodePresent.getWidth() / boundRect.getWidth() + ", " + nodePresent.getHeight() / boundRect.getHeight();
												
						if (instSpec.getSlot("m_iniRect") != null) {
							ISlot slot = instSpec.getSlot("m_iniRect");
							parameterString +=	Utils.get(
								m_stxCsv.get(indent, attr_kind, pathParam), 
								slot.getName(), 
								"", 
								"",
								rectCoord,
								"",
								slot.getDefinition(),
								getVisibility(instSpec)
							);
							pathParam = "extnxt";
						}
						if (instSpec.getSlot("m_name") != null) {
							ISlot slot = instSpec.getSlot("m_name");
							parameterString +=	Utils.get(
								m_stxCsv.get(indent, attr_kind, pathParam), 
								slot.getName(), 
								"", 
								"",
								"\"" + instSpec.getName() + "\"",
								"",
								slot.getDefinition(),
								getVisibility(instSpec)
							);
							pathParam = "extnxt";
						}
						String imgPath = ""; 
						if (instSpec.getSlot("m_imgPath") != null) {
							ISlot slot = instSpec.getSlot("m_imgPath");
							if (slot.getValue().equals("")) {
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									"\"" + instSpec.getClassifier() + ".png\"",
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
							} else {
								imgPath = slot.getValue();
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									"\"" + slot.getValue() + ".png\"",
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
							}
							pathParam = "extnxt";
						}
						int vertsCnt = 0;
			            m_centerX = 0;
			            m_centerY = 0;
						if (instSpec.getSlot("m_verts") != null) {
							ISlot slot = instSpec.getSlot("m_verts");
							if (!imgPath.isEmpty()) {
								Path path = Paths.get(System.getenv("PROJECT")).getParent();
								String svgFilePath = path + "/Image/" + imgPath + ".svg";
								
						        m_verticesCode = "";
						        vertsCnt = convertPathData(svgFilePath);
								
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									"(cpVect[]){ " + m_verticesCode + " }",
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
							}
							pathParam = "extnxt";
						}
						if (instSpec.getSlot("m_vertsCnt") != null) {
							ISlot slot = instSpec.getSlot("m_vertsCnt");
							parameterString +=	Utils.get(
								m_stxCsv.get(indent, attr_kind, pathParam), 
								slot.getName(), 
								"", 
								"",
								String.valueOf(vertsCnt),
								"",
								slot.getDefinition(),
								getVisibility(instSpec)
							);
						}						
						if (instSpec.getSlot("m_center") != null) {
							ISlot slot = instSpec.getSlot("m_center");
							parameterString +=	Utils.get(
								m_stxCsv.get(indent, attr_kind, pathParam), 
								slot.getName(), 
								"", 
								"",
								"{ " + String.valueOf(m_centerX) + ", " + String.valueOf(m_centerY) + " }",
								"",
								slot.getDefinition(),
								getVisibility(instSpec)
							);
						}						
						if (instSpec.getSlot("m_angle") != null) {
							ISlot slot = instSpec.getSlot("m_angle");
							Path path = Paths.get(System.getenv("PROJECT")).getParent();
							String svgFilePath = path + "/Image/" + imgPath + ".svg";
							double angle = getAngle(svgFilePath);
							parameterString +=	Utils.get(
								m_stxCsv.get(indent, attr_kind, pathParam), 
								slot.getName(), 
								"", 
								"",
								"" + angle,
								"",
								slot.getDefinition(),
								getVisibility(instSpec)
							);
							pathParam = "extnxt";
						}
						if (instSpec.getSlot("m_spriteCoords") != null) {
							ISlot slot = instSpec.getSlot("m_spriteCoords");
							if (slot.getValue().equals("")) {
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									"{ 0 }",
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
							} else {
								imgPath = slot.getValue();
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									slot.getValue(),
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
							}
							pathParam = "extnxt";
						}
						for (ISlot slot : instSpec.getAllSlots()) {
							if (slot.getName().equals("m_iniRect")) {
							} else if (slot.getName().equals("m_name")) {
							} else if (slot.getName().equals("m_imgPath")) {
							} else if (slot.getName().equals("m_verts")) {
							} else if (slot.getName().equals("m_vertsCnt")) {
							} else if (slot.getName().equals("m_center")) {
							} else if (slot.getName().equals("m_angle")) {
							} else if (slot.getName().equals("m_spriteCoords")) {
							} else if (!slot.getValue().trim().equals("")) {
								parameterString +=	Utils.get(
									m_stxCsv.get(indent, attr_kind, pathParam), 
									slot.getName(), 
									"", 
									"",
									slot.getValue(),
									"",
									slot.getDefinition(),
									getVisibility(instSpec)
								);
								pathParam = "extnxt";
							}
						}						
						
						objectString += Utils.get(
							m_stxCsv.get(indent, attr_kind, pathObject), 
							instSpec.getName(), 
							getTypeLiteral(instSpec.getClassifier()) + instSpec.getTypeModifier(), 
							m_iClass.getName(),
							parameterString,
							"",
							instSpec.getDefinition(),
							getVisibility(instSpec)
						);						
					
						pathObject = "end";
						indent--;
					} else if(present.getType().equals("Rectangle") 
							|| present.getType().equals("Oval") 
					) {
						indent++;						
						
						String parameterString = "";
						
						INodePresentation nodePresent = (INodePresentation)present;
						
						String lineColor = nodePresent.getProperty("line.color");
						String lineType = nodePresent.getProperty("line.type");
						String lineWidth = nodePresent.getProperty("line.width");
						String isFilled = nodePresent.getProperty("isfilled.color");
						String fillColor = nodePresent.getProperty("fill.color");
						String rectType = nodePresent.getProperty("rect.type");
						String fontFace = "null";
						String fontSize = "0";
						String label = "null";
						
						String primitiveType = present.getType() + 
							" " + lineColor.substring(1, 7) + 
							" " + lineType + 
							" " + lineWidth + 
							" " + isFilled + 
							" " + fillColor.substring(1, 7) + 
							" " + rectType +
							" " + fontFace +
							" " + fontSize +
							" " + label
						;
						System.out.println(primitiveType);
						
						// Collect parameters
						String pathParam = "ext1st";
						String rectCoord = "" + (nodePresent.getLocation().getX() - boundRect.getX()) / boundRect.getWidth() + ", " + 
												(nodePresent.getLocation().getY() - boundRect.getY()) / boundRect.getHeight() + ", " + 
												nodePresent.getWidth() / boundRect.getWidth() + ", " + nodePresent.getHeight() / boundRect.getHeight();

						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getID(), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						pathParam = "extnxt";
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + present.getType() + "_" + present.getID().substring(0, 3) + "\"",
							""
						);
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						
						objectString += Utils.get(
							m_stxCsv.get(indent, attr_kind, pathObject), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"Primitive", 
							m_iClass.getName(),
							parameterString,
							""
						);						
					
						pathObject = "end";
						indent--;
						
					} else if(present.getType().equals("Line")) {						
						indent++;						
						
						String parameterString = "";
						
						ILinkPresentation linePresent = (ILinkPresentation)present;
						if (linePresent.getPoints().length < 2) {
							continue;
						}
						
						String lineColor = linePresent.getProperty("line.color");
						String lineType = linePresent.getProperty("line.type");
						String lineWidth = linePresent.getProperty("line.width");
						String isFilled = linePresent.getProperty("isfilled.color");
						String fontFace = "null";
						String fontSize = "0";
						String label = "null";
						
						String primitiveType = present.getType() + 
							" " + lineColor.substring(1, 7) + 
							" " + lineType + 
							" " + lineWidth + 
							" " + isFilled +
							" " + "000000" +
							" " + "null" +
							" " + fontFace +
							" " + fontSize +
							" " + label
						;
						System.out.println(primitiveType);
						
						// Collect parameters
						String pathParam = "ext1st";
						String rectCoord = "" + (linePresent.getPoints()[0].getX() - boundRect.getX()) / boundRect.getWidth()
                                       + ", " + (linePresent.getPoints()[0].getY() - boundRect.getY()) / boundRect.getHeight()
                                       + ", " + (linePresent.getPoints()[1].getX() - boundRect.getX()) / boundRect.getWidth()
                                       + ", " + (linePresent.getPoints()[1].getY() - boundRect.getY()) / boundRect.getHeight()
                                       ;

						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getID(), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						pathParam = "extnxt";
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + present.getType() + "_" + present.getID().substring(0, 3) + "\"",
							""
						);
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						
						objectString += Utils.get(
							m_stxCsv.get(indent, attr_kind, pathObject), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"Primitive", 
							m_iClass.getName(),
							parameterString,
							""
						);						
					
						pathObject = "end";
						indent--;
					} else if(present.getType().equals("Text") && present instanceof INodePresentation) {
						indent++;						
						
						String parameterString = "";
						
						INodePresentation textPresent = (INodePresentation)present;
						
						String lineColor = textPresent.getProperty("line.color");
						String lineType = textPresent.getProperty("line.type");
						String lineWidth = textPresent.getProperty("line.width");
						String isFilled = textPresent.getProperty("isfilled.color");
						String fontFace = textPresent.getProperty("font.name");;
						String fontSize = textPresent.getProperty("font.size");;
						String label = textPresent.getLabel().replaceAll("\\r?\\n", "\\\\n");
						
						String primitiveType = present.getType() + 
							" " + lineColor.substring(1, 7) + 
							" " + lineType + 
							" " + lineWidth + 
							" " + isFilled +
							" " + "000000" +
							" " + "null" +
							" " + fontFace +
							" " + fontSize +
							" " + label
						;
						System.out.println(primitiveType);
						
						// Collect parameters
						String pathParam = "ext1st";
						String rectCoord = "" + (textPresent.getLocation().getX() - boundRect.getX()) / boundRect.getWidth() + ", " + 
								(textPresent.getLocation().getY() - boundRect.getY()) / boundRect.getHeight() + ", " + 
								textPresent.getWidth() / boundRect.getWidth() + ", " + textPresent.getHeight() / boundRect.getHeight();

						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getID(), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						pathParam = "extnxt";
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + present.getType() + "_" + present.getID().substring(0, 3) + "\"",
							""
						);
						parameterString +=	Utils.get(
							m_stxCsv.get(indent, attr_kind, pathParam), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"", 
							"",
							pathParam.equals("ext1st") ? rectCoord : "\"" + primitiveType + "\"",
							""
						);
						
						objectString += Utils.get(
							m_stxCsv.get(indent, attr_kind, pathObject), 
							present.getType() + "_" + present.getID().substring(0, 3), 
							"Primitive", 
							m_iClass.getName(),
							parameterString,
							""
						);						
					
						pathObject = "end";
						indent--;						
					} else {
						/*
						System.out.println("[Duc] Found Unknown Primitive " + present.getType());
						System.out.println("[Duc] Label " + present.getLabel());
						
						INodePresentation nodePresent = (INodePresentation)present;
						
						for (Object propName: nodePresent.getProperties().keySet()) {
							if (propName instanceof String) {
								System.out.println("[Duc]" + propName + ": " + nodePresent.getProperty((String)propName));
							}
						}
						*/
						
					}
				}
				
				writer.write(Utils.get(
					m_stxCsv.get(indent, attr_kind, "name"), 
					"", 
					"", 
					m_iClass.getName(),
					objectString,
					"",
					"",
					""
				));	
				
				
			}
		}	
		
	}

}
