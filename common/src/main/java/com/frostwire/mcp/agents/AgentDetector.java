package com.frostwire.mcp.agents;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AgentDetector {

    public static List<AgentInfo> detectAll() {
        List<AgentInfo> agents = new ArrayList<>();
        agents.add(detectClaudeDesktop());
        agents.add(detectCursor());
        agents.add(detectVSCode());
        agents.add(detectWindsurf());
        agents.add(detectClaudeCode());
        agents.add(detectCopilot());
        agents.add(detectCodex());
        agents.add(detectOpenCode());
        agents.add(detectQwen());
        agents.add(detectChatGPTDesktop());
        return agents;
    }

    static AgentInfo detectClaudeDesktop() {
        String configPath;
        boolean appExists = false;

        if (isMac()) {
            configPath = getHomeDir() + "/Library/Application Support/Claude/claude_desktop_config.json";
            appExists = new File("/Applications/Claude.app/Contents/Info.plist").exists();
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\Claude\\claude_desktop_config.json"
                    : getHomeDir() + "\\AppData\\Roaming\\Claude\\claude_desktop_config.json";
            String localAppData = System.getenv("LOCALAPPDATA");
            String exePath = localAppData != null
                    ? localAppData + "\\Claude\\Claude.exe"
                    : getHomeDir() + "\\AppData\\Local\\Claude\\Claude.exe";
            appExists = new File(exePath).exists();
        } else {
            return new AgentInfo("claude-desktop", "Claude Desktop", "", "mcpServers", false, false);
        }

        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("claude-desktop", "Claude Desktop", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectCursor() {
        String configPath = getHomeDir() + "/.cursor/mcp.json";
        if (isWindows()) {
            configPath = getHomeDir() + "\\.cursor\\mcp.json";
        }
        boolean appExists = isCommandInPath("cursor");
        if (!appExists && isMac()) {
            appExists = new File("/Applications/Cursor.app/Contents/Info.plist").exists();
        }
        if (!appExists && isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                appExists = new File(localAppData + "\\Programs\\Cursor\\Cursor.exe").exists()
                        || new File(localAppData + "\\Cursor\\Cursor.exe").exists();
            }
        }
        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("cursor", "Cursor", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectVSCode() {
        String configPath;
        if (isMac()) {
            configPath = getHomeDir() + "/Library/Application Support/Code/User/mcp.json";
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\Code\\User\\mcp.json"
                    : getHomeDir() + "\\AppData\\Roaming\\Code\\User\\mcp.json";
        } else {
            configPath = getHomeDir() + "/.config/Code/User/mcp.json";
        }
        boolean appExists = isCommandInPath("code");
        if (!appExists && isMac()) {
            appExists = new File("/Applications/Visual Studio Code.app/Contents/Info.plist").exists();
        }
        if (!appExists && isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                appExists = new File(localAppData + "\\Programs\\Microsoft VS Code\\Code.exe").exists();
            }
        }
        if (!appExists && !isMac() && !isWindows()) {
            appExists = new File(getHomeDir(), ".config/Code").isDirectory();
        }
        boolean configured = isFrostwireConfigured(configPath, "servers");
        return new AgentInfo("vscode", "VS Code", configPath, "servers", appExists, configured);
    }

    static AgentInfo detectWindsurf() {
        String configPath;
        if (isWindows()) {
            configPath = getHomeDir() + "\\.codeium\\windsurf\\mcp_config.json";
        } else {
            configPath = getHomeDir() + "/.codeium/windsurf/mcp_config.json";
        }
        boolean appExists = isCommandInPath("windsurf");
        if (!appExists && isMac()) {
            appExists = new File("/Applications/Windsurf.app/Contents/Info.plist").exists();
        }
        if (!appExists && isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                appExists = new File(localAppData + "\\Programs\\Windsurf\\Windsurf.exe").exists();
            }
        }
        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("windsurf", "Windsurf", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectClaudeCode() {
        String configPath;
        if (isWindows()) {
            configPath = getHomeDir() + "\\.claude.json";
        } else {
            configPath = getHomeDir() + "/.claude.json";
        }
        boolean appExists = isCommandInPath("claude");
        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("claude-code", "Claude Code", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectCopilot() {
        String configPath;
        if (isMac()) {
            configPath = getHomeDir() + "/.copilot/mcp-config.json";
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\copilot\\mcp-config.json"
                    : getHomeDir() + "\\AppData\\Roaming\\copilot\\mcp-config.json";
        } else {
            configPath = getHomeDir() + "/.copilot/mcp-config.json";
        }
        boolean appExists = isCommandInPath("copilot")
                || isCommandInPath("gh")
                || new File(getHomeDir(), ".copilot").isDirectory();
        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("copilot", "GitHub Copilot", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectCodex() {
        String configPath;
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\codex\\config.toml"
                    : getHomeDir() + "\\AppData\\Roaming\\codex\\config.toml";
        } else {
            configPath = getHomeDir() + "/.codex/config.toml";
        }
        boolean appExists = isCommandInPath("codex");
        boolean configured = isFrostwireConfiguredToml(configPath, "mcp_servers");
        return new AgentInfo("codex", "OpenAI Codex", configPath, "mcp_servers", AgentInfo.ConfigFormat.TOML, appExists, configured);
    }

    static AgentInfo detectOpenCode() {
        String configPath;
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\opencode\\opencode.json"
                    : getHomeDir() + "\\AppData\\Roaming\\opencode\\opencode.json";
        } else {
            configPath = getHomeDir() + "/.config/opencode/opencode.json";
        }
        boolean appExists = isCommandInPath("opencode");
        boolean configured = isFrostwireConfigured(configPath, "mcp");
        return new AgentInfo("opencode", "OpenCode", configPath, "mcp", appExists, configured);
    }

    static AgentInfo detectQwen() {
        String configPath;
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\qwen\\mcp.json"
                    : getHomeDir() + "\\AppData\\Roaming\\qwen\\mcp.json";
        } else {
            configPath = getHomeDir() + "/.qwen/mcp.json";
        }
        boolean appExists = isCommandInPath("qwen");
        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("qwen", "Qwen Code", configPath, "mcpServers", appExists, configured);
    }

    static AgentInfo detectChatGPTDesktop() {
        String configPath;
        boolean appExists = false;

        if (isMac()) {
            configPath = getHomeDir() + "/Library/Application Support/ChatGPT/mcp_servers.json";
            appExists = new File("/Applications/ChatGPT.app").exists();
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            configPath = appData != null
                    ? appData + "\\ChatGPT\\mcp_servers.json"
                    : getHomeDir() + "\\AppData\\Roaming\\ChatGPT\\mcp_servers.json";
            String localAppData = System.getenv("LOCALAPPDATA");
            String exePath = localAppData != null
                    ? localAppData + "\\ChatGPT\\ChatGPT.exe"
                    : getHomeDir() + "\\AppData\\Local\\ChatGPT\\ChatGPT.exe";
            appExists = new File(exePath).exists();
        } else {
            return new AgentInfo("chatgpt-desktop", "ChatGPT Desktop", "", "mcpServers", false, false);
        }

        boolean configured = isFrostwireConfigured(configPath, "mcpServers");
        return new AgentInfo("chatgpt-desktop", "ChatGPT Desktop", configPath, "mcpServers", appExists, configured);
    }

    static boolean isFrostwireConfigured(String configFilePath, String configKey) {
        File file = new File(configFilePath);
        if (!file.exists() || !file.canRead()) {
            return false;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject container = root.getAsJsonObject(configKey);
            if (container != null && container.has("frostwire")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean isFrostwireConfiguredToml(String configFilePath, String sectionPrefix) {
        File file = new File(configFilePath);
        if (!file.exists() || !file.canRead()) {
            return false;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            java.io.BufferedReader br = new java.io.BufferedReader(reader);
            String line;
            String targetSection = "[" + sectionPrefix + ".frostwire]";
            while ((line = br.readLine()) != null) {
                if (line.trim().equals(targetSection)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static String getHomeDir() {
        return System.getProperty("user.home");
    }

    static String getAppDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return appdata != null ? appdata : getHomeDir() + "\\AppData\\Roaming";
        }
        if (os.contains("mac")) {
            return getHomeDir() + "/Library/Application Support";
        }
        return getHomeDir() + "/.config";
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    static boolean isCommandInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        String pathSeparator = isWindows() ? ";" : ":";
        String[] paths = pathEnv.split(pathSeparator);
        for (String dir : paths) {
            File exe = new File(dir, command + (isWindows() ? ".exe" : ""));
            if (exe.exists() && exe.canExecute()) {
                return true;
            }
        }
        return false;
    }
}