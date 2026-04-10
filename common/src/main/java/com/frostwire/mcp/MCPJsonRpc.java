package com.frostwire.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class MCPJsonRpc {

    private static final String JSONRPC = "2.0";
    private static final String KEY_JSONRPC = "jsonrpc";
    private static final String KEY_ID = "id";
    private static final String KEY_METHOD = "method";
    private static final String KEY_PARAMS = "params";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ERROR = "error";
    private static final String KEY_CODE = "code";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_DATA = "data";

    private MCPJsonRpc() {
    }

    public static JsonObject request(Object id, String method, JsonObject params) {
        JsonObject req = new JsonObject();
        req.addProperty(KEY_JSONRPC, JSONRPC);
        addId(req, id);
        req.addProperty(KEY_METHOD, method);
        if (params != null) {
            req.add(KEY_PARAMS, params);
        }
        return req;
    }

    public static JsonObject response(Object id, JsonObject result) {
        JsonObject resp = new JsonObject();
        resp.addProperty(KEY_JSONRPC, JSONRPC);
        addId(resp, id);
        resp.add(KEY_RESULT, result != null ? result : JsonNull.INSTANCE);
        return resp;
    }

    public static JsonObject errorResponse(Object id, int code, String message) {
        return errorResponse(id, code, message, null);
    }

    public static JsonObject errorResponse(Object id, int code, String message, JsonObject data) {
        JsonObject resp = new JsonObject();
        resp.addProperty(KEY_JSONRPC, JSONRPC);
        addId(resp, id);
        JsonObject error = new JsonObject();
        error.addProperty(KEY_CODE, code);
        error.addProperty(KEY_MESSAGE, message);
        if (data != null) {
            error.add(KEY_DATA, data);
        }
        resp.add(KEY_ERROR, error);
        return resp;
    }

    public static JsonObject notification(String method, JsonObject params) {
        JsonObject notif = new JsonObject();
        notif.addProperty(KEY_JSONRPC, JSONRPC);
        notif.addProperty(KEY_METHOD, method);
        if (params != null) {
            notif.add(KEY_PARAMS, params);
        }
        return notif;
    }

    public static boolean isRequest(JsonObject msg) {
        return msg != null && msg.has(KEY_METHOD) && msg.has(KEY_ID);
    }

    public static boolean isResponse(JsonObject msg) {
        return msg != null && !msg.has(KEY_METHOD) && (msg.has(KEY_RESULT) || msg.has(KEY_ERROR));
    }

    public static boolean isNotification(JsonObject msg) {
        return msg != null && msg.has(KEY_METHOD) && !msg.has(KEY_ID);
    }

    public static Object extractId(JsonObject request) {
        if (request == null || !request.has(KEY_ID)) {
            return null;
        }
        JsonElement idElement = request.get(KEY_ID);
        if (idElement.isJsonNull()) {
            return null;
        }
        if (idElement.isJsonPrimitive()) {
            JsonPrimitive prim = idElement.getAsJsonPrimitive();
            if (prim.isNumber()) {
                return prim.getAsNumber();
            }
            if (prim.isString()) {
                return prim.getAsString();
            }
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
        }
        return idElement.toString();
    }

    private static void addId(JsonObject obj, Object id) {
        if (id == null) {
            obj.add(KEY_ID, JsonNull.INSTANCE);
        } else if (id instanceof Number) {
            obj.addProperty(KEY_ID, (Number) id);
        } else if (id instanceof String) {
            obj.addProperty(KEY_ID, (String) id);
        } else if (id instanceof Boolean) {
            obj.addProperty(KEY_ID, (Boolean) id);
        } else {
            obj.addProperty(KEY_ID, id.toString());
        }
    }
}