package com.edi.comparison.parser;

import com.edi.comparison.model.Message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for parsing EDI and XML files into Message objects.
 * 
 * <p>All file format parsers (EDIFACT, ANSI X12, XML) implement this interface
 * to provide a consistent API for converting raw file content into structured
 * Message objects.
 * 
 * <p>Implementations should be stateless and thread-safe.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * FileParser parser = new EdifactParser();
 * Message message = parser.parse(new File("booking.edi"));
 * 
 * // Or from String
 * Message message = parser.parse(fileContent);
 * </pre>
 */
public interface FileParser {
    
    /**
     * Parses a file into a Message object.
     * 
     * @param file file to parse
     * @return parsed Message
     * @throws IOException if file cannot be read
     * @throws ParseException if file content is invalid
     */
    Message parse(File file) throws IOException, ParseException;
    
    /**
     * Parses file content from a String into a Message object.
     * 
     * @param content file content as String
     * @return parsed Message
     * @throws ParseException if content is invalid
     */
    Message parse(String content) throws ParseException;
    
    /**
     * Parses file content from an InputStream into a Message object.
     * 
     * @param inputStream input stream to read from
     * @return parsed Message
     * @throws IOException if stream cannot be read
     * @throws ParseException if content is invalid
     */
    Message parse(InputStream inputStream) throws IOException, ParseException;
    
    /**
     * Validates if the given content can be parsed by this parser.
     * Does not throw exceptions - returns false if invalid.
     * 
     * @param content content to validate
     * @return true if content can be parsed by this parser
     */
    boolean canParse(String content);
}
