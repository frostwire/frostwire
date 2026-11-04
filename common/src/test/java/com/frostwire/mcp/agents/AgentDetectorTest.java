/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where FrostWire looks for each AI agent's MCP config on every OS, and how it tells whether its
 * own server entry is already there. The home directory and OS name are pointed at a temp home for
 * each test and restored afterwards.
 */
class AgentDetectorTest {

  @TempDir Path home;

  private String originalHome;
  private String originalOs;

  @BeforeEach
  void pointHomeAtTempDir() {
    originalHome = System.getProperty("user.home");
    originalOs = System.getProperty("os.name");
    System.setProperty("user.home", home.toString());
  }

  @AfterEach
  void restoreHomeAndOs() {
    System.setProperty("user.home", originalHome);
    System.setProperty("os.name", originalOs);
  }

  @Test
  void detectAllReportsEveryAgentInAStableOrder() {
    System.setProperty("os.name", "Linux");
    List<String> ids = AgentDetector.detectAll().stream().map(AgentInfo::getId).toList();
    assertEquals(
        List.of(
            "claude-desktop",
            "cursor",
            "vscode",
            "windsurf",
            "claude-code",
            "copilot",
            "codex",
            "opencode",
            "qwen",
            "grok",
            "chatgpt-desktop"),
        ids);
  }

  @Test
  void linuxConfigPathsLiveUnderTheHomeDirectory() {
    System.setProperty("os.name", "Linux");
    Map<String, AgentInfo> agents = byId();

    // The desktop chat apps have no Linux build.
    for (String id : List.of("claude-desktop", "chatgpt-desktop")) {
      AgentInfo agent = agents.get(id);
      assertEquals("", agent.getConfigFilePath(), id);
      assertFalse(agent.isInstalled(), id);
      assertFalse(agent.isConfigured(), id);
    }

    assertPath(agents, "cursor", "/.cursor/mcp.json", "mcpServers");
    assertPath(agents, "vscode", "/.config/Code/User/mcp.json", "servers");
    assertPath(agents, "windsurf", "/.codeium/windsurf/mcp_config.json", "mcpServers");
    assertPath(agents, "claude-code", "/.claude.json", "mcpServers");
    assertPath(agents, "copilot", "/.copilot/mcp-config.json", "mcpServers");
    assertPath(agents, "codex", "/.codex/config.toml", "mcp_servers");
    assertPath(agents, "opencode", "/.config/opencode/opencode.json", "mcp");
    assertPath(agents, "qwen", "/.qwen/mcp.json", "mcpServers");
    assertPath(agents, "grok", "/.grok/config.toml", "mcp_servers");

    assertEquals(AgentInfo.ConfigFormat.TOML, agents.get("codex").getConfigFormat());
    assertEquals(AgentInfo.ConfigFormat.TOML, agents.get("grok").getConfigFormat());
    assertEquals(AgentInfo.ConfigFormat.JSON, agents.get("cursor").getConfigFormat());
    assertEquals("Grok Build", agents.get("grok").getName());
    assertEquals("OpenAI Codex", agents.get("codex").getName());
  }

  @Test
  void linuxVsCodeAndCopilotAreInstalledWhenTheirDirectoriesExist() throws IOException {
    System.setProperty("os.name", "Linux");
    Files.createDirectories(home.resolve(".config/Code"));
    Files.createDirectories(home.resolve(".copilot"));

    Map<String, AgentInfo> agents = byId();

    assertTrue(agents.get("vscode").isInstalled());
    assertTrue(agents.get("copilot").isInstalled());
  }

  @Test
  void agentsAreConfiguredOnceTheirConfigHasAFrostWireEntry() throws IOException {
    System.setProperty("os.name", "Linux");
    write(".cursor/mcp.json", "{\"mcpServers\":{\"frostwire\":{\"url\":\"u\"}}}");
    write(".config/Code/User/mcp.json", "{\"servers\":{\"frostwire\":{\"url\":\"u\"}}}");
    write(".codeium/windsurf/mcp_config.json", "{\"mcpServers\":{\"other\":{}}}");
    write(".codex/config.toml", "  [mcp_servers.frostwire]  \nurl = \"u\"");
    write(".grok/config.toml", "[mcp_servers.other]");
    write(".config/opencode/opencode.json", "{\"mcp\":{\"frostwire\":{}}}");
    write(".qwen/mcp.json", "not json");

    Map<String, AgentInfo> agents = byId();

    assertTrue(agents.get("cursor").isConfigured());
    assertTrue(agents.get("vscode").isConfigured());
    assertTrue(agents.get("codex").isConfigured());
    assertTrue(agents.get("opencode").isConfigured());
    assertFalse(agents.get("windsurf").isConfigured(), "another server is not FrostWire");
    assertFalse(agents.get("grok").isConfigured(), "another TOML section is not FrostWire");
    assertFalse(agents.get("qwen").isConfigured(), "an unreadable config is not configured");
    assertFalse(agents.get("claude-code").isConfigured(), "no config file at all");
  }

  @Test
  void macConfigPathsUseApplicationSupport() throws IOException {
    System.setProperty("os.name", "Mac OS X");
    write(
        "Library/Application Support/Claude/claude_desktop_config.json",
        "{\"mcpServers\":{\"frostwire\":{}}}");

    Map<String, AgentInfo> agents = byId();

    assertPath(
        agents,
        "claude-desktop",
        "/Library/Application Support/Claude/claude_desktop_config.json",
        "mcpServers");
    assertTrue(agents.get("claude-desktop").isConfigured());
    assertPath(agents, "vscode", "/Library/Application Support/Code/User/mcp.json", "servers");
    assertPath(agents, "copilot", "/.copilot/mcp-config.json", "mcpServers");
    assertPath(
        agents,
        "chatgpt-desktop",
        "/Library/Application Support/ChatGPT/mcp_servers.json",
        "mcpServers");
    assertPath(agents, "cursor", "/.cursor/mcp.json", "mcpServers");
    assertPath(agents, "grok", "/.grok/config.toml", "mcp_servers");
    assertEquals(
        home + "/Library/Application Support", AgentDetector.getAppDataDir(), "mac app data");
  }

  @Test
  void windowsConfigPathsUseBackslashesAndAppData() {
    System.setProperty("os.name", "Windows 11");
    Map<String, AgentInfo> agents = byId();

    // %APPDATA% when it is set, else the default under the home directory: same file name.
    assertTrue(
        agents
            .get("claude-desktop")
            .getConfigFilePath()
            .endsWith("\\Claude\\claude_desktop_config.json"));
    assertTrue(agents.get("vscode").getConfigFilePath().endsWith("\\Code\\User\\mcp.json"));
    assertTrue(agents.get("copilot").getConfigFilePath().endsWith("\\copilot\\mcp-config.json"));
    assertTrue(agents.get("codex").getConfigFilePath().endsWith("\\codex\\config.toml"));
    assertTrue(agents.get("opencode").getConfigFilePath().endsWith("\\opencode\\opencode.json"));
    assertTrue(agents.get("qwen").getConfigFilePath().endsWith("\\qwen\\mcp.json"));
    assertTrue(
        agents.get("chatgpt-desktop").getConfigFilePath().endsWith("\\ChatGPT\\mcp_servers.json"));
    assertEquals(home + "\\.cursor\\mcp.json", agents.get("cursor").getConfigFilePath());
    assertEquals(
        home + "\\.codeium\\windsurf\\mcp_config.json", agents.get("windsurf").getConfigFilePath());
    assertEquals(home + "\\.claude.json", agents.get("claude-code").getConfigFilePath());
    assertEquals(home + "\\.grok\\config.toml", agents.get("grok").getConfigFilePath());
    String appData = System.getenv("APPDATA");
    assertEquals(
        appData != null ? appData : home + "\\AppData\\Roaming", AgentDetector.getAppDataDir());
    assertTrue(AgentDetector.isWindows());
    assertFalse(AgentDetector.isMac());
  }

  @Test
  void linuxAppDataIsDotConfig() {
    System.setProperty("os.name", "Linux");
    assertEquals(home + "/.config", AgentDetector.getAppDataDir());
    assertEquals(home.toString(), AgentDetector.getHomeDir());
    assertFalse(AgentDetector.isWindows());
    assertFalse(AgentDetector.isMac());
  }

  @Test
  void findCommandPathOnlyFindsExecutablesOnThePath() {
    assertNull(AgentDetector.findCommandPath("frostwire-no-such-command-" + System.nanoTime()));
    assertFalse(AgentDetector.isCommandInPath("frostwire-no-such-command"));

    String path = System.getenv("PATH");
    if (path == null) {
      return;
    }
    // The first executable file on the PATH must be found under its own name.
    for (String dir : path.split(File.pathSeparator)) {
      File[] files = new File(dir).listFiles();
      if (files == null) {
        continue;
      }
      for (File file : files) {
        String name = file.getName();
        boolean windowsExe = AgentDetector.isWindows() && name.endsWith(".exe");
        if (file.isFile() && file.canExecute() && (windowsExe || !AgentDetector.isWindows())) {
          String command = windowsExe ? name.substring(0, name.length() - 4) : name;
          String found = AgentDetector.findCommandPath(command);
          assertNotNull(found, command);
          assertTrue(new File(found).canExecute(), found);
          assertTrue(AgentDetector.isCommandInPath(command));
          return;
        }
      }
    }
  }

  @Test
  void configuredChecksToleratePathsThatAreNotFiles() {
    assertFalse(AgentDetector.isFrostwireConfigured(home.resolve("none").toString(), "k"));
    assertFalse(AgentDetector.isFrostwireConfiguredToml(home.resolve("none").toString(), "k"));
    // A directory exists and is readable but is not a config file.
    assertFalse(AgentDetector.isFrostwireConfigured(home.toString(), "k"));
    assertFalse(AgentDetector.isFrostwireConfiguredToml(home.toString(), "k"));
  }

  @Test
  void agentInfoKeepsWhatItWasBuiltWith() {
    AgentInfo json = new AgentInfo("id", "Name", "/p", "key", true, false);
    assertEquals("id", json.getId());
    assertEquals("Name", json.getName());
    assertEquals("/p", json.getConfigFilePath());
    assertEquals("key", json.getConfigKey());
    assertEquals(AgentInfo.ConfigFormat.JSON, json.getConfigFormat(), "JSON is the default");
    assertTrue(json.isInstalled());
    assertFalse(json.isConfigured());
  }

  private static Map<String, AgentInfo> byId() {
    Map<String, AgentInfo> agents = new LinkedHashMap<>();
    for (AgentInfo agent : AgentDetector.detectAll()) {
      agents.put(agent.getId(), agent);
    }
    return agents;
  }

  private void assertPath(Map<String, AgentInfo> agents, String id, String suffix, String key) {
    AgentInfo agent = agents.get(id);
    assertEquals(home + suffix, agent.getConfigFilePath(), id);
    assertEquals(key, agent.getConfigKey(), id);
  }

  private void write(String relative, String content) throws IOException {
    Path file = home.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
