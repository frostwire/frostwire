/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import android.app.Application;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.TorrentMetadataProvider;
import com.frostwire.search.relay.TorrentMetadataRequest;
import com.frostwire.search.relay.TorrentMetadataResponse;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.util.Hex;
import org.junit.Test;

import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidPublicMetadataTest {
    @Test
    public void withdrawalDeniesCachedPayloadBeforeProviderBytesAreRead() throws Exception {
        IdentityKeys holder = identityFromJdkKey();
        IdentityKeys requester = identityFromJdkKey();
        AtomicBoolean shared = new AtomicBoolean(true);
        AtomicBoolean participating = new AtomicBoolean(true);
        AndroidShareVisibility visibility = new AndroidShareVisibility(participating::get, hash -> shared.get());
        AtomicInteger reads = new AtomicInteger();
        List<byte[]> replies = new ArrayList<>();
        DistributedSearchTransport transport = new DistributedSearchTransport() {
            @Override public boolean send(byte[] target, int protocol, byte[] payload) {
                replies.add(payload);
                return true;
            }
            @Override public void addListener(PayloadListener listener) {}
            @Override public void removeListener(PayloadListener listener) {}
        };
        LocalIndex index = mock(LocalIndex.class);
        IncomingSearchRequestHandler handler = new IncomingSearchRequestHandler(transport,
                new RelaySearchService(index, holder, visibility), null, holder, index, visibility);
        handler.setTorrentMetadataProvider(new TorrentMetadataProvider() {
            @Override public boolean isPubliclyShared(byte[] hash) {
                return visibility.isVisible(Hex.encode(hash));
            }
            @Override public byte[] torrentBytes(byte[] hash) {
                reads.incrementAndGet();
                return new byte[16];
            }
        });
        handler.start();
        try {
            request(handler, requester, 1);
            assertEquals(1, reads.get());
            assertEquals(1, handler.torrentCacheSize());
            request(handler, requester, 2);
            assertEquals(1, reads.get());
            shared.set(false);
            replies.clear();
            request(handler, requester, 3);
            assertEquals(1, reads.get());
            assertEquals(0, handler.torrentCacheSize());
            assertEquals(1, replies.size());
            assertEquals(TorrentMetadataResponse.ERR_NOT_FOUND,
                    SearchPayloadCodec.decodeTorrentMetadataResponse(replies.get(0)).error());
            shared.set(true);
            participating.set(false);
            request(handler, requester, 4);
            assertEquals(1, reads.get());
        } finally {
            handler.stop();
        }
    }

    private static IdentityKeys identityFromJdkKey() throws Exception {
        java.security.KeyPair kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] seed = kp.getPrivate().getEncoded();
        byte[] rawSeed = java.util.Arrays.copyOfRange(seed, seed.length - 32, seed.length);
        byte[] pubEnc = kp.getPublic().getEncoded();
        byte[] rawPub = java.util.Arrays.copyOfRange(pubEnc, pubEnc.length - 32, pubEnc.length);
        return TestIdentityKeys.fromSeedWithoutNative(rawSeed, rawPub);
    }

    private static void request(IncomingSearchRequestHandler handler, IdentityKeys requester, int id)
            throws Exception {
        byte[] nonce = new byte[32];
        nonce[0] = (byte) id;
        byte[] hash = new byte[20];
        long timestamp = System.currentTimeMillis() / 1000L;
        TorrentMetadataRequest unsigned = TorrentMetadataRequest.builder().infoHash(hash).nonce(nonce)
                .requesterPub(requester.ed25519PubRaw()).timestamp(timestamp).signature(new byte[64]).build();
        Signature signer = IdentityKeys.softwareSignature("Ed25519");
        signer.initSign(requester.ed25519().getPrivate());
        signer.update(unsigned.canonicalBytes());
        TorrentMetadataRequest signed = TorrentMetadataRequest.builder().infoHash(hash).nonce(nonce)
                .requesterPub(requester.ed25519PubRaw()).timestamp(timestamp).signature(signer.sign()).build();
        handler.onPayload(requester.ed25519PubRaw(), SearchPayloadCodec.encodeTorrentMetadataRequest(signed),
                0, MeshProtocolId.METADATA);
    }
}
