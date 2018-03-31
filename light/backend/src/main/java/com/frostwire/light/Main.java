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
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static com.frostwire.light.Main.ExecutorID.GENERAL;

// IntelliJ Run configuration:
// Main Class: com.frostwire.light.Main
// VM Options: -Djava.library.path=lib
// Use classpath of module: backend_main

public final class Main {
    private static Logger LOG = Logger.getLogger(Main.class);

    enum ExecutorID {
        GENERAL,
        CLOUD_SEARCH,
        P2P_SEARCH
    }

    private Main() {
        ConfigurationManager.create();
        ConfigurationManager configurationManager = ConfigurationManager.instance();
        final Map<ExecutorID, ExecutorService> EXECUTORS = initExecutorServices(configurationManager);
        setupBTEngineAsync(configurationManager, EXECUTORS);
        RuntimeEnvironment runtimeEnvironment = RuntimeEnvironment.init();
        initRoutesAsync(runtimeEnvironment, configurationManager, EXECUTORS);
    }

    private Map<ExecutorID, ExecutorService> initExecutorServices(final ConfigurationManager configurationManager) {
        final Map<ExecutorID, ExecutorService> executorsMap = new HashMap<>();
        executorsMap.put(GENERAL, ThreadPool.newThreadPool(GENERAL.name(), 8, false));
        return executorsMap;
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
                                        final Map<ExecutorID, ExecutorService> EXECUTORS) {
        EXECUTORS.get(GENERAL).execute(() -> {
            // All this to be moved to com.frostwire.light.api.RouteManager once we understand Vertx better
            final HashMap<String, String> aboutMap = new HashMap<>();
            aboutMap.put("serverVersion", Constants.FROSTWIRE_VERSION_STRING + "b" + Constants.FROSTWIRE_BUILD);
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
            sockJSHandler.socketHandler(sockJSSocket -> sockJSSocket.write(sockJSSocket.uri()));

            router.route("/api/*").handler(sockJSHandler);

            StaticHandler staticHandler = StaticHandler.create();
            staticHandler.setAlwaysAsyncFS(true);
            router.route("/*").handler(staticHandler::handle);

            HttpServerOptions httpServerOptions = new HttpServerOptions();
            //httpServerOptions.setSsl(true);
            int randomPort = 9191; //new Random(System.currentTimeMillis()).nextInt(1001) + 6969;
            httpServerOptions.setPort(randomPort);
            HttpServer httpServer = VERTX.createHttpServer(httpServerOptions);

            httpServer.requestHandler(router::accept).listen();

            SystemUtils.printClasspath();

            LOG.info("FrostWire Light Server started at http://localhost:" + httpServer.actualPort());
        });
    }

    public static void main(String[] args) {
        new Main();
    }
}
