package com.edi.comparison.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete set of comparison rules for a message type.
 * 
 * <p>Typically loaded from a YAML configuration file.
 * 
 * <p>Example YAML structure:
 * <pre>
 * message_type: IFTMBF
 * description: Booking confirmation comparison
 * rules:
 *   - segment: BGM
 *     fields:
 *       - position: BGM.C002.1001
 *         validation: exact_match
 * </pre>
 */
public class RuleSet {
    
    private String messageType;
    private String description;
    private List<ComparisonRule> rules;
    private Map<String, Object> config;
    
    public RuleSet() {
        this.rules = new ArrayList<>();
        this.config = new HashMap<>();
    }
    
    /**
     * Gets the message type this ruleset applies to.
     * 
     * @return message type (e.g., "IFTMBF", "214")
     */
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    /**
     * Gets the description of this ruleset.
     * 
     * @return description
     */
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Gets all comparison rules in this set.
     * 
     * @return list of rules
     */
    public List<ComparisonRule> getRules() {
        return rules;
    }
    
    public void setRules(List<ComparisonRule> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }
    
    /**
     * Gets additional configuration options.
     * 
     * @return configuration map
     */
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : new HashMap<>();
    }
    
    /**
     * Adds a comparison rule to this set.
     * 
     * @param rule rule to add
     */
    public void addRule(ComparisonRule rule) {
        if (rule != null) {
            this.rules.add(rule);
        }
    }
    
    /**
     * Gets a rule for a specific segment tag.
     * 
     * @param segmentTag segment tag to find
     * @return ComparisonRule or null if not found
     */
    public ComparisonRule getRuleForSegment(String segmentTag) {
        return rules.stream()
            .filter(r -> segmentTag.equals(r.getSegment()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if this ruleset has a rule for the given segment.
     * 
     * @param segmentTag segment tag to check
     * @return true if rule exists
     */
    public boolean hasRuleForSegment(String segmentTag) {
        return getRuleForSegment(segmentTag) != null;
    }
    
    /**
     * Gets the number of rules in this set.
     * 
     * @return rule count
     */
    public int getRuleCount() {
        return rules.size();
    }
    
    @Override
    public String toString() {
        return "RuleSet{" +
                "messageType='" + messageType + '\'' +
                ", description='" + description + '\'' +
                ", rules=" + rules.size() +
                '}';
    }
}
