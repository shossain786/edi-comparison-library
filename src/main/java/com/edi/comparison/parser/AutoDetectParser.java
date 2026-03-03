package com.edi.comparison.parser;

import com.edi.comparison.model.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Parser that auto-detects the EDI format from content and delegates to the
 * appropriate parser (EDIFACT, ANSI X12, or XML).
 *
 * <p>Detection rules:
 * <ul>
 *   <li>EDIFACT — content starts with {@code UNA} or {@code UNB}</li>
 *   <li>ANSI X12 — content starts with {@code ISA}</li>
 *   <li>XML — content starts with {@code <} or {@code <?}</li>
 * </ul>
 *
 * <p>This parser is stateless and thread-safe.
 *
 * <p>Usage (typically via EdiVerifier):
 * <pre>
 * FileParser parser = new AutoDetectParser();
 * Message msg = parser.parse(new File("booking.edi")); // format detected automatically
 * </pre>
 */
public class AutoDetectParser implements FileParser {

    private static final Logger log = LoggerFactory.getLogger(AutoDetectParser.class);

    private final EdifactParser edifactParser = new EdifactParser();
    private final AnsiX12Parser ansiX12Parser = new AnsiX12Parser();
    private final XmlParser xmlParser = new XmlParser();

    @Override
    public Message parse(File file) throws IOException, ParseException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return parse(content);
    }

    @Override
    public Message parse(String content) throws ParseException {
        if (content == null || content.isBlank()) {
            throw new ParseException("Content is empty or null");
        }
        String trimmed = content.stripLeading();
        if (edifactParser.canParse(trimmed)) {
            log.debug("Detected format: EDIFACT");
            return edifactParser.parse(content);
        }
        if (ansiX12Parser.canParse(trimmed)) {
            log.debug("Detected format: ANSI X12");
            return ansiX12Parser.parse(content);
        }
        if (xmlParser.canParse(trimmed)) {
            log.debug("Detected format: XML");
            return xmlParser.parse(content);
        }
        String preview = trimmed.substring(0, Math.min(50, trimmed.length()));
        log.warn("Cannot detect EDI format. Content starts with: '{}'", preview);
        throw new ParseException("Cannot detect EDI format. Content starts with: " + preview);
    }

    @Override
    public Message parse(InputStream inputStream) throws IOException, ParseException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        return parse(content);
    }

    @Override
    public boolean canParse(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.stripLeading();
        return edifactParser.canParse(trimmed)
                || ansiX12Parser.canParse(trimmed)
                || xmlParser.canParse(trimmed);
    }
}
