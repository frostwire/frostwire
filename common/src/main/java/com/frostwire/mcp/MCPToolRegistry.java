package com.frostwire.mcp;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MCPToolRegistry {

    private final Map<String, MCPTool> tools = new ConcurrentHashMap<>();
    private final Map<String, MCPResource> resources = new ConcurrentHashMap<>();

    public MCPToolRegistry() {
    }

    public void registerTool(MCPTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        tools.put(tool.name(), tool);
    }

    public void registerResource(MCPResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        resources.put(resource.uri(), resource);
    }

    public MCPTool getTool(String name) {
        return tools.get(name);
    }

    public MCPResource getResource(String uri) {
        return resources.get(uri);
    }

    public Collection<MCPTool> getTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Collection<MCPResource> getResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public boolean hasResource(String uri) {
        return resources.containsKey(uri);
    }

    public void unregisterTool(String name) {
        tools.remove(name);
    }

    public void unregisterResource(String uri) {
        resources.remove(uri);
    }
}