/**
 * Core module of EDI Comparison Library.
 * 
 * <p>This package contains the main API facade that users interact with.
 * The library follows a layered architecture with clear separation of concerns:
 * 
 * <pre>
 * Layer 1: Core API (this package)
 *   - FileComparator: Main entry point for users
 *   - ComparisonResult: Result holder with report generation
 * 
 * Layer 2: Rule Engine (rule package)
 *   - RuleLoader: Loads rules from YAML configs
 *   - RuleValidator: Validates segments against rules
 * 
 * Layer 3: Parsers (parser package)
 *   - EdifactParser: Parses EDIFACT files
 *   - AnsiX12Parser: Parses ANSI X12 files
 *   - XmlParser: Parses XML files
 * 
 * Layer 4: Validators (validator package)
 *   - FieldValidator: Interface for custom validators
 *   - Built-in validators: ExactMatch, PatternMatch, DateFormat
 * 
 * Layer 5: Reporting (report package)
 *   - HtmlReportGenerator: Generates HTML diff reports
 *   - JsonReportGenerator: Generates JSON reports
 * </pre>
 * 
 * <p><b>Design Principles:</b>
 * <ul>
 *   <li>Loose coupling through interfaces</li>
 *   <li>Dependency injection for testability</li>
 *   <li>Immutable models where possible</li>
 *   <li>Fail-safe: Collect all errors instead of failing fast</li>
 * </ul>
 * 
 * @author EDI Comparison Team
 * @version 1.0.0
 */
package com.edi.comparison.core;
