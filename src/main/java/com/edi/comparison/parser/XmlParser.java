package com.edi.comparison.parser;

import com.edi.comparison.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for XML files.
 * 
 * <p>This parser converts XML documents into the same Message/Segment/Field
 * structure used by EDI parsers, allowing uniform comparison logic.
 * 
 * <p>Mapping:
 * <ul>
 *   <li>Root element name -> Message type</li>
 *   <li>Child elements -> Segments (tag = element name)</li>
 *   <li>Element text/attributes -> Fields</li>
 * </ul>
 * 
 * <p>Example:
 * <pre>
 * &lt;Booking&gt;
 *   &lt;BGM&gt;
 *     &lt;DocumentCode&gt;340&lt;/DocumentCode&gt;
 *   &lt;/BGM&gt;
 * &lt;/Booking&gt;
 * 
 * Becomes:
 * - Message type: "Booking"
 * - Segment: tag="BGM"
 * - Field: position="BGM.DocumentCode", value="340"
 * </pre>
 */
public class XmlParser implements FileParser {
    
    private final DocumentBuilderFactory factory;
    
    public XmlParser() {
        this.factory = DocumentBuilderFactory.newInstance();
        // Disable external entities for security
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            // Features not supported, continue anyway
        }
    }
    
    @Override
    public Message parse(File file) throws IOException, ParseException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + file);
        }
        
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();
            
            Message message = parseDocument(document);
            
            // Set source file path
            return message.toBuilder()
                .sourceFilePath(file.getAbsolutePath())
                .build();
                
        } catch (Exception e) {
            throw new ParseException("Failed to parse XML file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Message parse(String content) throws ParseException {
        if (content == null || content.trim().isEmpty()) {
            throw new ParseException("Content is empty");
        }
        
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            
            return parseDocument(document);
            
        } catch (Exception e) {
            throw new ParseException("Failed to parse XML content: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Message parse(InputStream inputStream) throws IOException, ParseException {
        if (inputStream == null) {
            throw new IOException("InputStream is null");
        }
        
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            
            return parseDocument(document);
            
        } catch (Exception e) {
            throw new ParseException("Failed to parse XML stream: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean canParse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = content.trim();
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<");
    }
    
    /**
     * Parses a DOM Document into a Message object.
     */
    private Message parseDocument(Document document) {
        Element root = document.getDocumentElement();
        String messageType = root.getNodeName();
        
        Message.Builder messageBuilder = Message.builder()
            .fileFormat(FileFormat.XML)
            .messageType(messageType);
        
        // Parse child elements as segments
        int sequenceNumber = 0;
        NodeList children = root.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Segment segment = parseElement(element, sequenceNumber);
                messageBuilder.addSegment(segment);
                sequenceNumber++;
            }
        }
        
        return messageBuilder.build();
    }
    
    /**
     * Parses an XML element into a Segment.
     */
    private Segment parseElement(Element element, int sequenceNumber) {
        String tag = element.getNodeName();
        
        Segment.Builder segmentBuilder = Segment.builder()
            .tag(tag)
            .sequenceNumber(sequenceNumber);
        
        // Parse attributes as fields
        if (element.hasAttributes()) {
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                Node attr = element.getAttributes().item(i);
                String position = tag + "[@" + attr.getNodeName() + "]";
                Field field = Field.builder()
                    .position(position)
                    .value(attr.getNodeValue())
                    .name(attr.getNodeName())
                    .build();
                segmentBuilder.addField(field);
            }
        }
        
        // Parse child elements and text as fields
        parseElementContent(element, tag, segmentBuilder);
        
        return segmentBuilder.build();
    }
    
    /**
     * Recursively parses element content into fields.
     */
    private void parseElementContent(Element element, String parentPath, Segment.Builder segmentBuilder) {
        NodeList children = element.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String childName = childElement.getNodeName();
                String position = parentPath + "." + childName;
                
                // If element has no children, treat its text as a field value
                if (!hasElementChildren(childElement)) {
                    String value = childElement.getTextContent().trim();
                    Field field = Field.builder()
                        .position(position)
                        .value(value)
                        .name(childName)
                        .build();
                    segmentBuilder.addField(field);
                } else {
                    // Recursively parse nested elements
                    parseElementContent(childElement, position, segmentBuilder);
                }
            }
        }
    }
    
    /**
     * Checks if an element has child elements (not just text).
     */
    private boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }
}
