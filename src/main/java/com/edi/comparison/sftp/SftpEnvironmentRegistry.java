package com.edi.comparison.sftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads SFTP connection settings per environment from a YAML config file.
 *
 * <h3>Active environment — selection priority</h3>
 * <ol>
 *   <li>{@code -Dedi.env=cvt} JVM system property (Maven CLI, CI/CD pipeline)</li>
 *   <li>{@code EDI_ENV=cvt} OS environment variable (persistent shell setting)</li>
 *   <li>{@code active:} field in {@code config/edi-local.yaml} — <b>gitignored per-developer
 *       override</b>, used when running directly from the IDE without any JVM args</li>
 *   <li>{@code active:} field in {@code config/sftp-environments.yaml} — committed team default</li>
 * </ol>
 *
 * <h3>config/sftp-environments.yaml (committed to git)</h3>
 * <pre>
 * active: beta   # team default — each developer overrides via edi-local.yaml
 *
 * environments:
 *   beta:
 *     host:     10.0.1.100
 *     port:     22
 *     username: edi_test
 *     password: ${SFTP_BETA_PASSWORD}
 *
 *   cvt:
 *     host:     10.0.2.100
 *     port:     22
 *     username: edi_test
 *     password: ${SFTP_CVT_PASSWORD}
 *
 *   prod:
 *     host:     10.0.3.100
 *     port:     22
 *     username: edi_test
 *     password: ${SFTP_PROD_PASSWORD}
 * </pre>
 *
 * <h3>config/edi-local.yaml (gitignored — per-developer)</h3>
 * <p>Copy {@code config/edi-local.yaml.template} to {@code config/edi-local.yaml}
 * (which is gitignored) and set your preferred environment:
 * <pre>
 * active: cvt   # your personal default when running from the IDE
 * </pre>
 * This file is only an environment selector — it does not contain credentials.
 *
 * <h3>Password references</h3>
 * <p>{@code ${VAR_NAME}} placeholders in {@code sftp-environments.yaml} are resolved
 * against OS environment variables and JVM system properties at load time.
 * No plaintext credentials are ever committed to the repository.
 *
 * <p>If {@code sftp-environments.yaml} is absent from the classpath,
 * {@link #isAvailable()} returns {@code false} and the library falls back to
 * local filesystem mode automatically.
 */
public class SftpEnvironmentRegistry {

    private static final Logger log = LoggerFactory.getLogger(SftpEnvironmentRegistry.class);

    /** Default classpath location of the SFTP config. */
    public static final String DEFAULT_CONFIG = "config/sftp-environments.yaml";

    /**
     * Gitignored per-developer local override file.
     * Only needs an {@code active:} field — no credentials.
     */
    public static final String LOCAL_OVERRIDE_CONFIG = "config/edi-local.yaml";

    /** JVM system property for active environment: {@code -Dedi.env=cvt}. */
    public static final String ENV_SYSTEM_PROP = "edi.env";

    /** OS environment variable for active environment: {@code EDI_ENV=cvt}. */
    public static final String ENV_VARIABLE = "EDI_ENV";

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, SftpConfig> environments;
    private final String defaultEnv;      // from sftp-environments.yaml active: field
    private final String localOverrideEnv; // from edi-local.yaml active: field (may be null)
    private final boolean available;

    /**
     * Loads from the default config paths:
     * {@value DEFAULT_CONFIG} and {@value LOCAL_OVERRIDE_CONFIG}.
     */
    public SftpEnvironmentRegistry() {
        this(DEFAULT_CONFIG, LOCAL_OVERRIDE_CONFIG);
    }

    /**
     * Loads from custom classpath resource paths.
     *
     * @param configResource       path to the main SFTP environments config
     * @param localOverrideResource path to the gitignored local override file
     */
    @SuppressWarnings("unchecked")
    public SftpEnvironmentRegistry(String configResource, String localOverrideResource) {
        Map<String, SftpConfig> envs = new LinkedHashMap<>();
        String defEnv = "beta";
        String localEnv = null;
        boolean avail = false;

        // 1. Load main config (sftp-environments.yaml)
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(configResource)) {
            if (is != null) {
                avail = true;
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);

                if (data.containsKey("active")) {
                    defEnv = String.valueOf(data.get("active"));
                }

                Map<String, Map<String, Object>> envsData =
                        (Map<String, Map<String, Object>>) data.get("environments");

                if (envsData != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : envsData.entrySet()) {
                        envs.put(entry.getKey(), parseConfig(entry.getKey(), entry.getValue()));
                    }
                }
                log.info("Loaded SFTP config for environments: {}", envs.keySet());
            } else {
                log.debug("No SFTP config at '{}' — using local filesystem mode", configResource);
            }
        } catch (IOException e) {
            log.warn("Cannot read SFTP config '{}': {}", configResource, e.getMessage());
        }

        // 2. Load local override (edi-local.yaml) — gitignored, per-developer
        if (avail && localOverrideResource != null) {
            localEnv = loadLocalOverride(localOverrideResource);
        }

        this.environments     = envs;
        this.defaultEnv       = defEnv;
        this.localOverrideEnv = localEnv;
        this.available        = avail;

        log.info("Active SFTP environment: {} (resolved from: {})",
                getActiveEnvironmentName(), resolveSource());
    }

    /**
     * Returns {@code true} if the SFTP config file was found on the classpath.
     * When {@code false}, the library operates in local filesystem mode.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the name of the active environment using the following priority:
     * <ol>
     *   <li>{@code -Dedi.env} JVM system property</li>
     *   <li>{@code EDI_ENV} OS environment variable</li>
     *   <li>{@code active:} in {@code config/edi-local.yaml} (gitignored per-developer file)</li>
     *   <li>{@code active:} in {@code config/sftp-environments.yaml} (committed team default)</li>
     * </ol>
     */
    public String getActiveEnvironmentName() {
        String env = System.getProperty(ENV_SYSTEM_PROP);
        if (env == null || env.isBlank()) env = System.getenv(ENV_VARIABLE);
        if (env == null || env.isBlank()) env = localOverrideEnv;
        if (env == null || env.isBlank()) env = defaultEnv;
        return env;
    }

    /** Returns a short description of which source resolved the active environment (for logging). */
    private String resolveSource() {
        if (System.getProperty(ENV_SYSTEM_PROP) != null) return "-D" + ENV_SYSTEM_PROP;
        if (System.getenv(ENV_VARIABLE) != null)          return ENV_VARIABLE + " env var";
        if (localOverrideEnv != null)                     return LOCAL_OVERRIDE_CONFIG;
        return DEFAULT_CONFIG + " active: field";
    }

    /**
     * Returns the {@link SftpConfig} for the currently active environment.
     *
     * @throws IllegalArgumentException if the active environment is not defined in config
     */
    public SftpConfig getActiveConfig() {
        return getConfig(getActiveEnvironmentName());
    }

    /**
     * Returns the {@link SftpConfig} for the named environment.
     *
     * @param env environment name (e.g. "beta", "cvt", "prod")
     * @throws IllegalArgumentException if the environment is not defined in config
     */
    public SftpConfig getConfig(String env) {
        SftpConfig config = environments.get(env);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Unknown SFTP environment: '" + env + "'. "
                    + "Known environments: " + environments.keySet() + ". "
                    + "Set -D" + ENV_SYSTEM_PROP + "=<env> or the "
                    + ENV_VARIABLE + " environment variable.");
        }
        return config;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Reads the {@code active:} field from the local override YAML file.
     * Returns {@code null} if the file is absent — absence is expected and not an error.
     */
    private String loadLocalOverride(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null || !data.containsKey("active")) return null;
            String env = String.valueOf(data.get("active")).trim();
            log.info("Local override active env from {}: '{}'", resourcePath, env);
            return env;
        } catch (IOException e) {
            log.warn("Cannot read local override '{}': {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private SftpConfig parseConfig(String envName, Map<String, Object> data) {
        return SftpConfig.builder(envName)
                .host(resolveValue(data, "host"))
                .port(parsePort(data.get("port")))
                .username(resolveValue(data, "username"))
                .password(resolveValue(data, "password"))
                .build();
    }

    private String resolveValue(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return null;
        return expandEnvVars(String.valueOf(val));
    }

    private int parsePort(Object val) {
        if (val == null) return 22;
        if (val instanceof Integer i) return i;
        try { return Integer.parseInt(String.valueOf(val)); }
        catch (NumberFormatException e) { return 22; }
    }

    /**
     * Replaces {@code ${VAR_NAME}} with the corresponding OS environment variable
     * or JVM system property. If neither is set, replaces with an empty string
     * and logs a warning.
     */
    private String expandEnvVars(String value) {
        if (value == null || !value.contains("${")) return value;
        Matcher m = ENV_VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String resolved = System.getenv(varName);
            if (resolved == null) resolved = System.getProperty(varName);
            if (resolved == null) {
                log.warn("Environment variable '{}' is not set — credential will be empty", varName);
                resolved = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
