/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests.dht;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.extension.*;

public class LocalDhtCluster implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final Logger LOG = Logger.getLogger(LocalDhtCluster.class);
    private static final String EXTENSION_KEY = "LocalDhtCluster";
    
    private final int nodeCount;
    private final int dhtReadyTimeoutSeconds;
    private final List<SessionManager> nodes = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private final CountDownLatch dhtReadyLatch;
    
    public LocalDhtCluster() {
        this(3, 30);
    }
    
    public LocalDhtCluster(int nodeCount, int dhtReadyTimeoutSeconds) {
        this.nodeCount = nodeCount;
        this.dhtReadyTimeoutSeconds = dhtReadyTimeoutSeconds;
        this.dhtReadyLatch = new CountDownLatch(nodeCount);
    }
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        LOG.info("Starting LocalDhtCluster with " + nodeCount + " nodes");
        
        // Allocate ephemeral ports
        for (int i = 0; i < nodeCount; i++) {
            try (ServerSocket s = new ServerSocket(0)) {
                ports.add(s.getLocalPort());
            }
        }
        
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            SessionManager node = new SessionManager();
            int port = ports.get(nodeIndex);
            
            // Configure settings via SettingsPack
            SettingsPack sp = new SettingsPack();
            sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:" + port);
            
            // Disable UPnP/NAT-PMP for deterministic tests
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
            // DHT only, no torrent storage
            sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 10);
            sp.setInteger(settings_pack.int_types.alert_queue_size.swigValue(), 1000);
            // Enable DHT
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true);
            // Bootstrap nodes
            sp.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), 
                "127.0.0.1:" + ports.get(0));
            
            SessionParams params = new SessionParams(sp);
            
            node.start(params);
            nodes.add(node);
            
            // Add listener to track DHT bootstrap
            node.addListener(new AlertListener() {
                @Override
                public int[] types() {
                    return new int[] { AlertType.DHT_BOOTSTRAP.swig(), AlertType.DHT_ANNOUNCE.swig() };
                }
                
                @Override
                public void alert(Alert<?> alert) {
                    dhtReadyLatch.countDown();
                }
            });
            
            // Bootstrap off node 0 for i > 0
            if (nodeIndex > 0) {
                node.swig().add_dht_node(new string_int_pair("127.0.0.1", ports.get(0)));
            }
        }
        
        boolean ready = dhtReadyLatch.await(dhtReadyTimeoutSeconds, TimeUnit.SECONDS);
        if (!ready) {
            int readyCount = 0;
            for (SessionManager n : nodes) {
                if (n.isDhtRunning()) readyCount++;
            }
            throw new IllegalStateException("Only " + readyCount + " of " + nodeCount + " nodes DHT ready after " + dhtReadyTimeoutSeconds + "s");
        }
        
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(EXTENSION_KEY, this);
    }
    
    @Override
    public void afterAll(ExtensionContext context) {
        context.getStore(ExtensionContext.Namespace.GLOBAL).remove(EXTENSION_KEY);
        
        for (SessionManager node : nodes) {
            try {
                node.stop();
            } catch (Exception e) {
                LOG.error("Error stopping node: ", e);
            }
        }
        nodes.clear();
        ports.clear();
    }
    
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == LocalDhtCluster.class;
    }
    
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        LocalDhtCluster cluster = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(EXTENSION_KEY, LocalDhtCluster.class);
        if (cluster == null) {
            throw new ParameterResolutionException("LocalDhtCluster not initialized. Annotate test class with @ExtendWith(LocalDhtCluster.class)");
        }
        return cluster;
    }
    
    public SessionManager getNode(int index) {
        if (index < 0 || index >= nodes.size()) {
            throw new IndexOutOfBoundsException("Node index out of bounds: " + index);
        }
        return nodes.get(index);
    }
    
    public int getNodeCount() {
        return nodeCount;
    }

    public int getPort(int index) {
        if (index < 0 || index >= ports.size()) {
            throw new IndexOutOfBoundsException("Port index out of bounds: " + index);
        }
        return ports.get(index);
    }
    
    public boolean isNodeReady(int index) {
        if (index < 0 || index >= nodes.size()) {
            throw new IndexOutOfBoundsException("Node index out of bounds: " + index);
        }
        return nodes.get(index).isDhtRunning();
    }
    
}
