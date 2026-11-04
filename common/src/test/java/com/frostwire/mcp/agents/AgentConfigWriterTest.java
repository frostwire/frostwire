/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Writing FrostWire's MCP server entry into AI agents' config files and removing it again, without
 * losing the user's other servers or settings.
 */
class AgentConfigWriterTest {

  private static final String URL = "https://127.0.0.1:48487/mcp";

  @TempDir Path tempDir;

  @Test
  void configureAddsTheEntryNextToTheUsersOtherServers() throws IOException {
    Path config = tempDir.resolve("cursor/mcp.json");
    Files.createDirectories(config.getParent());
    Files.writeString(
        config, "{\"theme\":\"dark\",\"mcpServers\":{\"github\":{\"url\":\"https://gh\"}}}");

    assertTrue(AgentConfigWriter.configure(agent("cursor", config, "mcpServers"), URL));

    JsonObject root = readJson(config);
    assertEquals("dark", root.get("theme").getAsString());
    JsonObject servers = root.getAsJsonObject("mcpServers");
    assertEquals("https://gh", servers.getAsJsonObject("github").get("url").getAsString());
    assertEquals(URL, servers.getAsJsonObject("frostwire").get("url").getAsString());
    assertTrue(AgentDetector.isFrostwireConfigured(config.toString(), "mcpServers"));
    assertNoTempFilesLeft(config.getParent());
  }

  @Test
  void configureCreatesMissingDirectoriesAndFiles() throws IOException {
    Path config = tempDir.resolve("a/b/opencode.json");

    assertTrue(AgentConfigWriter.configure(agent("opencode", config, "mcp"), URL));

    JsonObject entry = readJson(config).getAsJsonObject("mcp").getAsJsonObject("frostwire");
    assertEquals("remote", entry.get("type").getAsString());
    assertEquals(URL, entry.get("url").getAsString());
    assertTrue(entry.get("enabled").getAsBoolean());
  }

  @Test
  void configureRefusesAgentsThatAreNotInstalled() {
    Path config = tempDir.resolve("mcp.json");
    AgentInfo missing =
        new AgentInfo("cursor", "Cursor", config.toString(), "mcpServers", false, false);
    assertFalse(AgentConfigWriter.configure(missing, URL));
    assertFalse(AgentConfigWriter.configure(null, URL));
    assertFalse(Files.exists(config), "nothing may be written for an agent that isn't there");
  }

  @Test
  void configureBacksUpACorruptConfigBeforeReplacingIt() throws IOException {
    Path config = tempDir.resolve("mcp.json");
    Files.writeString(config, "{not json");

    assertTrue(AgentConfigWriter.configure(agent("qwen", config, "mcpServers"), URL));

    assertEquals(
        URL,
        readJson(config)
            .getAsJsonObject("mcpServers")
            .getAsJsonObject("frostwire")
            .get("url")
            .getAsString());
    try (Stream<Path> files = Files.list(tempDir)) {
      List<Path> backups =
          files.filter(p -> p.getFileName().toString().startsWith("mcp.json.bak.")).toList();
      assertEquals(1, backups.size(), "the corrupt file is kept as a backup");
      assertEquals("{not json", Files.readString(backups.get(0)));
    }
  }

  @Test
  void configureFailsWhenTheServersKeyIsNotAnObject() throws IOException {
    Path config = tempDir.resolve("mcp.json");
    Files.writeString(config, "{\"mcpServers\":[1,2]}");

    assertFalse(AgentConfigWriter.configure(agent("cursor", config, "mcpServers"), URL));
    assertEquals("{\"mcpServers\":[1,2]}", Files.readString(config), "the file is left alone");
  }

  @Test
  void eachAgentGetsTheEntryShapeItsClientUnderstands() {
    assertEquals(URL, entry("cursor").get("url").getAsString());
    assertEquals(URL, entry("claude-code").get("url").getAsString());
    assertEquals(URL, entry("codex").get("url").getAsString());
    assertEquals(URL, entry("qwen").get("url").getAsString());
    assertEquals(URL, entry("grok").get("url").getAsString());
    assertEquals(URL, entry("chatgpt-desktop").get("url").getAsString());
    assertEquals(URL, entry("some-future-agent").get("url").getAsString());

    JsonObject vscode = entry("vscode");
    assertEquals(URL, vscode.get("url").getAsString());
    assertEquals("http", vscode.get("type").getAsString());

    JsonObject windsurf = entry("windsurf");
    assertEquals(URL, windsurf.get("serverUrl").getAsString());
    assertFalse(windsurf.has("url"), "Windsurf reads serverUrl");

    JsonObject copilot = entry("copilot");
    assertEquals("http", copilot.get("type").getAsString());
    assertEquals(URL, copilot.get("url").getAsString());
    assertEquals(0, copilot.getAsJsonObject("headers").size());
    assertEquals("*", copilot.getAsJsonArray("tools").get(0).getAsString());

    // Claude Desktop only speaks stdio, so it reaches the HTTP server through mcp-remote, which
    // must accept FrostWire's self-signed certificate.
    JsonObject claude = entry("claude-desktop");
    JsonArray args = claude.getAsJsonArray("args");
    assertEquals(URL, args.get(args.size() - 1).getAsString());
    String command = claude.get("command").getAsString();
    if (command.equals("npx")) {
      assertEquals("-y", args.get(0).getAsString());
      assertEquals("mcp-remote", args.get(1).getAsString());
    } else {
      assertTrue(command.endsWith("mcp-remote"), command);
      assertEquals(1, args.size());
    }
    assertEquals(
        "0", claude.getAsJsonObject("env").get("NODE_TLS_REJECT_UNAUTHORIZED").getAsString());
  }

  @Test
  void deconfigureRemovesOnlyFrostWireAndDropsAnEmptyContainer() throws IOException {
    Path shared = tempDir.resolve("shared.json");
    Files.writeString(
        shared, "{\"mcpServers\":{\"frostwire\":{\"url\":\"x\"},\"github\":{\"url\":\"gh\"}}}");
    assertTrue(AgentConfigWriter.deconfigure(agent("cursor", shared, "mcpServers")));
    JsonObject servers = readJson(shared).getAsJsonObject("mcpServers");
    assertFalse(servers.has("frostwire"));
    assertTrue(servers.has("github"));

    Path alone = tempDir.resolve("alone.json");
    Files.writeString(alone, "{\"other\":1,\"servers\":{\"frostwire\":{\"url\":\"x\"}}}");
    assertTrue(AgentConfigWriter.deconfigure(agent("vscode", alone, "servers")));
    JsonObject root = readJson(alone);
    assertFalse(root.has("servers"), "an emptied container is removed");
    assertEquals(1, root.get("other").getAsInt());
    assertFalse(AgentDetector.isFrostwireConfigured(alone.toString(), "servers"));
  }

  @Test
  void deconfigureSucceedsWhenThereIsNothingToRemove() throws IOException {
    assertFalse(AgentConfigWriter.deconfigure(null));
    assertTrue(
        AgentConfigWriter.deconfigure(agent("cursor", tempDir.resolve("none.json"), "mcpServers")));
    assertFalse(Files.exists(tempDir.resolve("none.json")), "deconfigure creates no file");

    Path corrupt = tempDir.resolve("corrupt.json");
    Files.writeString(corrupt, "][");
    assertTrue(AgentConfigWriter.deconfigure(agent("cursor", corrupt, "mcpServers")));
    assertEquals("][", Files.readString(corrupt), "an unreadable config is not rewritten");
  }

  @Test
  void configureTomlAppendsASectionAfterTheUsersSettings() throws IOException {
    Path config = tempDir.resolve("config.toml");
    Files.writeString(config, "model = \"o3\"\n[mcp_servers.other]\nurl = \"http://other\"");

    assertTrue(AgentConfigWriter.configure(toml("codex", config), URL));

    assertEquals(
        List.of(
            "model = \"o3\"",
            "[mcp_servers.other]",
            "url = \"http://other\"",
            "",
            "[mcp_servers.frostwire]",
            "url = \"" + URL + "\"",
            "enabled = true"),
        Files.readAllLines(config));
    assertTrue(AgentDetector.isFrostwireConfiguredToml(config.toString(), "mcp_servers"));
  }

  @Test
  void configureTomlCreatesANewFile() throws IOException {
    Path config = tempDir.resolve("grok/config.toml");

    assertTrue(AgentConfigWriter.configure(toml("grok", config), URL));

    assertEquals(
        List.of("[mcp_servers.frostwire]", "url = \"" + URL + "\"", "enabled = true"),
        Files.readAllLines(config));
  }

  @Test
  void configureTomlUpdatesTheUrlOfAnExistingSectionInPlace() throws IOException {
    Path config = tempDir.resolve("config.toml");
    Files.writeString(
        config,
        String.join(
            "\n",
            "[mcp_servers.frostwire]",
            "url = \"http://old\"",
            "enabled = false",
            "[mcp_servers.next]",
            "url = \"http://next\""));

    assertTrue(AgentConfigWriter.configure(toml("codex", config), URL));

    assertEquals(
        List.of(
            "[mcp_servers.frostwire]",
            "url = \"" + URL + "\"",
            "enabled = false",
            "[mcp_servers.next]",
            "url = \"http://next\""),
        Files.readAllLines(config),
        "the user's enabled choice and the next section are kept");
  }

  @Test
  void configureTomlFillsInMissingUrlAndEnabledLines() throws IOException {
    Path config = tempDir.resolve("config.toml");
    Files.writeString(config, "[mcp_servers.frostwire]\ntimeout = 5\n[other]\nkey = 1");

    assertTrue(AgentConfigWriter.configure(toml("codex", config), URL));

    assertEquals(
        List.of(
            "[mcp_servers.frostwire]",
            "url = \"" + URL + "\"",
            "enabled = true",
            "timeout = 5",
            "[other]",
            "key = 1"),
        Files.readAllLines(config));
  }

  @Test
  void configureTomlAddsEnabledAfterAnExistingUrl() throws IOException {
    Path config = tempDir.resolve("config.toml");
    Files.writeString(config, "[mcp_servers.frostwire]\nurl = \"http://old\"");

    assertTrue(AgentConfigWriter.configure(toml("codex", config), URL));

    assertEquals(
        List.of("[mcp_servers.frostwire]", "url = \"" + URL + "\"", "enabled = true"),
        Files.readAllLines(config));
  }

  @Test
  void deconfigureTomlRemovesTheSectionAndTheBlankLineBeforeIt() throws IOException {
    Path config = tempDir.resolve("config.toml");
    Files.writeString(
        config,
        String.join(
            "\n",
            "model = \"o3\"",
            "",
            "[mcp_servers.frostwire]",
            "url = \"" + URL + "\"",
            "enabled = true",
            "[mcp_servers.other]",
            "url = \"http://other\""));

    assertTrue(AgentConfigWriter.deconfigure(toml("codex", config)));

    assertEquals(
        List.of("model = \"o3\"", "[mcp_servers.other]", "url = \"http://other\""),
        Files.readAllLines(config));
    assertFalse(AgentDetector.isFrostwireConfiguredToml(config.toString(), "mcp_servers"));
  }

  @Test
  void deconfigureTomlWithoutTheSectionLeavesTheFileAlone() throws IOException {
    assertTrue(AgentConfigWriter.deconfigure(toml("codex", tempDir.resolve("missing.toml"))));

    Path config = tempDir.resolve("config.toml");
    Files.writeString(config, "model = \"o3\"");
    assertTrue(AgentConfigWriter.deconfigure(toml("codex", config)));
    assertEquals("model = \"o3\"", Files.readString(config));
  }

  @Test
  void configureAllConfiguresEveryInstalledAgentThatIsNotYetConfigured() throws IOException {
    String home = System.getProperty("user.home");
    String os = System.getProperty("os.name");
    try {
      System.setProperty("user.home", tempDir.toString());
      System.setProperty("os.name", "Linux");
      // A Linux home with VS Code and Copilot's directories, and Copilot already configured.
      Files.createDirectories(tempDir.resolve(".config/Code"));
      Path copilotConfig = tempDir.resolve(".copilot/mcp-config.json");
      Files.createDirectories(copilotConfig.getParent());
      Files.writeString(copilotConfig, "{\"mcpServers\":{\"frostwire\":{\"url\":\"old\"}}}");

      long expected =
          AgentDetector.detectAll().stream()
              .filter(a -> a.isInstalled() && !a.isConfigured())
              .count();
      assertTrue(expected >= 1, "VS Code is installed and not configured");

      assertEquals(expected, AgentConfigWriter.configureAll(URL));

      for (AgentInfo agent : AgentDetector.detectAll()) {
        if (agent.isInstalled()) {
          assertTrue(agent.isConfigured(), agent.getId() + " is configured afterwards");
        }
      }
      assertEquals(
          "old",
          readJson(copilotConfig)
              .getAsJsonObject("mcpServers")
              .getAsJsonObject("frostwire")
              .get("url")
              .getAsString(),
          "an agent that is already configured is not rewritten");
    } finally {
      System.setProperty("user.home", home);
      System.setProperty("os.name", os);
    }
  }

  @Test
  void readConfigFileReturnsNullForMissingFiles() {
    assertNull(AgentConfigWriter.readConfigFile(new File(tempDir.toFile(), "none.json")));
  }

  private static AgentInfo agent(String id, Path config, String key) {
    return new AgentInfo(id, id, config.toString(), key, true, false);
  }

  private static AgentInfo toml(String id, Path config) {
    return new AgentInfo(
        id, id, config.toString(), "mcp_servers", AgentInfo.ConfigFormat.TOML, true, false);
  }

  private static JsonObject entry(String id) {
    return AgentConfigWriter.buildConfigEntry(
        new AgentInfo(id, id, "", "mcpServers", true, false), URL);
  }

  private static JsonObject readJson(Path file) throws IOException {
    return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static void assertNoTempFilesLeft(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      assertTrue(
          files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
          "the atomic write leaves no temp file behind");
    }
  }
}
