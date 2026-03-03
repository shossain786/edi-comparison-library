package com.edi.comparison.cucumber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads folder-path aliases from a YAML config file and resolves them at runtime.
 *
 * <p>Aliases keep feature files readable and environment-agnostic. Instead of
 * hard-coding filesystem paths in Gherkin steps you use short names like
 * {@code CU2100_Inbound} that map to real directories in the config:
 *
 * <pre>
 * # config/edi-locations.yaml
 * CU2100_Inbound:              /opt/edi/inbound/cu2100
 * CA2000_304IFT_Min_Archieve:  /opt/edi/outbound/ca2000/archive
 * </pre>
 *
 * <p>The config file is loaded from the classpath (e.g.
 * {@code src/test/resources/config/edi-locations.yaml}).
 * To use a different path call {@link #LocationRegistry(String)}.
 *
 * <h3>Usage</h3>
 * <pre>
 * LocationRegistry registry = new LocationRegistry();
 * Path inbound = registry.resolve("CU2100_Inbound");
 * </pre>
 */
public class LocationRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocationRegistry.class);

    /** Default classpath path to the locations config file. */
    public static final String DEFAULT_CONFIG = "config/edi-locations.yaml";

    private final Map<String, String> locations;

    /**
     * Loads aliases from the default config path ({@value DEFAULT_CONFIG}).
     */
    public LocationRegistry() {
        this(DEFAULT_CONFIG);
    }

    /**
     * Loads aliases from a custom classpath resource path.
     *
     * @param configResource classpath resource path, e.g. {@code "config/edi-locations.yaml"}
     */
    public LocationRegistry(String configResource) {
        this.locations = loadLocations(configResource);
    }

    /**
     * Resolves an alias to an absolute {@link Path}.
     *
     * @param alias the alias name as defined in the YAML config
     * @return resolved directory path
     * @throws IllegalArgumentException if the alias is not defined
     */
    public Path resolve(String alias) {
        String pathStr = locations.get(alias);
        if (pathStr == null) {
            throw new IllegalArgumentException(
                    "Unknown location alias: '" + alias + "'. "
                    + "Known aliases: " + locations.keySet()
                    + ". Check config/edi-locations.yaml.");
        }
        return Paths.get(pathStr);
    }

    /**
     * Returns {@code true} if the alias is registered in the config.
     */
    public boolean hasAlias(String alias) {
        return locations.containsKey(alias);
    }

    /**
     * Returns all registered aliases (useful for diagnostics).
     */
    public Map<String, String> getAllAliases() {
        return new LinkedHashMap<>(locations);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadLocations(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Location config not found on classpath: '" + resource + "'. "
                        + "Create src/test/resources/" + resource + " with your alias mappings.");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException("Location config is empty: " + resource);
            }
            // Support both flat format and nested under a "locations:" key
            Map<String, Object> rawMap;
            Object locationsNode = data.get("locations");
            if (locationsNode instanceof Map) {
                rawMap = (Map<String, Object>) locationsNode;
            } else {
                rawMap = data;
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            log.info("Loaded {} location alias(es) from {}", result.size(), resource);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read location config: " + resource, e);
        }
    }
}
