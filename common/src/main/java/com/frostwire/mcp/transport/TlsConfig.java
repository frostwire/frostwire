package com.frostwire.mcp.transport;

public class TlsConfig {
    private boolean enabled;
    private String keystorePath;
    private String keystorePassword;
    private boolean autoGenerateCert;

    public TlsConfig() {
        this.enabled = false;
        this.autoGenerateCert = true;
    }

    public TlsConfig(boolean enabled, String keystorePath, String keystorePassword, boolean autoGenerateCert) {
        this.enabled = enabled;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.autoGenerateCert = autoGenerateCert;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }
    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }
    public boolean isAutoGenerateCert() { return autoGenerateCert; }
    public void setAutoGenerateCert(boolean autoGenerateCert) { this.autoGenerateCert = autoGenerateCert; }
}
