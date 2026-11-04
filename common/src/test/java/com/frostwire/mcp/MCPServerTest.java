/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The JSON-RPC dispatch of {@link MCPServer}: the initialize handshake, the methods an MCP client
 * may only call after it, tool calls wrapped in text content, resource reads and the error codes a
 * client sees for malformed requests.
 */
class MCPServerTest {

  @Test
  void initializeAdvertisesProtocolCapabilitiesAndServerVersion() {
    MCPServer server = new MCPServer("7.0.1");
    assertFalse(server.isInitialized());

    JsonObject response = server.handleRequest(request(1, "initialize", null));

    assertTrue(server.isInitialized());
    assertEquals("2.0", response.get("jsonrpc").getAsString());
    assertEquals(1, response.get("id").getAsInt());
    JsonObject result = response.getAsJsonObject("result");
    assertEquals(MCPConstants.PROTOCOL_VERSION, result.get("protocolVersion").getAsString());
    JsonObject serverInfo = result.getAsJsonObject("serverInfo");
    assertEquals("frostwire", serverInfo.get("name").getAsString());
    assertEquals("7.0.1", serverInfo.get("version").getAsString());
    JsonObject capabilities = result.getAsJsonObject("capabilities");
    assertFalse(capabilities.getAsJsonObject("tools").get("listChanged").getAsBoolean());
    assertTrue(capabilities.getAsJsonObject("resources").get("subscribe").getAsBoolean());
    assertTrue(capabilities.getAsJsonObject("resources").get("listChanged").getAsBoolean());
  }

  @Test
  void methodsOtherThanInitializeAndPingRequireTheHandshake() {
    MCPServer server = new MCPServer("1");
    for (String method : List.of("tools/list", "tools/call", "resources/list", "resources/read")) {
      JsonObject response = server.handleRequest(request("a", method, new JsonObject()));
      assertError(response, MCPConstants.ERROR_INVALID_REQUEST, "Server not initialized");
      assertEquals("a", response.get("id").getAsString(), method);
    }

    JsonObject pong = server.handleRequest(request(9, "ping", null));
    assertEquals(9, pong.get("id").getAsInt());
    assertEquals(0, pong.getAsJsonObject("result").size());
    assertFalse(server.isInitialized(), "ping must not complete the handshake");
  }

  @Test
  void initializedNotificationHasNoResponse() {
    MCPServer server = initializedServer();
    JsonObject notification = MCPJsonRpc.notification("notifications/initialized", null);
    assertNull(server.handleRequest(notification));
  }

  @Test
  void malformedRequestsGetJsonRpcErrors() {
    MCPServer server = new MCPServer("1");

    assertError(server.handleRequest(null), MCPConstants.ERROR_PARSE, "Invalid JSON");

    JsonObject noMethod = new JsonObject();
    noMethod.addProperty("id", 3);
    JsonObject missing = server.handleRequest(noMethod);
    assertError(missing, MCPConstants.ERROR_INVALID_REQUEST, "Missing method field");
    assertEquals(3, missing.get("id").getAsInt());

    JsonObject objectMethod = new JsonObject();
    objectMethod.addProperty("id", 4);
    objectMethod.add("method", new JsonObject());
    assertError(
        server.handleRequest(objectMethod),
        MCPConstants.ERROR_INVALID_REQUEST,
        "Invalid method field");

    assertError(
        server.handleRequest(request(5, "sampling/createMessage", null)),
        MCPConstants.ERROR_METHOD_NOT_FOUND,
        "Method not found: sampling/createMessage");
  }

  @Test
  void toolsListDescribesEveryRegisteredToolWithAnEmptySchemaWhenNoneIsGiven() {
    MCPServer server = initializedServer();
    server.registerTool(new FakeTool("frostwire_echo", schema("text")));
    server.registerTool(new FakeTool("frostwire_noschema", null));

    JsonArray tools =
        server
            .handleRequest(request(2, "tools/list", null))
            .getAsJsonObject("result")
            .getAsJsonArray("tools");

    assertEquals(2, tools.size());
    for (int i = 0; i < tools.size(); i++) {
      JsonObject tool = tools.get(i).getAsJsonObject();
      String name = tool.get("name").getAsString();
      assertEquals("description of " + name, tool.get("description").getAsString());
      JsonObject inputSchema = tool.getAsJsonObject("inputSchema");
      if (name.equals("frostwire_echo")) {
        assertTrue(inputSchema.getAsJsonObject("properties").has("text"));
      } else {
        assertEquals("frostwire_noschema", name);
        assertEquals(0, inputSchema.size());
      }
    }
  }

  @Test
  void toolsCallWrapsTheToolResultAsJsonText() {
    MCPServer server = initializedServer();
    FakeTool echo = new FakeTool("frostwire_echo", schema("text"));
    server.registerTool(echo);

    JsonObject arguments = new JsonObject();
    arguments.addProperty("text", "hello");
    JsonObject response = server.handleRequest(toolCall(7, "frostwire_echo", arguments));

    assertEquals(7, response.get("id").getAsInt());
    JsonObject result = response.getAsJsonObject("result");
    assertFalse(result.has("isError"));
    JsonArray content = result.getAsJsonArray("content");
    assertEquals(1, content.size());
    JsonObject item = content.get(0).getAsJsonObject();
    assertEquals("text", item.get("type").getAsString());
    JsonObject echoed = JsonParser.parseString(item.get("text").getAsString()).getAsJsonObject();
    assertEquals("hello", echoed.get("echo").getAsString());
    assertSame(arguments, echo.lastArguments);
  }

  @Test
  void toolsCallWithoutArgumentsPassesAnEmptyObjectAndANullResultHasNoContent() {
    MCPServer server = initializedServer();
    FakeTool silent = new FakeTool("frostwire_silent", null);
    silent.result = null;
    server.registerTool(silent);

    JsonObject response = server.handleRequest(toolCall(8, "frostwire_silent", null));

    assertNotNull(silent.lastArguments);
    assertEquals(0, silent.lastArguments.size());
    assertEquals(0, response.getAsJsonObject("result").getAsJsonArray("content").size());
  }

  @Test
  void toolsCallReportsUnknownToolsAndToolFailuresAsToolErrors() {
    MCPServer server = initializedServer();
    FakeTool broken = new FakeTool("frostwire_broken", null);
    broken.failure = new IllegalStateException("engine not started");
    server.registerTool(broken);

    JsonObject unknown = server.handleRequest(toolCall(1, "frostwire_nope", null));
    assertToolError(unknown, "Tool not found: frostwire_nope");

    JsonObject failed = server.handleRequest(toolCall(2, "frostwire_broken", null));
    assertToolError(failed, "Tool execution failed: engine not started");
  }

  @Test
  void toolsCallWithoutAToolNameIsAnInvalidParamsError() {
    MCPServer server = initializedServer();
    assertError(
        server.handleRequest(request(1, "tools/call", null)),
        MCPConstants.ERROR_INVALID_PARAMS,
        "Missing tool name in params");
    assertError(
        server.handleRequest(request(2, "tools/call", new JsonObject())),
        MCPConstants.ERROR_INVALID_PARAMS,
        "Missing tool name in params");
  }

  @Test
  void resourcesListAndReadReturnTheRegisteredResource() {
    MCPServer server = initializedServer();
    FakeResource status = new FakeResource("frostwire://status", "{\"ok\":true}");
    server.registerResource(status);

    JsonArray resources =
        server
            .handleRequest(request(1, "resources/list", null))
            .getAsJsonObject("result")
            .getAsJsonArray("resources");
    assertEquals(1, resources.size());
    JsonObject listed = resources.get(0).getAsJsonObject();
    assertEquals("frostwire://status", listed.get("uri").getAsString());
    assertEquals("Status", listed.get("name").getAsString());
    assertEquals("Engine status", listed.get("description").getAsString());
    assertEquals("application/json", listed.get("mimeType").getAsString());

    JsonObject read = server.handleRequest(resourceRead(2, "frostwire://status"));
    JsonArray contents = read.getAsJsonObject("result").getAsJsonArray("contents");
    assertEquals(1, contents.size());
    JsonObject content = contents.get(0).getAsJsonObject();
    assertEquals("frostwire://status", content.get("uri").getAsString());
    assertEquals("application/json", content.get("mimeType").getAsString());
    assertEquals("{\"ok\":true}", content.get("text").getAsString());

    status.content = null;
    JsonObject empty = server.handleRequest(resourceRead(3, "frostwire://status"));
    assertEquals(
        "",
        empty
            .getAsJsonObject("result")
            .getAsJsonArray("contents")
            .get(0)
            .getAsJsonObject()
            .get("text")
            .getAsString(),
        "a resource with no content reads as empty text");
  }

  @Test
  void resourcesReadErrorsNameTheProblem() {
    MCPServer server = initializedServer();
    FakeResource broken = new FakeResource("frostwire://broken", null);
    broken.failure = new IllegalStateException("disk gone");
    server.registerResource(broken);

    assertError(
        server.handleRequest(request(1, "resources/read", null)),
        MCPConstants.ERROR_INVALID_PARAMS,
        "Missing resource uri in params");
    assertError(
        server.handleRequest(resourceRead(2, "frostwire://missing")),
        MCPConstants.ERROR_INVALID_PARAMS,
        "Resource not found: frostwire://missing");
    assertError(
        server.handleRequest(resourceRead(3, "frostwire://broken")),
        MCPConstants.ERROR_INTERNAL,
        "Failed to read resource: disk gone");
  }

  @Test
  void startAndStopDriveTheTransportAndResetTheHandshake() {
    MCPServer server = new MCPServer("1");
    assertThrows(IllegalStateException.class, server::start);
    assertThrows(IllegalStateException.class, () -> server.sendNotification("x", null));
    server.stop(); // no transport: nothing to stop, no exception

    FakeTransport transport = new FakeTransport();
    server.setTransport(transport);
    server.start();
    assertTrue(server.isStarted());
    assertTrue(transport.isRunning());

    // The transport hands requests to the server it was started by.
    JsonObject response = transport.handler.handleRequest(request(1, "initialize", null));
    assertNotNull(response.getAsJsonObject("result"));
    assertTrue(server.isInitialized());

    JsonObject params = new JsonObject();
    params.addProperty("infoHash", "abc");
    server.sendNotification("notifications/transfer", params);
    assertEquals(1, transport.notifications.size());
    JsonObject sent = transport.notifications.get(0);
    assertEquals("notifications/transfer", sent.get("method").getAsString());
    assertEquals("abc", sent.getAsJsonObject("params").get("infoHash").getAsString());
    assertFalse(sent.has("id"), "notifications carry no id");

    server.stop();
    assertFalse(server.isStarted());
    assertFalse(server.isInitialized());
    assertFalse(transport.isRunning());
  }

  private static MCPServer initializedServer() {
    MCPServer server = new MCPServer("test");
    server.handleRequest(request(0, "initialize", null));
    return server;
  }

  private static JsonObject request(Object id, String method, JsonObject params) {
    return MCPJsonRpc.request(id, method, params);
  }

  private static JsonObject toolCall(int id, String name, JsonObject arguments) {
    JsonObject params = new JsonObject();
    params.addProperty("name", name);
    if (arguments != null) {
      params.add("arguments", arguments);
    }
    return request(id, "tools/call", params);
  }

  private static JsonObject resourceRead(int id, String uri) {
    JsonObject params = new JsonObject();
    params.addProperty("uri", uri);
    return request(id, "resources/read", params);
  }

  private static JsonObject schema(String property) {
    JsonObject properties = new JsonObject();
    JsonObject type = new JsonObject();
    type.addProperty("type", "string");
    properties.add(property, type);
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    return schema;
  }

  private static void assertError(JsonObject response, int code, String message) {
    assertFalse(response.has("result"), "an error response has no result: " + response);
    JsonObject error = response.getAsJsonObject("error");
    assertEquals(code, error.get("code").getAsInt(), response.toString());
    assertEquals(message, error.get("message").getAsString());
  }

  private static void assertToolError(JsonObject response, String message) {
    assertFalse(response.has("error"), "tool errors are results, not JSON-RPC errors");
    JsonObject result = response.getAsJsonObject("result");
    assertTrue(result.get("isError").getAsBoolean());
    JsonObject item = result.getAsJsonArray("content").get(0).getAsJsonObject();
    assertEquals("text", item.get("type").getAsString());
    assertEquals(message, item.get("text").getAsString());
  }

  /** Echoes its "text" argument, or fails or returns a fixed result when told to. */
  private static final class FakeTool implements MCPTool {
    private final String name;
    private final JsonObject schema;
    private JsonObject lastArguments;
    private RuntimeException failure;
    private JsonObject result = new JsonObject();

    FakeTool(String name, JsonObject schema) {
      this.name = name;
      this.schema = schema;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return "description of " + name;
    }

    @Override
    public JsonObject inputSchema() {
      return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
      lastArguments = arguments;
      if (failure != null) {
        throw failure;
      }
      if (result != null && arguments.has("text")) {
        result.addProperty("echo", arguments.get("text").getAsString());
      }
      return result;
    }
  }

  private static final class FakeResource implements MCPResource {
    private final String uri;
    private String content;
    private RuntimeException failure;

    FakeResource(String uri, String content) {
      this.uri = uri;
      this.content = content;
    }

    @Override
    public String uri() {
      return uri;
    }

    @Override
    public String name() {
      return "Status";
    }

    @Override
    public String description() {
      return "Engine status";
    }

    @Override
    public String mimeType() {
      return "application/json";
    }

    @Override
    public String read() {
      if (failure != null) {
        throw failure;
      }
      return content;
    }
  }

  private static final class FakeTransport implements MCPTransport {
    private MCPTransportHandler handler;
    private boolean running;
    private final List<JsonObject> notifications = new ArrayList<>();

    @Override
    public void start(MCPTransportHandler handler) {
      this.handler = handler;
      running = true;
    }

    @Override
    public void stop() {
      running = false;
    }

    @Override
    public void sendNotification(JsonObject notification) {
      notifications.add(notification);
    }

    @Override
    public boolean isRunning() {
      return running;
    }
  }
}
