/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.icebridge.control.InboundMessageInfo;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Construct the immutable control-plane frame rather than setting its final fields reflectively. */
final class InboundMessageInfoAdapter extends TypeAdapter<InboundMessageInfo> {

    @Override
    public InboundMessageInfo read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        String sourcePub = null;
        String payload = null;
        long receivedMs = 0;
        int protocolId = MeshProtocolId.SEARCH; // absent on legacy /poll frames
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                continue;
            }
            switch (name) {
                case "sourcePub": sourcePub = reader.nextString(); break;
                case "payload": payload = reader.nextString(); break;
                case "receivedMs": receivedMs = reader.nextLong(); break;
                case "protocolId": protocolId = reader.nextInt(); break;
                default: reader.skipValue();
            }
        }
        reader.endObject();
        return new InboundMessageInfo(sourcePub, payload, receivedMs, protocolId);
    }

    @Override
    public void write(JsonWriter writer, InboundMessageInfo value) throws IOException {
        if (value == null) {
            writer.nullValue();
            return;
        }
        writer.beginObject();
        writer.name("sourcePub").value(value.sourcePub);
        writer.name("payload").value(value.payload);
        writer.name("receivedMs").value(value.receivedMs);
        writer.name("protocolId").value(value.protocolId);
        writer.endObject();
    }
}
