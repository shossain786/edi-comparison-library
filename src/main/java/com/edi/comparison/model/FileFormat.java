package com.edi.comparison.model;

/**
 * Supported file formats for comparison.
 * 
 * <p>This enum defines the types of files that can be parsed and compared
 * by the library. Each format may have different parsing rules and delimiters.
 */
public enum FileFormat {
    
    /**
     * EDIFACT (Electronic Data Interchange For Administration, Commerce and Transport).
     * Common segments: UNH, BGM, NAD, DTM, UNT
     * Segment delimiter: ' (apostrophe)
     * Element delimiter: + (plus)
     * Component delimiter: : (colon)
     */
    EDIFACT("EDIFACT", "edi", "'", "+", ":"),
    
    /**
     * ANSI X12 (American National Standards Institute X12).
     * Common segments: ISA, GS, ST, SE, GE, IEA
     * Segment delimiter: ~ (tilde)
     * Element delimiter: * (asterisk)
     * Component delimiter: : (colon) or > (greater than)
     */
    ANSI_X12("ANSI X12", "x12", "~", "*", ":"),
    
    /**
     * XML (Extensible Markup Language).
     * Standard XML structure with tags and attributes.
     */
    XML("XML", "xml", null, null, null);
    
    private final String displayName;
    private final String fileExtension;
    private final String segmentDelimiter;
    private final String elementDelimiter;
    private final String componentDelimiter;
    
    FileFormat(String displayName, String fileExtension, 
               String segmentDelimiter, String elementDelimiter, 
               String componentDelimiter) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
        this.segmentDelimiter = segmentDelimiter;
        this.elementDelimiter = elementDelimiter;
        this.componentDelimiter = componentDelimiter;
    }
    
    /**
     * Gets the human-readable display name.
     * 
     * @return display name (e.g., "EDIFACT", "ANSI X12")
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the typical file extension for this format.
     * 
     * @return file extension without dot (e.g., "edi", "x12", "xml")
     */
    public String getFileExtension() {
        return fileExtension;
    }
    
    /**
     * Gets the segment delimiter used in this format.
     * 
     * @return segment delimiter or null for XML
     */
    public String getSegmentDelimiter() {
        return segmentDelimiter;
    }
    
    /**
     * Gets the element delimiter used in this format.
     * 
     * @return element delimiter or null for XML
     */
    public String getElementDelimiter() {
        return elementDelimiter;
    }
    
    /**
     * Gets the component delimiter used in this format.
     * 
     * @return component delimiter or null for XML
     */
    public String getComponentDelimiter() {
        return componentDelimiter;
    }
    
    /**
     * Checks if this format is EDI-based (EDIFACT or ANSI X12).
     * 
     * @return true if EDI format, false if XML
     */
    public boolean isEdiFormat() {
        return this == EDIFACT || this == ANSI_X12;
    }
    
    /**
     * Detects file format based on file extension or content.
     * 
     * @param filename the filename to analyze
     * @return detected FileFormat or null if cannot determine
     */
    public static FileFormat fromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        String lower = filename.toLowerCase();
        
        if (lower.endsWith(".edi") || lower.endsWith(".edifact")) {
            return EDIFACT;
        } else if (lower.endsWith(".x12") || lower.endsWith(".ansi")) {
            return ANSI_X12;
        } else if (lower.endsWith(".xml")) {
            return XML;
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
