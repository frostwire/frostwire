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
import com.frostwire.util.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Composite {@link BTEngineListener} that fans events out to a
 * fixed set of inner listeners. Each inner listener is invoked from
 * the same call site as the chain, so off-EDT work still happens
 * inside the inner listener (this class is a pure dispatch helper).
 */
public final class BTEngineListenerChain implements BTEngineListener {

    private static final Logger LOG = Logger.getLogger(BTEngineListenerChain.class);

    private final List<BTEngineListener> delegates;

    public BTEngineListenerChain(BTEngineListener... delegates) {
        Objects.requireNonNull(delegates, "delegates");
        this.delegates = Arrays.asList(delegates.clone());
    }

    /**
     * Replaces the engine's current listener with a chain that
     * preserves any existing listener and adds the new one. Returns
     * the chain that was installed, or the existing listener if no
     * new listener was provided.
     */
    public static BTEngineListener install(BTEngine engine, BTEngineListener extra) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is null");
        }
        if (extra == null) {
            return engine.getListener();
        }
        BTEngineListener existing = engine.getListener();
        BTEngineListener chain;
        if (existing == null) {
            chain = extra;
        } else if (existing instanceof BTEngineListenerChain) {
            chain = ((BTEngineListenerChain) existing).with(extra);
        } else {
            chain = new BTEngineListenerChain(existing, extra);
        }
        engine.setListener(chain);
        LOG.info("Installed BTEngineListenerChain with " +
                (chain instanceof BTEngineListenerChain
                        ? ((BTEngineListenerChain) chain).delegates.size()
                        : 1) + " listener(s)");
        return chain;
    }

    public BTEngineListenerChain with(BTEngineListener extra) {
        if (extra == null) {
            throw new IllegalArgumentException("extra is null");
        }
        BTEngineListener[] merged = new BTEngineListener[delegates.size() + 1];
        for (int i = 0; i < delegates.size(); i++) {
            merged[i] = delegates.get(i);
        }
        merged[delegates.size()] = extra;
        return new BTEngineListenerChain(merged);
    }

    @Override
    public void started(BTEngine engine) {
        forEach(d -> d.started(engine));
    }

    @Override
    public void stopped(BTEngine engine) {
        forEach(d -> d.stopped(engine));
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
        forEach(d -> d.downloadAdded(engine, dl));
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
        forEach(d -> d.downloadUpdate(engine, dl));
    }

    private void forEach(java.util.function.Consumer<BTEngineListener> action) {
        for (BTEngineListener d : delegates) {
            if (d == null) {
                continue;
            }
            try {
                action.accept(d);
            } catch (Throwable t) {
                LOG.warn("Listener " + d + " threw, continuing", t);
            }
        }
    }

    // Defensive: a thin re-export of a list snapshot for tests/diagnostics.
    List<BTEngineListener> delegates() {
        return new CopyOnWriteArrayList<>(delegates);
    }
}
