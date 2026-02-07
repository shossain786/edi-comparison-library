package com.edi.comparison.rule;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Loads comparison rules from YAML configuration files.
 * 
 * <p>Supports loading from:
 * <ul>
 *   <li>File path</li>
 *   <li>Classpath resource</li>
 *   <li>InputStream</li>
 *   <li>String content</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * RuleLoader loader = new RuleLoader();
 * RuleSet rules = loader.loadFromFile("rules/iftmbf-comparison.yaml");
 * </pre>
 */
public class RuleLoader {
    
    private final Yaml yaml;
    
    public RuleLoader() {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(RuleSet.class, loaderOptions);
        this.yaml = new Yaml(constructor);
    }
    
    /**
     * Loads rules from a file path.
     * 
     * @param filePath path to YAML file
     * @return loaded RuleSet
     * @throws IOException if file cannot be read
     * @throws RuleLoadException if YAML is invalid
     */
    public RuleSet loadFromFile(String filePath) throws IOException, RuleLoadException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Rule file not found: " + filePath);
        }
        
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            return loadFromInputStream(inputStream);
        }
    }
    
    /**
     * Loads rules from a classpath resource.
     * 
     * @param resourcePath classpath resource path (e.g., "rules/default.yaml")
     * @return loaded RuleSet
     * @throws IOException if resource cannot be read
     * @throws RuleLoadException if YAML is invalid
     */
    public RuleSet loadFromResource(String resourcePath) throws IOException, RuleLoadException {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }
        
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        
        try {
            return loadFromInputStream(inputStream);
        } finally {
            inputStream.close();
        }
    }
    
    /**
     * Loads rules from an InputStream.
     * 
     * @param inputStream input stream to read from
     * @return loaded RuleSet
     * @throws RuleLoadException if YAML is invalid
     */
    public RuleSet loadFromInputStream(InputStream inputStream) throws RuleLoadException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        
        try {
            RuleSet ruleSet = yaml.load(inputStream);
            if (ruleSet == null) {
                throw new RuleLoadException("Failed to parse YAML - file is empty or invalid");
            }
            validateRuleSet(ruleSet);
            return ruleSet;
        } catch (Exception e) {
            throw new RuleLoadException("Failed to load rules: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads rules from YAML string content.
     * 
     * @param yamlContent YAML content as string
     * @return loaded RuleSet
     * @throws RuleLoadException if YAML is invalid
     */
    public RuleSet loadFromString(String yamlContent) throws RuleLoadException {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("YAML content cannot be null or empty");
        }
        
        try {
            RuleSet ruleSet = yaml.load(yamlContent);
            if (ruleSet == null) {
                throw new RuleLoadException("Failed to parse YAML - content is empty or invalid");
            }
            validateRuleSet(ruleSet);
            return ruleSet;
        } catch (Exception e) {
            throw new RuleLoadException("Failed to load rules from string: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates that the loaded RuleSet is complete and valid.
     * 
     * @param ruleSet ruleset to validate
     * @throws RuleLoadException if validation fails
     */
    private void validateRuleSet(RuleSet ruleSet) throws RuleLoadException {
        if (ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
            throw new RuleLoadException("RuleSet must contain at least one rule");
        }
        
        for (int i = 0; i < ruleSet.getRules().size(); i++) {
            ComparisonRule rule = ruleSet.getRules().get(i);
            if (rule.getSegment() == null || rule.getSegment().trim().isEmpty()) {
                throw new RuleLoadException("Rule at index " + i + " must specify a segment");
            }
            
            // Validate field rules
            if (rule.getFields() != null) {
                for (int j = 0; j < rule.getFields().size(); j++) {
                    FieldRule fieldRule = rule.getFields().get(j);
                    if (fieldRule.getPosition() == null || fieldRule.getPosition().trim().isEmpty()) {
                        throw new RuleLoadException(
                            "Field rule at index " + j + " in segment " + rule.getSegment() + 
                            " must specify a position"
                        );
                    }
                }
            }
        }
    }
    
    /**
     * Exception thrown when rule loading fails.
     */
    public static class RuleLoadException extends Exception {
        public RuleLoadException(String message) {
            super(message);
        }
        
        public RuleLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
