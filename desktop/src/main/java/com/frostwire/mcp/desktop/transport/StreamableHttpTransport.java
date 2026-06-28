package com.frostwire.mcp.desktop.transport;

import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class StreamableHttpTransport implements MCPTransport {

  private final String host;
  private final int port;
  private final TlsConfig tlsConfig;
  private HttpServer httpServer;
  private MCPTransportHandler handler;
  private volatile boolean running;
  private final Map<String, SSEConnection> activeSSEConnections = new ConcurrentHashMap<>();
  private final AtomicInteger sessionCounter = new AtomicInteger(0);
  private Thread keepAliveThread;

  private static final Logger LOG = Logger.getLogger(StreamableHttpTransport.class);

  public StreamableHttpTransport(String host, int port) {
    this(host, port, new TlsConfig());
  }

  public StreamableHttpTransport(String host, int port, TlsConfig tlsConfig) {
    this.host = host;
    this.port = port;
    this.tlsConfig = tlsConfig;
  }

  @Override
  public void start(MCPTransportHandler handler) {
    this.handler = handler;
    try {
      if (tlsConfig != null && tlsConfig.isEnabled()) {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(host, port), 0);
        configureSSL(httpsServer);
        this.httpServer = httpsServer;
      } else {
        this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
      }
      httpServer.createContext("/mcp", new McpHttpHandler());
      httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      httpServer.start();
      running = true;
      startKeepAlive();
    } catch (IOException e) {
      throw new RuntimeException("Failed to start MCP HTTP server: " + e.getMessage(), e);
    }
  }

  @Override
  public void stop() {
    running = false;
    if (keepAliveThread != null) {
      keepAliveThread.interrupt();
    }
    for (SSEConnection conn : activeSSEConnections.values()) {
      conn.close();
    }
    activeSSEConnections.clear();
    if (httpServer != null) {
      httpServer.stop(1);
    }
  }

  @Override
  public void sendNotification(JsonObject notification) {
    String json = new Gson().toJson(notification);
    String sseEvent = "event: message\ndata: " + json + "\n\n";
    for (SSEConnection conn : activeSSEConnections.values()) {
      conn.send(sseEvent);
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  private void startKeepAlive() {
    keepAliveThread =
        Thread.startVirtualThread(
            () -> {
              while (running) {
                try {
                  Thread.sleep(30000);
                  for (SSEConnection conn : activeSSEConnections.values()) {
                    conn.send(": keep-alive\n\n");
                  }
                } catch (InterruptedException e) {
                  break;
                }
              }
            });
  }

  private void configureSSL(HttpsServer server) {
    try {
      SSLContext sslContext;
      if (tlsConfig.getKeystorePath() != null && !tlsConfig.getKeystorePath().isEmpty()) {
        char[] password =
            tlsConfig.getKeystorePassword() != null
                ? tlsConfig.getKeystorePassword().toCharArray()
                : new char[0];
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(tlsConfig.getKeystorePath())) {
          ks.load(fis, password);
        }
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
      } else if (tlsConfig != null && tlsConfig.isAutoGenerateCert()) {
        sslContext = createSelfSignedSSLContext();
      } else {
        throw new RuntimeException("TLS enabled but no keystore and auto-generation disabled");
      }
      server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
    } catch (Exception e) {
      throw new RuntimeException("Failed to configure SSL: " + e.getMessage(), e);
    }
  }

  private SSLContext createSelfSignedSSLContext() throws Exception {
    String hostname = host.equals("0.0.0.0") || host.equals("127.0.0.1") ? "localhost" : host;
    return SelfSignedCertGenerator.createSSLContext(hostname);
  }

  private class McpHttpHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      setCorsHeaders(exchange);
      String method = exchange.getRequestMethod();
      switch (method) {
        case "OPTIONS":
          sendResponse(exchange, 200, "");
          break;
        case "GET":
          handleSSEConnect(exchange);
          break;
        case "POST":
          handleJsonRpc(exchange);
          break;
        case "DELETE":
          handleSessionClose(exchange);
          break;
        default:
          sendResponse(exchange, 405, "Method Not Allowed");
      }
    }

    private void setCorsHeaders(HttpExchange exchange) {
      exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
      exchange
          .getResponseHeaders()
          .set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
      exchange
          .getResponseHeaders()
          .set(
              "Access-Control-Allow-Headers",
              "Content-Type, MCP-Protocol-Version, Mcp-Session-Id, Accept");
    }

    private void handleSSEConnect(HttpExchange exchange) throws IOException {
      String sessionId = "mcp-session-" + sessionCounter.incrementAndGet();
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      exchange.getResponseHeaders().set("Cache-Control", "no-cache");
      exchange.getResponseHeaders().set("Connection", "keep-alive");
      exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
      exchange.sendResponseHeaders(200, 0);

      OutputStream os = exchange.getResponseBody();
      SSEConnection conn = new SSEConnection(sessionId, os);
      activeSSEConnections.put(sessionId, conn);

      // Do NOT send any initial data (comments or events) here.
      // Sending anything (even a neutral comment) can cause clients like Grok's
      // to receive an Sse event at stream open time, which they then consume
      // as the "response" when they later send the "notifications/initialized"
      // notification.
      // The Mcp-Session-Id is provided via the response header (per MCP spec).
      // The stream stays open silently; keep-alives and real messages (incl.
      // "accepted" for initialized) are sent later via the conn or POST responses.

      try {
        while (running && !conn.isClosed()) {
          Thread.sleep(1000);
        }
      } catch (InterruptedException e) {
        // client disconnected
      } finally {
        activeSSEConnections.remove(sessionId);
        try {
          os.close();
        } catch (IOException ignored) {
        }
      }
    }

    private void handleJsonRpc(HttpExchange exchange) throws IOException {
      String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
      if (sessionId != null && !activeSSEConnections.containsKey(sessionId)) {
        // If client references a session we don't know, create tracking
        if (!sessionId.startsWith("mcp-session-")) {
          sendJsonResponse(exchange, 404, "{\"error\":\"Session not found\"}");
          return;
        }
      }

      String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
      boolean wantsSSE = acceptHeader != null && acceptHeader.contains("text/event-stream");

      String body = readRequestBody(exchange);
      JsonElement parsed = JsonParser.parseString(body);

      // Special handling for "notifications/initialized" to satisfy Grok's client (and other
      // streamable-http clients).
      // Per MCP streamable HTTP examples and issues, return 202 Accepted (often empty or "accepted"
      // body).
      // Also push a proper "data: ..." event on the open SSE stream for clients that consume acks
      // via SSE.
      if (parsed.isJsonObject()) {
        JsonObject req = parsed.getAsJsonObject();
        String method = req.has("method") ? req.get("method").getAsString() : "";
        LOG.info(
            "MCP: request method="
                + method
                + " wantsSSE="
                + wantsSSE
                + " session="
                + sessionId
                + " id="
                + (req.has("id") ? req.get("id") : "none"));
        if ("notifications/initialized".equals(method)) {
          LOG.info(
              "MCP: received notifications/initialized, wantsSSE="
                  + wantsSSE
                  + ", session="
                  + sessionId
                  + ", hasConn="
                  + activeSSEConnections.containsKey(sessionId)
                  + " -> sending 200 + \"accepted\" body on POST (no ack push on stream)");
          if (sessionId == null) {
            sessionId = "mcp-session-" + sessionCounter.incrementAndGet();
          }
          exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);

          // Always deliver the ack directly on the POST response body as JSON string.
          // This ensures the client's direct HTTP response for the initialized notification
          // is "accepted" / json (not an Sse(session) object from the stream layer).
          // We do NOT push a special "accepted" event on the main SSE stream here, to avoid
          // injecting a non-jsonrpc message into the client's message stream (which can cause
          // parse issues or the ack to be consumed as a regular event, leading to stalls later).
          String responseJson = "\"accepted\"";
          byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
          return; // handled
        }
      }

      if (wantsSSE) {
        SSEConnection targetConn;
        String responseSessionId;
        boolean usingExisting = false;
        if (sessionId != null && activeSSEConnections.containsKey(sessionId)) {
          targetConn = activeSSEConnections.get(sessionId);
          responseSessionId = sessionId;
          usingExisting = true;
          // For existing main SSE stream: return a plain short response on this POST.
          // Set application/json (not text/event-stream) so the client sees a known content type
          // on the POST response instead of "None". The real event will be delivered on the main
          // SSE GET the client already has open (with matching request id).
          exchange.getResponseHeaders().set("Mcp-Session-Id", responseSessionId);
          // Use 202 + application/json (empty body) to signal that the request was accepted
          // and the actual result (for id-bearing requests) will be delivered as an SSE event
          // on the main long-lived stream the client already holds. This prevents the client
          // library from treating the POST response body as the final result (or complaining
          // about missing/None content type).
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(202, 0);
          // no body on this exchange; real data on main SSE with matching id
        } else {
          responseSessionId = "mcp-session-" + sessionCounter.incrementAndGet();
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.getResponseHeaders().set("Mcp-Session-Id", responseSessionId);
          exchange.getResponseHeaders().set("Cache-Control", "no-cache");
          exchange.sendResponseHeaders(200, 0);
          OutputStream os = exchange.getResponseBody();
          targetConn = new SSEConnection(responseSessionId, os);
          activeSSEConnections.put(responseSessionId, targetConn);
        }

        try {
          if (parsed.isJsonArray()) {
            for (JsonElement elem : parsed.getAsJsonArray()) {
              JsonObject request = elem.getAsJsonObject();
              JsonObject response = handler.handleRequest(request);
              if (response != null) {
                LOG.info("MCP: sending batch response via SSE");
                targetConn.send("event: message\ndata: " + new Gson().toJson(response) + "\n\n");
              }
            }
          } else if (parsed.isJsonObject()) {
            JsonObject req = parsed.getAsJsonObject();
            String method = req.has("method") ? req.get("method").getAsString() : "";
            JsonObject response = handler.handleRequest(req);
            if ("notifications/initialized".equals(method)) {
              LOG.info(
                  "MCP: received notifications/initialized, sending accepted on SSE (existing="
                      + usingExisting
                      + ")");
              targetConn.send("event: message\ndata: \"accepted\"\n\n");
            } else if (response != null) {
              LOG.info("MCP: sending response via SSE for method=" + method);
              targetConn.send("event: message\ndata: " + new Gson().toJson(response) + "\n\n");
            }
          }
        } finally {
          if (!usingExisting) {
            targetConn.close();
          }
        }
      } else {
        JsonElement resultElement;
        if (parsed.isJsonArray()) {
          JsonArray responses = new JsonArray();
          for (JsonElement elem : parsed.getAsJsonArray()) {
            JsonObject request = elem.getAsJsonObject();
            JsonObject response = handler.handleRequest(request);
            if (response != null) {
              responses.add(response);
            }
          }
          resultElement = responses;
        } else if (parsed.isJsonObject()) {
          JsonObject req = parsed.getAsJsonObject();
          String method = req.has("method") ? req.get("method").getAsString() : "";
          JsonObject response = handler.handleRequest(req);
          if ("notifications/initialized".equals(method)) {
            LOG.info("MCP: received notifications/initialized, sending accepted as json");
            // return plain "accepted" body via the helper (helper will json-escape to
            // "\"accepted\"")
            resultElement = new com.google.gson.JsonPrimitive("accepted");
          } else {
            resultElement = response != null ? response : new JsonObject();
          }
        } else {
          resultElement = new JsonObject();
        }
        String responseJson = new Gson().toJson(resultElement);
        if (sessionId == null) {
          sessionId = "mcp-session-" + sessionCounter.incrementAndGet();
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        sendResponse(exchange, 200, responseJson);
      }
    }

    private void handleSessionClose(HttpExchange exchange) throws IOException {
      String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
      if (sessionId != null) {
        SSEConnection conn = activeSSEConnections.remove(sessionId);
        if (conn != null) {
          conn.close();
        }
      }
      sendResponse(exchange, 200, "{}");
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
      try (InputStream is = exchange.getRequestBody();
          ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
          bos.write(buffer, 0, len);
        }
        return bos.toString(StandardCharsets.UTF_8);
      }
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(code, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(code, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    }
  }

  private static class SSEConnection {
    private final String sessionId;
    private final OutputStream outputStream;
    private volatile boolean closed;

    SSEConnection(String sessionId, OutputStream outputStream) {
      this.sessionId = sessionId;
      this.outputStream = outputStream;
    }

    synchronized void send(String data) {
      if (closed) return;
      try {
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
      } catch (IOException e) {
        closed = true;
      }
    }

    void close() {
      closed = true;
      try {
        outputStream.close();
      } catch (IOException ignored) {
      }
    }

    boolean isClosed() {
      return closed;
    }

    String getSessionId() {
      return sessionId;
    }
  }
}
