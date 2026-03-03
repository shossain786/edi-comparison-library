package com.edi.comparison.sftp;

/**
 * SFTP connection parameters for a single environment (beta, cvt, prod, etc.).
 *
 * <p>Instances are created by {@link SftpEnvironmentRegistry} from the YAML config.
 * Credentials are resolved from environment variables at load time — passwords are
 * never stored as literals in config files.
 */
public class SftpConfig {

    private final String environment;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private SftpConfig(Builder b) {
        this.environment = b.environment;
        this.host        = b.host;
        this.port        = b.port;
        this.username    = b.username;
        this.password    = b.password;
    }

    /** Environment name this config belongs to (e.g. "beta", "cvt", "prod"). */
    public String getEnvironment() { return environment; }

    /** SFTP server hostname or IP address. */
    public String getHost()        { return host; }

    /** SFTP port — defaults to 22. */
    public int getPort()           { return port; }

    /** Username used to authenticate. */
    public String getUsername()    { return username; }

    /** Password used to authenticate (already resolved from env var). */
    public String getPassword()    { return password; }

    @Override
    public String toString() {
        return username + "@" + host + ":" + port + " [env=" + environment + "]";
    }

    // =========================================================================
    // Builder
    // =========================================================================

    static Builder builder(String environment) {
        return new Builder(environment);
    }

    static class Builder {
        private final String environment;
        private String host;
        private int port = 22;
        private String username;
        private String password;

        Builder(String environment) { this.environment = environment; }
        Builder host(String h)      { this.host = h;        return this; }
        Builder port(int p)         { this.port = p;        return this; }
        Builder username(String u)  { this.username = u;    return this; }
        Builder password(String p)  { this.password = p;    return this; }
        SftpConfig build()          { return new SftpConfig(this); }
    }
}
