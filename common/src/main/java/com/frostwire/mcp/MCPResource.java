/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp;

public interface MCPResource {
    String uri();
    String name();
    String description();
    String mimeType();
    String read();
}
