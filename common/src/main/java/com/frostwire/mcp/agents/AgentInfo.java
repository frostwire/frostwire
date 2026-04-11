package com.frostwire.mcp.agents;

public class AgentInfo {
    public enum ConfigFormat { JSON, TOML }

    private final String id;
    private final String name;
    private final String configFilePath;
    private final String configKey;
    private final ConfigFormat configFormat;
    private final boolean installed;
    private final boolean configured;

    public AgentInfo(String id, String name, String configFilePath, String configKey, ConfigFormat configFormat, boolean installed, boolean configured) {
        this.id = id;
        this.name = name;
        this.configFilePath = configFilePath;
        this.configKey = configKey;
        this.configFormat = configFormat;
        this.installed = installed;
        this.configured = configured;
    }

    public AgentInfo(String id, String name, String configFilePath, String configKey, boolean installed, boolean configured) {
        this(id, name, configFilePath, configKey, ConfigFormat.JSON, installed, configured);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getConfigFilePath() { return configFilePath; }
    public String getConfigKey() { return configKey; }
    public ConfigFormat getConfigFormat() { return configFormat; }
    public boolean isInstalled() { return installed; }
    public boolean isConfigured() { return configured; }
}
