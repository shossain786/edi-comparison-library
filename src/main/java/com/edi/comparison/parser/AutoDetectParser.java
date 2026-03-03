package com.edi.comparison.parser;

import com.edi.comparison.model.Message;

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
            return edifactParser.parse(content);
        }
        if (ansiX12Parser.canParse(trimmed)) {
            return ansiX12Parser.parse(content);
        }
        if (xmlParser.canParse(trimmed)) {
            return xmlParser.parse(content);
        }
        throw new ParseException(
                "Cannot detect EDI format. Content starts with: " +
                trimmed.substring(0, Math.min(50, trimmed.length())));
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
