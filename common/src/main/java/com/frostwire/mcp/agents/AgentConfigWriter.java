package com.frostwire.mcp.agents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AgentConfigWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean configure(AgentInfo agent, String mcpUrl) {
        if (agent == null || !agent.isInstalled()) {
            return false;
        }
        if (agent.getConfigFormat() == AgentInfo.ConfigFormat.TOML) {
            return configureToml(agent, mcpUrl);
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
        if (agent.getConfigFormat() == AgentInfo.ConfigFormat.TOML) {
            return deconfigureToml(agent);
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

    static boolean configureToml(AgentInfo agent, String mcpUrl) {
        File configFile = new File(agent.getConfigFilePath());
        try {
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            List<String> lines = new ArrayList<>();
            if (configFile.exists() && configFile.canRead()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }

            String sectionHeader = "[" + agent.getConfigKey() + ".frostwire]";
            String urlLine = "url = \"" + mcpUrl + "\"";

            int sectionIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals(sectionHeader)) {
                    sectionIndex = i;
                    break;
                }
            }

            if (sectionIndex >= 0) {
                int urlIndex = -1;
                for (int i = sectionIndex + 1; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.startsWith("[")) {
                        break;
                    }
                    if (trimmed.startsWith("url")) {
                        urlIndex = i;
                        break;
                    }
                }
                if (urlIndex >= 0) {
                    lines.set(urlIndex, urlLine);
                } else {
                    lines.add(sectionIndex + 1, urlLine);
                }
            } else {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                    lines.add("");
                }
                lines.add(sectionHeader);
                lines.add(urlLine);
            }

            return writeTomlFileAtomic(configFile, lines);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean deconfigureToml(AgentInfo agent) {
        File configFile = new File(agent.getConfigFilePath());
        if (!configFile.exists()) {
            return true;
        }

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }

            String sectionHeader = "[" + agent.getConfigKey() + ".frostwire]";
            int sectionIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals(sectionHeader)) {
                    sectionIndex = i;
                    break;
                }
            }

            if (sectionIndex < 0) {
                return true;
            }

            int removeEnd = sectionIndex + 1;
            while (removeEnd < lines.size()) {
                String trimmed = lines.get(removeEnd).trim();
                if (trimmed.startsWith("[")) {
                    break;
                }
                removeEnd++;
            }

            lines.subList(sectionIndex, removeEnd).clear();

            if (sectionIndex > 0 && sectionIndex <= lines.size() && lines.get(sectionIndex - 1).trim().isEmpty()) {
                lines.remove(sectionIndex - 1);
            }

            return writeTomlFileAtomic(configFile, lines);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean writeTomlFileAtomic(File file, List<String> lines) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("fwmcp", ".tmp", parentDir);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                for (int i = 0; i < lines.size(); i++) {
                    writer.write(lines.get(i));
                    if (i < lines.size() - 1) {
                        writer.write(System.lineSeparator());
                    }
                }
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
            case "claude-code":
                entry.addProperty("url", mcpUrl);
                break;
            case "copilot":
                entry.addProperty("type", "http");
                entry.addProperty("url", mcpUrl);
                entry.add("headers", new JsonObject());
                JsonArray tools = new JsonArray();
                tools.add("*");
                entry.add("tools", tools);
                break;
            case "codex":
                entry.addProperty("url", mcpUrl);
                break;
            case "opencode":
                entry.addProperty("type", "remote");
                entry.addProperty("url", mcpUrl);
                entry.addProperty("enabled", true);
                break;
            case "qwen":
                entry.addProperty("url", mcpUrl);
                break;
            case "chatgpt-desktop":
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