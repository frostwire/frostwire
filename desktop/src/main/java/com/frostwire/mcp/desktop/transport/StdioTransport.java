package com.frostwire.mcp.desktop.transport;

import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class StdioTransport implements MCPTransport {

  // Keeps JSON nulls: JSON-RPC requires "id": null on errors for requests without a readable id.
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  private volatile boolean running;
  private MCPTransportHandler handler;
  private final BufferedReader reader;
  private final PrintWriter writer;
  private Thread readerThread;

  public StdioTransport() {
    this(System.in, System.out);
  }

  public StdioTransport(InputStream in, OutputStream out) {
    this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    this.writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
  }

  @Override
  public void start(MCPTransportHandler handler) {
    this.handler = handler;
    this.running = true;
    this.readerThread = Thread.startVirtualThread(this::readLoop);
  }

  private void readLoop() {
    try {
      String line;
      while (running && (line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) continue;
        try {
          JsonObject request = JsonParser.parseString(line).getAsJsonObject();
          JsonObject response = handler.handleRequest(request);
          if (response != null) {
            synchronized (writer) {
              writer.println(GSON.toJson(response));
              writer.flush();
            }
          }
        } catch (Exception e) {
          JsonObject errorResponse = new JsonObject();
          errorResponse.addProperty("jsonrpc", "2.0");
          errorResponse.add("id", com.google.gson.JsonNull.INSTANCE);
          JsonObject error = new JsonObject();
          error.addProperty("code", -32700);
          error.addProperty("message", "Parse error: " + e.getMessage());
          errorResponse.add("error", error);
          synchronized (writer) {
            writer.println(GSON.toJson(errorResponse));
            writer.flush();
          }
        }
      }
    } catch (IOException e) {
      if (running) {
        // unexpected disconnect
      }
    }
  }

  @Override
  public void stop() {
    running = false;
    if (readerThread != null) {
      readerThread.interrupt();
    }
  }

  @Override
  public void sendNotification(JsonObject notification) {
    synchronized (writer) {
      writer.println(GSON.toJson(notification));
      writer.flush();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
