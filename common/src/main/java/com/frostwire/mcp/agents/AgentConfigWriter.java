package com.frostwire.mcp.agents;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AgentConfigWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean configure(AgentInfo agent, String mcpUrl) {
        if (agent == null || !agent.isInstalled()) {
            return false;
        }
        File configFile = new File(agent.getConfigFilePath());
        try {
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            JsonObject root = readConfigFile(configFile);
            if (root == null) {
                root = new JsonObject();
            }

            String configKey = agent.getConfigKey();
            JsonObject container = root.getAsJsonObject(configKey);
            if (container == null) {
                container = new JsonObject();
                root.add(configKey, container);
            }

            JsonObject entry = buildConfigEntry(agent, mcpUrl);
            container.add("frostwire", entry);

            return writeConfigFileAtomic(configFile, root);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deconfigure(AgentInfo agent) {
        if (agent == null) {
            return false;
        }
        File configFile = new File(agent.getConfigFilePath());
        if (!configFile.exists()) {
            return true;
        }

        try {
            JsonObject root = readConfigFile(configFile);
            if (root == null) {
                return true;
            }

            String configKey = agent.getConfigKey();
            JsonObject container = root.getAsJsonObject(configKey);
            if (container != null && container.has("frostwire")) {
                container.remove("frostwire");
                if (container.size() == 0) {
                    root.remove(configKey);
                }
            }

            return writeConfigFileAtomic(configFile, root);
        } catch (Exception e) {
            return false;
        }
    }

    public static int configureAll(String mcpUrl) {
        List<AgentInfo> agents = AgentDetector.detectAll();
        int count = 0;
        for (AgentInfo agent : agents) {
            if (agent.isInstalled() && !agent.isConfigured()) {
                if (configure(agent, mcpUrl)) {
                    count++;
                }
            }
        }
        return count;
    }

    static JsonObject buildConfigEntry(AgentInfo agent, String mcpUrl) {
        JsonObject entry = new JsonObject();
        switch (agent.getId()) {
            case "claude-desktop":
                entry.addProperty("url", mcpUrl);
                break;
            case "cursor":
                entry.addProperty("url", mcpUrl);
                break;
            case "vscode":
                entry.addProperty("url", mcpUrl);
                entry.addProperty("type", "http");
                break;
            case "windsurf":
                entry.addProperty("serverUrl", mcpUrl);
                break;
            case "cline":
                entry.addProperty("url", mcpUrl);
                break;
            case "claude-code":
                entry.addProperty("url", mcpUrl);
                break;
            default:
                entry.addProperty("url", mcpUrl);
                break;
        }
        return entry;
    }

    static JsonObject readConfigFile(File file) {
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            backupCorruptedFile(file);
            return null;
        }
    }

    static boolean writeConfigFileAtomic(File file, JsonObject root) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("fwmcp", ".tmp", parentDir);

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
                writer.flush();
            }

            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception atomicEx) {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.copy(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (Exception copyEx) {
                    return false;
                } finally {
                    tempFile.delete();
                }
            }
            return false;
        }
    }

    static void backupCorruptedFile(File file) {
        if (!file.exists()) {
            return;
        }
        try {
            String baseName = file.getName();
            File backup = new File(file.getParentFile(), baseName + ".bak." + System.currentTimeMillis());
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}