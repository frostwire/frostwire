/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JSON-RPC 2.0 message framing and the tool and resource registry behind the MCP server. */
class MCPJsonRpcTest {

  @Test
  void requestCarriesVersionIdMethodAndOptionalParams() {
    JsonObject params = new JsonObject();
    params.addProperty("q", "ubuntu");
    JsonObject request = MCPJsonRpc.request(42, "tools/call", params);
    assertEquals("2.0", request.get("jsonrpc").getAsString());
    assertEquals(42, request.get("id").getAsInt());
    assertEquals("tools/call", request.get("method").getAsString());
    assertSame(params, request.getAsJsonObject("params"));

    assertFalse(MCPJsonRpc.request(1, "ping", null).has("params"));
  }

  @Test
  void idsKeepTheirJsonType() {
    assertTrue(MCPJsonRpc.response(7, null).get("id").getAsJsonPrimitive().isNumber());
    assertTrue(MCPJsonRpc.response("seven", null).get("id").getAsJsonPrimitive().isString());
    assertTrue(MCPJsonRpc.response(true, null).get("id").getAsJsonPrimitive().isBoolean());
    assertTrue(MCPJsonRpc.response(null, null).get("id").isJsonNull());
    // Anything else is sent as its string form.
    assertEquals("[1, 2]", MCPJsonRpc.response(List.of(1, 2), null).get("id").getAsString());
  }

  @Test
  void responseHasAResultEvenWhenItIsNull() {
    JsonObject result = new JsonObject();
    result.addProperty("ok", true);
    assertSame(result, MCPJsonRpc.response(1, result).getAsJsonObject("result"));
    assertEquals(JsonNull.INSTANCE, MCPJsonRpc.response(1, null).get("result"));
  }

  @Test
  void errorResponseCarriesCodeMessageAndOptionalData() {
    JsonObject plain = MCPJsonRpc.errorResponse("x", MCPConstants.ERROR_INTERNAL, "boom");
    assertEquals("x", plain.get("id").getAsString());
    JsonObject error = plain.getAsJsonObject("error");
    assertEquals(-32603, error.get("code").getAsInt());
    assertEquals("boom", error.get("message").getAsString());
    assertFalse(error.has("data"));
    assertFalse(plain.has("result"));

    JsonObject data = new JsonObject();
    data.addProperty("field", "name");
    JsonObject detailed =
        MCPJsonRpc.errorResponse(1, MCPConstants.ERROR_INVALID_PARAMS, "bad", data);
    assertSame(data, detailed.getAsJsonObject("error").getAsJsonObject("data"));
  }

  @Test
  void notificationHasNoId() {
    JsonObject notification = MCPJsonRpc.notification("notifications/progress", null);
    assertEquals("2.0", notification.get("jsonrpc").getAsString());
    assertEquals("notifications/progress", notification.get("method").getAsString());
    assertFalse(notification.has("id"));
    assertFalse(notification.has("params"));
    assertTrue(MCPJsonRpc.notification("m", new JsonObject()).has("params"));
  }

  @Test
  void messagesAreClassifiedByTheirFields() {
    JsonObject request = MCPJsonRpc.request(1, "ping", null);
    JsonObject notification = MCPJsonRpc.notification("ping", null);
    JsonObject response = MCPJsonRpc.response(1, new JsonObject());
    JsonObject error = MCPJsonRpc.errorResponse(1, MCPConstants.ERROR_PARSE, "bad");

    assertTrue(MCPJsonRpc.isRequest(request));
    assertFalse(MCPJsonRpc.isRequest(notification));
    assertFalse(MCPJsonRpc.isRequest(null));

    assertTrue(MCPJsonRpc.isNotification(notification));
    assertFalse(MCPJsonRpc.isNotification(request));
    assertFalse(MCPJsonRpc.isNotification(null));

    assertTrue(MCPJsonRpc.isResponse(response));
    assertTrue(MCPJsonRpc.isResponse(error));
    assertFalse(MCPJsonRpc.isResponse(request));
    assertFalse(MCPJsonRpc.isResponse(new JsonObject()));
    assertFalse(MCPJsonRpc.isResponse(null));
  }

  @Test
  void extractIdReturnsTheIdAsAJavaValue() {
    assertNull(MCPJsonRpc.extractId(null));
    assertNull(MCPJsonRpc.extractId(new JsonObject()));
    assertNull(MCPJsonRpc.extractId(MCPJsonRpc.response(null, null)));
    assertEquals(5, ((Number) MCPJsonRpc.extractId(MCPJsonRpc.request(5, "m", null))).intValue());
    assertEquals("abc", MCPJsonRpc.extractId(MCPJsonRpc.request("abc", "m", null)));
    assertEquals(Boolean.FALSE, MCPJsonRpc.extractId(MCPJsonRpc.request(false, "m", null)));

    JsonObject arrayId = new JsonObject();
    JsonArray id = new JsonArray();
    id.add(1);
    arrayId.add("id", id);
    assertEquals("[1]", MCPJsonRpc.extractId(arrayId));
  }

  @Test
  void registryLooksUpReplacesAndUnregistersByNameAndUri() {
    MCPToolRegistry registry = new MCPToolRegistry();
    assertThrows(IllegalArgumentException.class, () -> registry.registerTool(null));
    assertThrows(IllegalArgumentException.class, () -> registry.registerResource(null));

    MCPTool first = tool("frostwire_a");
    MCPTool replacement = tool("frostwire_a");
    registry.registerTool(first);
    registry.registerTool(replacement);
    registry.registerTool(tool("frostwire_b"));
    assertSame(replacement, registry.getTool("frostwire_a"), "same name replaces the tool");
    assertEquals(2, registry.getTools().size());
    assertTrue(registry.hasTool("frostwire_b"));
    registry.unregisterTool("frostwire_b");
    assertFalse(registry.hasTool("frostwire_b"));
    assertNull(registry.getTool("frostwire_b"));

    MCPResource resource = resource("frostwire://status");
    registry.registerResource(resource);
    assertSame(resource, registry.getResource("frostwire://status"));
    assertTrue(registry.hasResource("frostwire://status"));
    Collection<MCPResource> resources = registry.getResources();
    assertEquals(1, resources.size());
    assertThrows(UnsupportedOperationException.class, resources::clear);
    assertThrows(UnsupportedOperationException.class, () -> registry.getTools().clear());
    registry.unregisterResource("frostwire://status");
    assertFalse(registry.hasResource("frostwire://status"));
    assertTrue(resources.isEmpty(), "the returned view tracks the registry");
  }

  private static MCPTool tool(String name) {
    return new MCPTool() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return name;
      }

      @Override
      public JsonObject inputSchema() {
        return new JsonObject();
      }

      @Override
      public JsonObject execute(JsonObject arguments) {
        return new JsonObject();
      }
    };
  }

  private static MCPResource resource(String uri) {
    return new MCPResource() {
      @Override
      public String uri() {
        return uri;
      }

      @Override
      public String name() {
        return uri;
      }

      @Override
      public String description() {
        return uri;
      }

      @Override
      public String mimeType() {
        return "text/plain";
      }

      @Override
      public String read() {
        return "";
      }
    };
  }
}
