package com.edi.comparison.parser;

import com.edi.comparison.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for ANSI X12 (American National Standards Institute X12) files.
 * 
 * <p>ANSI X12 format characteristics:
 * <ul>
 *   <li>Segment delimiter: ~ (tilde)</li>
 *   <li>Element delimiter: * (asterisk)</li>
 *   <li>Component delimiter: : (colon) or > (greater than)</li>
 *   <li>Example: ISA*00*          *00*          *ZZ*SENDER         *~</li>
 * </ul>
 * 
 * <p>This parser is stateless and thread-safe.
 */
public class AnsiX12Parser implements FileParser {
    
    private static final String DEFAULT_SEGMENT_DELIMITER = "~";
    private static final String DEFAULT_ELEMENT_DELIMITER = "\\*";
    private static final String DEFAULT_COMPONENT_DELIMITER = ":";
    
    @Override
    public Message parse(File file) throws IOException, ParseException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + file);
        }
        
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Message message = parse(content);
        
        // Set source file path
        return Message.builder()
            .fileFormat(message.getFileFormat())
            .messageType(message.getMessageType())
            .addSegments(message.getSegments())
            .addMetadata(message.getMetadata())
            .sourceFilePath(file.getAbsolutePath())
            .build();
    }
    
    @Override
    public Message parse(String content) throws ParseException {
        if (content == null || content.trim().isEmpty()) {
            throw new ParseException("Content is empty");
        }
        
        // Split by segment delimiter
        String[] segments = content.split(DEFAULT_SEGMENT_DELIMITER);
        
        List<Segment> parsedSegments = new ArrayList<>();
        String messageType = null;
        int lineNumber = 1;
        int sequenceNumber = 0;
        
        for (String segmentStr : segments) {
            String trimmed = segmentStr.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            try {
                Segment segment = parseSegment(trimmed, lineNumber, sequenceNumber);
                parsedSegments.add(segment);
                
                // Extract message type from ST segment
                if (segment.getTag().equals("ST") && messageType == null) {
                    messageType = segment.getFieldValue("ST.0001");
                }
                
                sequenceNumber++;
                lineNumber++;
                
            } catch (Exception e) {
                throw new ParseException("Failed to parse segment", lineNumber, trimmed);
            }
        }
        
        if (parsedSegments.isEmpty()) {
            throw new ParseException("No valid segments found in content");
        }
        
        return Message.builder()
            .fileFormat(FileFormat.ANSI_X12)
            .messageType(messageType)
            .addSegments(parsedSegments)
            .build();
    }
    
    @Override
    public Message parse(InputStream inputStream) throws IOException, ParseException {
        if (inputStream == null) {
            throw new IOException("InputStream is null");
        }
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return parse(content.toString());
    }
    
    @Override
    public boolean canParse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // ANSI X12 typically starts with ISA
        String trimmed = content.trim();
        return trimmed.startsWith("ISA") || content.contains("~");
    }
    
    /**
     * Parses a single segment string into a Segment object.
     * 
     * @param segmentStr raw segment string (e.g., "ST*214*0001")
     * @param lineNumber line number in source file
     * @param sequenceNumber sequence number in message
     * @return parsed Segment
     */
    private Segment parseSegment(String segmentStr, int lineNumber, int sequenceNumber) {
        // Split by element delimiter
        String[] elements = segmentStr.split(DEFAULT_ELEMENT_DELIMITER);
        
        if (elements.length == 0) {
            throw new IllegalArgumentException("Empty segment");
        }
        
        String tag = elements[0].trim();
        Segment.Builder segmentBuilder = Segment.builder()
            .tag(tag)
            .lineNumber(lineNumber)
            .sequenceNumber(sequenceNumber)
            .rawContent(segmentStr);
        
        // Parse fields from elements (skip first element which is the tag)
        for (int i = 1; i < elements.length; i++) {
            String element = elements[i];
            
            // Check if element has components (separated by : or >)
            if (element.contains(":") || element.contains(">")) {
                String delimiter = element.contains(":") ? ":" : ">";
                String[] components = element.split(delimiter);
                for (int j = 0; j < components.length; j++) {
                    String position = String.format("%s.C%02d.%02d", tag, i, j + 1);
                    Field field = Field.builder()
                        .position(position)
                        .value(components[j].trim())
                        .lineNumber(lineNumber)
                        .build();
                    segmentBuilder.addField(field);
                }
            } else {
                // Simple element without components
                String position = String.format("%s.%04d", tag, i);
                Field field = Field.builder()
                    .position(position)
                    .value(element.trim())
                    .lineNumber(lineNumber)
                    .build();
                segmentBuilder.addField(field);
            }
        }
        
        return segmentBuilder.build();
    }
}
