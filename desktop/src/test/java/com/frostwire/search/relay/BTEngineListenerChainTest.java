/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.bittorrent.BTEngineAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BTEngineListenerChainTest {

    @Test
    void chainFansOutEventsToAllDelegates() {
        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        BTEngineListenerChain chain = new BTEngineListenerChain(a, b);

        BTEngine engine = null;
        chain.started(engine);
        chain.stopped(engine);
        chain.downloadAdded(engine, null);
        chain.downloadUpdate(engine, null);

        assertEquals(4, a.events.size());
        assertEquals(4, b.events.size());
        assertEquals("started", a.events.get(0));
        assertEquals("downloadUpdate", b.events.get(3));
    }

    @Test
    void throwingListenerDoesNotStopChain() {
        RecordingListener a = new RecordingListener();
        BTEngineListener throwing = new BTEngineAdapter() {
            @Override
            public void downloadAdded(BTEngine engine, BTDownload dl) {
                throw new IllegalStateException("boom");
            }
        };
        RecordingListener b = new RecordingListener();
        BTEngineListenerChain chain = new BTEngineListenerChain(a, throwing, b);

        chain.downloadAdded(null, null);

        assertTrue(a.events.contains("downloadAdded"));
        assertTrue(b.events.contains("downloadAdded"));
    }

    @Test
    void withAppendsListenerAndPreservesOrder() {
        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        RecordingListener c = new RecordingListener();
        BTEngineListenerChain chain = new BTEngineListenerChain(a, b).with(c);

        chain.downloadAdded(null, null);

        assertEquals(1, a.events.size());
        assertEquals(1, b.events.size());
        assertEquals(1, c.events.size());
    }

    @Test
    void withRejectsNull() {
        BTEngineListenerChain chain = new BTEngineListenerChain();
        assertThrows(IllegalArgumentException.class, () -> chain.with(null));
    }

    @Test
    void constructorRejectsNullArray() {
        assertThrows(NullPointerException.class, () -> new BTEngineListenerChain((BTEngineListener[]) null));
    }

    @Test
    void nullDelegateIsSkipped() {
        RecordingListener a = new RecordingListener();
        BTEngineListenerChain chain = new BTEngineListenerChain(a, null);

        chain.downloadAdded(null, null);

        assertEquals(1, a.events.size());
    }

    @Test
    void withDoesNotAddDuplicateListener() {
        RecordingListener a = new RecordingListener();
        BTEngineListenerChain chain = new BTEngineListenerChain(a);
        BTEngineListenerChain chain2 = chain.with(a);

        assertSame(chain, chain2);
        assertEquals(1, chain2.size());
    }

    private static final class RecordingListener extends BTEngineAdapter {
        final List<String> events = new ArrayList<>();

        @Override
        public void started(BTEngine engine) {
            events.add("started");
        }

        @Override
        public void stopped(BTEngine engine) {
            events.add("stopped");
        }

        @Override
        public void downloadAdded(BTEngine engine, BTDownload dl) {
            events.add("downloadAdded");
        }

        @Override
        public void downloadUpdate(BTEngine engine, BTDownload dl) {
            events.add("downloadUpdate");
        }
    }
}
