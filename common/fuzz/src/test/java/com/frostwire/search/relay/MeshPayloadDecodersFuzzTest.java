/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.icebridge.MeshEnvelope;
import com.frostwire.search.relay.icebridge.NodeMetaPayload;
import com.frostwire.search.relay.icebridge.udp.RelayFrame;

/**
 * Fuzzes the decoders of bytes other peers send over the IceBridge mesh: the envelope, relay
 * frames, NODE_META and INDEX_DIGEST announcements, the JSON search, catalog and torrent metadata
 * payloads, and index manifests. The first byte picks the decoder, the rest is its input.
 *
 * <p>Documented rejections are {@link IllegalArgumentException} (envelope, relay frame) and a null
 * or -1 result (the others). Anything else escaping, or an input that takes seconds, is a bug: a
 * remote peer can send any of these.
 */
class MeshPayloadDecodersFuzzTest {

  @FuzzTest
  void decode(FuzzedDataProvider data) {
    int decoder = data.consumeInt(0, 9);
    byte[] bytes = data.consumeRemainingAsBytes();
    try {
      switch (decoder) {
        case 0 -> MeshEnvelope.unwrap(bytes);
        case 1 -> RelayFrame.decode(bytes);
        case 2 -> NodeMetaPayload.decode(bytes);
        case 3 -> IndexDigest.fromBytes(bytes);
        case 4 -> SearchPayloadCodec.decodeRequest(bytes);
        case 5 -> SearchPayloadCodec.decodeResponse(bytes);
        case 6 -> SearchPayloadCodec.decodeCatalogBrowseRequest(bytes);
        case 7 -> SearchPayloadCodec.decodeTorrentMetadataRequest(bytes);
        case 8 -> SearchPayloadCodec.decodeTorrentMetadataResponse(bytes);
        default -> RemoteIndexFetcher.parseManifest(bytes);
      }
    } catch (IllegalArgumentException documented) {
      // MeshEnvelope.unwrap and RelayFrame.decode reject malformed frames this way.
    }
  }
}
