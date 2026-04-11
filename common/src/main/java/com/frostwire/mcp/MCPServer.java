package com.frostwire.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;

public class MCPServer implements MCPRequestHandler {

    private final MCPToolRegistry registry;
    private MCPTransport transport;
    private volatile boolean initialized;
    private volatile boolean started;

    public MCPServer() {
        this.registry = new MCPToolRegistry();
    }

    public void registerTool(MCPTool tool) {
        registry.registerTool(tool);
    }

    public void registerResource(MCPResource resource) {
        registry.registerResource(resource);
    }

    public void setTransport(MCPTransport transport) {
        this.transport = transport;
    }

    public void start() {
        if (transport == null) {
            throw new IllegalStateException("transport not set");
        }
        transport.start(this::handleRequest);
        started = true;
    }

    public void stop() {
        if (transport != null) {
            transport.stop();
        }
        initialized = false;
        started = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isStarted() {
        return started;
    }

    public void sendNotification(String method, JsonObject params) {
        if (transport == null) {
            throw new IllegalStateException("transport not set");
        }
        JsonObject notification = MCPJsonRpc.notification(method, params);
        transport.sendNotification(notification);
    }

    @Override
    public JsonObject handleRequest(JsonObject request) {
        if (request == null) {
            return MCPJsonRpc.errorResponse(null, MCPConstants.ERROR_PARSE, "Invalid JSON");
        }

        if (!request.has("method")) {
            return MCPJsonRpc.errorResponse(
                    MCPJsonRpc.extractId(request),
                    MCPConstants.ERROR_INVALID_REQUEST,
                    "Missing method field");
        }

        String method;
        try {
            method = request.get("method").getAsString();
        } catch (Exception e) {
            return MCPJsonRpc.errorResponse(
                    MCPJsonRpc.extractId(request),
                    MCPConstants.ERROR_INVALID_REQUEST,
                    "Invalid method field");
        }

        switch (method) {
            case "initialize":
                initialized = true;
                return handleInitialize(request);
            case "notifications/initialized":
                return null;
            case "tools/list":
                if (!initialized) {
                    return MCPJsonRpc.errorResponse(
                            MCPJsonRpc.extractId(request),
                            MCPConstants.ERROR_INVALID_REQUEST,
                            "Server not initialized");
                }
                return handleToolsList(request);
            case "tools/call":
                if (!initialized) {
                    return MCPJsonRpc.errorResponse(
                            MCPJsonRpc.extractId(request),
                            MCPConstants.ERROR_INVALID_REQUEST,
                            "Server not initialized");
                }
                return handleToolsCall(request);
            case "resources/list":
                if (!initialized) {
                    return MCPJsonRpc.errorResponse(
                            MCPJsonRpc.extractId(request),
                            MCPConstants.ERROR_INVALID_REQUEST,
                            "Server not initialized");
                }
                return handleResourcesList(request);
            case "resources/read":
                if (!initialized) {
                    return MCPJsonRpc.errorResponse(
                            MCPJsonRpc.extractId(request),
                            MCPConstants.ERROR_INVALID_REQUEST,
                            "Server not initialized");
                }
                return handleResourcesRead(request);
            case "ping":
                return handlePing(request);
            default:
                return MCPJsonRpc.errorResponse(
                        MCPJsonRpc.extractId(request),
                        MCPConstants.ERROR_METHOD_NOT_FOUND,
                        "Method not found: " + method);
        }
    }

    private JsonObject handleInitialize(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);

        JsonObject capabilities = new JsonObject();
        JsonObject toolsCap = new JsonObject();
        toolsCap.addProperty("listChanged", false);
        capabilities.add("tools", toolsCap);
        JsonObject resourcesCap = new JsonObject();
        resourcesCap.addProperty("subscribe", true);
        resourcesCap.addProperty("listChanged", true);
        capabilities.add("resources", resourcesCap);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", MCPConstants.SERVER_NAME);
        serverInfo.addProperty("version", MCPConstants.SERVER_VERSION);

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", MCPConstants.PROTOCOL_VERSION);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);

        return MCPJsonRpc.response(id, result);
    }

    private JsonObject handleToolsList(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);
        JsonArray toolsArray = new JsonArray();

        for (MCPTool tool : registry.getTools()) {
            JsonObject toolObj = new JsonObject();
            toolObj.addProperty("name", tool.name());
            toolObj.addProperty("description", tool.description());
            JsonObject schema = tool.inputSchema();
            toolObj.add("inputSchema", schema != null ? schema : new JsonObject());
            toolsArray.add(toolObj);
        }

        JsonObject result = new JsonObject();
        result.add("tools", toolsArray);
        return MCPJsonRpc.response(id, result);
    }

    private JsonObject handleToolsCall(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : null;

        if (params == null || !params.has("name")) {
            return MCPJsonRpc.errorResponse(id, MCPConstants.ERROR_INVALID_PARAMS,
                    "Missing tool name in params");
        }

        String toolName = params.get("name").getAsString();
        MCPTool tool = registry.getTool(toolName);

        if (tool == null) {
            return MCPJsonRpc.response(id, buildToolError("Tool not found: " + toolName));
        }

        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        try {
            JsonObject toolResult = tool.execute(arguments);
            JsonArray contentArray = new JsonArray();
            if (toolResult != null) {
                JsonObject textItem = new JsonObject();
                textItem.addProperty("type", "text");
                textItem.addProperty("text", new com.google.gson.Gson().toJson(toolResult));
                contentArray.add(textItem);
            }
            JsonObject content = new JsonObject();
            content.add("content", contentArray);
            return MCPJsonRpc.response(id, content);
        } catch (Exception e) {
            return MCPJsonRpc.response(id, buildToolError("Tool execution failed: " + e.getMessage()));
        }
    }

    private JsonObject handleResourcesList(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);
        JsonArray resourcesArray = new JsonArray();

        for (MCPResource resource : registry.getResources()) {
            JsonObject resObj = new JsonObject();
            resObj.addProperty("uri", resource.uri());
            resObj.addProperty("name", resource.name());
            resObj.addProperty("description", resource.description());
            resObj.addProperty("mimeType", resource.mimeType());
            resourcesArray.add(resObj);
        }

        JsonObject result = new JsonObject();
        result.add("resources", resourcesArray);
        return MCPJsonRpc.response(id, result);
    }

    private JsonObject handleResourcesRead(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : null;

        if (params == null || !params.has("uri")) {
            return MCPJsonRpc.errorResponse(id, MCPConstants.ERROR_INVALID_PARAMS,
                    "Missing resource uri in params");
        }

        String uri = params.get("uri").getAsString();
        MCPResource resource = registry.getResource(uri);

        if (resource == null) {
            return MCPJsonRpc.errorResponse(id, MCPConstants.ERROR_INVALID_PARAMS,
                    "Resource not found: " + uri);
        }

        try {
            String content = resource.read();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("uri", resource.uri());
            textContent.addProperty("mimeType", resource.mimeType());
            textContent.addProperty("text", content != null ? content : "");

            JsonArray contents = new JsonArray();
            contents.add(textContent);

            JsonObject result = new JsonObject();
            result.add("contents", contents);
            return MCPJsonRpc.response(id, result);
        } catch (Exception e) {
            return MCPJsonRpc.errorResponse(id, MCPConstants.ERROR_INTERNAL,
                    "Failed to read resource: " + e.getMessage());
        }
    }

    private JsonObject handlePing(JsonObject request) {
        Object id = MCPJsonRpc.extractId(request);
        return MCPJsonRpc.response(id, new JsonObject());
    }

    private JsonObject buildToolError(String message) {
        JsonObject textItem = new JsonObject();
        textItem.addProperty("type", "text");
        textItem.addProperty("text", message);

        JsonArray content = new JsonArray();
        content.add(textItem);

        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", true);
        return result;
    }
}