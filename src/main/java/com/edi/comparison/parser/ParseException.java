package com.edi.comparison.parser;

/**
 * Exception thrown when file parsing fails.
 * 
 * <p>This exception provides detailed information about parsing errors
 * including line number, position, and the problematic content.
 */
public class ParseException extends Exception {
    
    private final int lineNumber;
    private final String problematicContent;
    
    /**
     * Constructs a new ParseException with a message.
     * 
     * @param message error message
     */
    public ParseException(String message) {
        super(message);
        this.lineNumber = -1;
        this.problematicContent = null;
    }
    
    /**
     * Constructs a new ParseException with a message and cause.
     * 
     * @param message error message
     * @param cause underlying cause
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
        this.lineNumber = -1;
        this.problematicContent = null;
    }
    
    /**
     * Constructs a new ParseException with detailed location information.
     * 
     * @param message error message
     * @param lineNumber line number where error occurred (1-based)
     * @param problematicContent the content that caused the error
     */
    public ParseException(String message, int lineNumber, String problematicContent) {
        super(formatMessage(message, lineNumber, problematicContent));
        this.lineNumber = lineNumber;
        this.problematicContent = problematicContent;
    }
    
    /**
     * Gets the line number where the parsing error occurred.
     * 
     * @return line number (1-based) or -1 if not available
     */
    public int getLineNumber() {
        return lineNumber;
    }
    
    /**
     * Gets the content that caused the parsing error.
     * 
     * @return problematic content or null if not available
     */
    public String getProblematicContent() {
        return problematicContent;
    }
    
    private static String formatMessage(String message, int lineNumber, String content) {
        StringBuilder sb = new StringBuilder(message);
        if (lineNumber > 0) {
            sb.append(" (at line ").append(lineNumber).append(")");
        }
        if (content != null && !content.isEmpty()) {
            String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            sb.append(": ").append(preview);
        }
        return sb.toString();
    }
}
