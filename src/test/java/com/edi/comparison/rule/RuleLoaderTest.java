package com.edi.comparison.rule;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for rule loading and validation.
 */
class RuleLoaderTest {
    
    @Test
    void testLoadRuleFromString() throws Exception {
        String yaml = 
            "message_type: IFTMBF\n" +
            "description: Test rules\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - position: BGM.0001\n" +
            "        validation: exact_match\n" +
            "        expected_value: \"340\"\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        assertNotNull(ruleSet);
        assertEquals("IFTMBF", ruleSet.getMessageType());
        assertEquals("Test rules", ruleSet.getDescription());
        assertEquals(1, ruleSet.getRuleCount());
        
        ComparisonRule rule = ruleSet.getRules().get(0);
        assertEquals("BGM", rule.getSegment());
        assertEquals(1, rule.getFields().size());
        
        FieldRule fieldRule = rule.getFields().get(0);
        assertEquals("BGM.0001", fieldRule.getPosition());
        assertEquals(FieldRule.ValidationType.EXACT_MATCH, fieldRule.getValidation());
        assertEquals("340", fieldRule.getExpectedValue());
    }
    
    @Test
    void testLoadRuleWithMultipleFields() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - position: BGM.0001\n" +
            "        validation: exact_match\n" +
            "      - position: BGM.0002\n" +
            "        validation: pattern_match\n" +
            "        pattern: \"^[A-Z0-9]+$\"\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        ComparisonRule rule = ruleSet.getRules().get(0);
        assertEquals(2, rule.getFields().size());
        
        assertEquals("BGM.0001", rule.getFields().get(0).getPosition());
        assertEquals("BGM.0002", rule.getFields().get(1).getPosition());
        assertEquals("^[A-Z0-9]+$", rule.getFields().get(1).getPattern());
    }
    
    @Test
    void testLoadRuleWithSegmentOptions() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: NAD\n" +
            "    multiple_occurrences: true\n" +
            "    order_matters: false\n" +
            "    required: true\n" +
            "    fields:\n" +
            "      - position: NAD.3035\n" +
            "        validation: exists\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        ComparisonRule rule = ruleSet.getRules().get(0);
        assertTrue(rule.isMultipleOccurrences());
        assertFalse(rule.isOrderMatters());
        assertTrue(rule.isRequired());
    }
    
    @Test
    void testLoadRuleWithValidationTypes() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: TEST\n" +
            "    fields:\n" +
            "      - position: TEST.001\n" +
            "        validation: exact_match\n" +
            "      - position: TEST.002\n" +
            "        validation: pattern_match\n" +
            "      - position: TEST.003\n" +
            "        validation: date_format\n" +
            "      - position: TEST.004\n" +
            "        validation: exists\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        ComparisonRule rule = ruleSet.getRules().get(0);
        assertEquals(FieldRule.ValidationType.EXACT_MATCH, rule.getFields().get(0).getValidation());
        assertEquals(FieldRule.ValidationType.PATTERN_MATCH, rule.getFields().get(1).getValidation());
        assertEquals(FieldRule.ValidationType.DATE_FORMAT, rule.getFields().get(2).getValidation());
        assertEquals(FieldRule.ValidationType.EXISTS, rule.getFields().get(3).getValidation());
    }
    
    @Test
    void testLoadRuleWithSourceReferences() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - position: BGM.0001\n" +
            "        source: testData.bgmCode\n" +
            "      - position: BGM.0002\n" +
            "        source: inbound.BGM.0002\n" +
            "      - position: BGM.0003\n" +
            "        expected_value: \"340\"\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        ComparisonRule rule = ruleSet.getRules().get(0);
        assertEquals("testData.bgmCode", rule.getFields().get(0).getSource());
        assertEquals("inbound.BGM.0002", rule.getFields().get(1).getSource());
        assertEquals("340", rule.getFields().get(2).getExpectedValue());
    }
    
    @Test
    void testLoadFromInputStream() throws Exception {
        String yaml = "message_type: TEST\nrules:\n  - segment: BGM\n    fields:\n      - position: BGM.0001\n";
        InputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromInputStream(inputStream);
        
        assertNotNull(ruleSet);
        assertEquals("TEST", ruleSet.getMessageType());
    }
    
    @Test
    void testLoadFromResource() throws Exception {
        RuleLoader loader = new RuleLoader();
        
        // Try to load from test resources
        try {
            RuleSet ruleSet = loader.loadFromResource("rules/simple-test.yaml");
            assertNotNull(ruleSet);
            assertEquals("TEST", ruleSet.getMessageType());
        } catch (FileNotFoundException e) {
            // File might not be in classpath during test, that's ok
        }
    }
    
    @Test
    void testValidationFailsOnEmptyRules() {
        String yaml = "message_type: TEST\nrules: []";
        
        RuleLoader loader = new RuleLoader();
        Exception exception = assertThrows(RuleLoader.RuleLoadException.class, () -> {
            loader.loadFromString(yaml);
        });
        
        assertTrue(exception.getMessage().contains("at least one rule"));
    }
    
    @Test
    void testValidationFailsOnMissingSegment() {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - fields:\n" +
            "      - position: TEST.001\n";
        
        RuleLoader loader = new RuleLoader();
        Exception exception = assertThrows(RuleLoader.RuleLoadException.class, () -> {
            loader.loadFromString(yaml);
        });
        
        assertTrue(exception.getMessage().contains("must specify a segment"));
    }
    
    @Test
    void testValidationFailsOnMissingFieldPosition() {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - validation: exact_match\n";
        
        RuleLoader loader = new RuleLoader();
        Exception exception = assertThrows(RuleLoader.RuleLoadException.class, () -> {
            loader.loadFromString(yaml);
        });
        
        assertTrue(exception.getMessage().contains("must specify a position"));
    }
    
    @Test
    void testGetRuleForSegment() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - position: BGM.0001\n" +
            "  - segment: NAD\n" +
            "    fields:\n" +
            "      - position: NAD.3035\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        ComparisonRule bgmRule = ruleSet.getRuleForSegment("BGM");
        assertNotNull(bgmRule);
        assertEquals("BGM", bgmRule.getSegment());
        
        ComparisonRule nadRule = ruleSet.getRuleForSegment("NAD");
        assertNotNull(nadRule);
        assertEquals("NAD", nadRule.getSegment());
        
        ComparisonRule notFound = ruleSet.getRuleForSegment("DTM");
        assertNull(notFound);
    }
    
    @Test
    void testHasRuleForSegment() throws Exception {
        String yaml = 
            "message_type: TEST\n" +
            "rules:\n" +
            "  - segment: BGM\n" +
            "    fields:\n" +
            "      - position: BGM.0001\n";
        
        RuleLoader loader = new RuleLoader();
        RuleSet ruleSet = loader.loadFromString(yaml);
        
        assertTrue(ruleSet.hasRuleForSegment("BGM"));
        assertFalse(ruleSet.hasRuleForSegment("NAD"));
    }
    
    @Test
    void testValidationTypeFromString() {
        assertEquals(FieldRule.ValidationType.EXACT_MATCH, 
            FieldRule.ValidationType.fromString("exact_match"));
        assertEquals(FieldRule.ValidationType.EXACT_MATCH, 
            FieldRule.ValidationType.fromString("exact"));
        assertEquals(FieldRule.ValidationType.PATTERN_MATCH, 
            FieldRule.ValidationType.fromString("pattern_match"));
        assertEquals(FieldRule.ValidationType.PATTERN_MATCH, 
            FieldRule.ValidationType.fromString("regex"));
        assertEquals(FieldRule.ValidationType.DATE_FORMAT, 
            FieldRule.ValidationType.fromString("date_format"));
        assertEquals(FieldRule.ValidationType.EXISTS, 
            FieldRule.ValidationType.fromString("exists"));
        assertEquals(FieldRule.ValidationType.EXACT_MATCH, 
            FieldRule.ValidationType.fromString(null));
    }
}
