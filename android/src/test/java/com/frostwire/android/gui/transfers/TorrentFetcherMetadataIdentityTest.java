/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.gui.RelaySearchWiring;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.TorrentMetadataRequest;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TorrentFetcherMetadataIdentityTest {
  @Test
  public void catalogMagnetProducesSignedRequestForCanonicalV1AndExactHolder() throws Exception {
    IdentityKeys requester = softwareIdentity();
    IdentityKeys holder = softwareIdentity();
    String selected = "d6ce91299d15e8dac37591868de2d1fefabd3e16";
    String uri =
        "magnet:?xt=urn:btih:"
            + selected
            + "&x.hp="
            + Base64.getUrlEncoder().withoutPadding().encodeToString(holder.ed25519PubRaw());
    AtomicReference<byte[]> target = new AtomicReference<>();
    AtomicReference<byte[]> wire = new AtomicReference<>();
    AtomicReference<Integer> protocol = new AtomicReference<>();
    DistributedSearchTransport transport =
        new DistributedSearchTransport() {
          @Override
          public boolean send(byte[] pub, int protocolId, byte[] payload) {
            target.set(pub.clone());
            protocol.set(protocolId);
            wire.set(payload.clone());
            // Admission failure finishes immediately; this test asserts the actual outbound bytes.
            return false;
          }

          @Override
          public void addListener(PayloadListener listener) {}

          @Override
          public void removeListener(PayloadListener listener) {}
        };
    RelaySearchWiring wiring = SearchEngine.DISTRIBUTED_WIRING;
    IdentityKeys previousIdentity = wiring.identity();
    DistributedSearchTransport previousTransport = wiring.searchTransport();
    try {
      wiring.identity(requester).searchTransport(transport);
      TorrentFetcherDownload transfer =
          new TorrentFetcherDownload(
              mock(TransferManager.class),
              new TorrentUrlInfo(uri, "ElGeneral", holder.ed25519PubRaw()),
              null,
              task -> {});
      // Exercise the existing production mesh stage without entering the JNI fallback afterward.
      Method fetch =
          TorrentFetcherDownload.class.getDeclaredMethod("fetchMeshTorrentMetadata", String.class);
      fetch.setAccessible(true);
      assertNull(fetch.invoke(transfer, uri));
      assertArrayEquals(holder.ed25519PubRaw(), target.get());
      assertEquals(Integer.valueOf(MeshProtocolId.METADATA), protocol.get());
      TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(wire.get());
      assertNotNull(request);
      assertArrayEquals(Hex.decode(selected), request.infoHash());
      assertArrayEquals(requester.ed25519PubRaw(), request.requesterPub());
      assertTrue(request.verifySignature());
    } finally {
      wiring.identity(previousIdentity).searchTransport(previousTransport);
    }
  }

  private static IdentityKeys softwareIdentity() throws Exception {
    // This JVM has no Android JNI library. Use real software Ed25519 keys/signatures;
    // substitute only the identity container, not the production signing or wire path.
    KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    IdentityKeys keys = mock(IdentityKeys.class);
    when(keys.ed25519()).thenReturn(keyPair);
    when(keys.ed25519PubRaw()).thenReturn(IdentityRecord.extractRawEd25519(keyPair.getPublic()));
    return keys;
  }
}
