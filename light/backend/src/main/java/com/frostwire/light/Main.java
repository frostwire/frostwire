/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.light;

import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.LibTorrent;
import com.frostwire.light.platform.LightPlatform;
import com.frostwire.light.util.SystemUtils;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static com.frostwire.light.Main.ExecutorID.*;

// IntelliJ Run configuration:
// Main Class: com.frostwire.light.Main
// VM Options: -Djava.library.path=lib
// Use classpath of module: backend_main

public final class Main {
    private static Logger LOG = Logger.getLogger(Main.class);

    enum ExecutorID {
        GENERAL,
        CLOUD_SEARCH,
        TORRENT_INDEX_SEARCH,
        P2P_SEARCH
    }

    private Main() {
        ConfigurationManager.create();
        ConfigurationManager configurationManager = ConfigurationManager.instance();
        final Map<ExecutorID, ExecutorService> EXECUTORS = initExecutorServices(configurationManager);
        setupBTEngineAsync(configurationManager, EXECUTORS);
        RuntimeEnvironment runtimeEnvironment = RuntimeEnvironment.create();
        initSearchEngines(runtimeEnvironment, configurationManager, EXECUTORS);
        initRoutesAsync(runtimeEnvironment, configurationManager, EXECUTORS.get(GENERAL));
    }

    private Map<ExecutorID, ExecutorService> initExecutorServices(final ConfigurationManager configurationManager) {
        final Map<ExecutorID, ExecutorService> executorsMap = new HashMap<>();
        executorsMap.put(GENERAL, ThreadPool.newThreadPool(GENERAL.name(), 4, false));
        executorsMap.put(TORRENT_INDEX_SEARCH, ThreadPool.newThreadPool(TORRENT_INDEX_SEARCH.name(), 4, false));
        executorsMap.put(CLOUD_SEARCH, ThreadPool.newThreadPool(CLOUD_SEARCH.name(), 4, false));
        executorsMap.put(P2P_SEARCH, ThreadPool.newThreadPool(P2P_SEARCH.name(), 1, false));
        return executorsMap;
    }

    private static void initSearchEngines(final RuntimeEnvironment runtimeEnvironment,
                                          final ConfigurationManager configurationManager,
                                          final Map<ExecutorID, ExecutorService> EXECUTORS) {
        LocalSearchEngine.create(
                runtimeEnvironment.userAgent,
                EXECUTORS.get(CLOUD_SEARCH),
                EXECUTORS.get(TORRENT_INDEX_SEARCH),
                EXECUTORS.get(P2P_SEARCH));
    }

    private static void setupBTEngineAsync(final ConfigurationManager configurationManager, final Map<ExecutorID, ExecutorService> EXECUTORS) {
        EXECUTORS.get(GENERAL).execute(() -> {
            Platforms.set(new LightPlatform(configurationManager));
            SystemPaths paths = Platforms.get().systemPaths();

            BTContext ctx = new BTContext();
            ctx.homeDir = paths.libtorrent();
            ctx.torrentsDir = paths.torrents();
            ctx.dataDir = paths.data();
            ctx.optimizeMemory = true;

            // port range [37000, 57000]
            int port0 = 37000 + new Random().nextInt(20000);
            int port1 = port0 + 10; // 10 retries
            String iface = "0.0.0.0:%1$d,[::]:%1$d";
            ctx.interfaces = String.format(Locale.US, iface, port0);
            ctx.retries = port1 - port0;

            ctx.enableDht = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_ENABLE_DHT);

            BTEngine.ctx = ctx;
            BTEngine.getInstance().start();
        });
    }

    private static void initRoutesAsync(final RuntimeEnvironment runtimeEnvironment,
                                        final ConfigurationManager configurationManager,
                                        ExecutorService executorService) {
        executorService.execute(() -> {
            // All this to be moved to com.frostwire.light.api.RouteManager once we understand Vertx better
            final HashMap<String, String> aboutMap = new HashMap<>();
            aboutMap.put("serverVersion", runtimeEnvironment.frostWireVersion + "b" + runtimeEnvironment.frostWireBuild);
            aboutMap.put("java", Constants.JAVA_VERSION);
            aboutMap.put("jlibtorrent", LibTorrent.jlibtorrentVersion());
            aboutMap.put("boost", LibTorrent.boostVersion());
            aboutMap.put("openssl", LibTorrent.opensslVersion());
            final Buffer aboutBuffer = Buffer.buffer();
            aboutBuffer.appendString(JsonUtils.toJson(aboutMap));

            Vertx VERTX = Vertx.vertx(new VertxOptions().setWorkerPoolSize(4));
            Router router = Router.router(VERTX);

            SockJSHandlerOptions options = new SockJSHandlerOptions().setHeartbeatInterval(2000);
            SockJSHandler sockJSHandler = SockJSHandler.create(VERTX, options);
            BridgeOptions bridgeOptions = new BridgeOptions();
            PermittedOptions permittedOptions = new PermittedOptions();
            permittedOptions.setAddress("search");

            bridgeOptions.addInboundPermitted(permittedOptions);
            bridgeOptions.addOutboundPermitted(permittedOptions);

            sockJSHandler.bridge(bridgeOptions, (BridgeEvent be) -> {
                if (be != null && be.type() == BridgeEventType.SOCKET_CLOSED) {
                    LOG.info("Handler<BridgeEvent> socket closed from " + be.socket().remoteAddress());
                }
                JsonObject rawMessage = be.getRawMessage();
                // SEND: From Client to Server
                if (be != null && rawMessage != null && be.type() == BridgeEventType.SEND) {
                    LOG.info("Handler<BridgeEvent> message:\n" + rawMessage.encodePrettily() + "\n");
                }
                be.complete(true);
            });

            router.route("/bus/*").handler(sockJSHandler);

            StaticHandler staticHandler = StaticHandler.create();
            staticHandler.setWebRoot("build");
            staticHandler.setAlwaysAsyncFS(true);
            router.route("/*").handler(staticHandler::handle);

            HttpServerOptions httpServerOptions = new HttpServerOptions();
            //httpServerOptions.setSsl(true);
            int randomPort = 9191; //new Random(System.currentTimeMillis()).nextInt(1001) + 6969;
            httpServerOptions.setPort(randomPort);
            HttpServer httpServer = VERTX.createHttpServer(httpServerOptions);
            httpServer.requestHandler(router::accept).listen();

            LOG.info("FrostWire Light Server started at http://localhost:" + httpServer.actualPort());
        });
    }

    public static void main(String[] args) {
        SystemUtils.printClasspath();
        new Main();
    }
}
