/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.util.http;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.ThreadPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OkHttpClientWrapperTest {

    @Test
    public void pingIntervalDetectsStaleHttp2Connections() {
        ThreadPool pool = new ThreadPool("okhttp-test", 1, new LinkedBlockingQueue<>(), true);
        OkHttpClient client = OkHttpClientWrapper.newOkHttpClient(pool).build();
        assertEquals(5000, client.pingIntervalMillis());
        pool.shutdownNow();
    }

    @Test
    public void miscClientFetchesSlideshowJson() {
        try {
            String json =
                    HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC)
                            .get(
                                    "https://update.frostwire.com/o2.php?from=desktop&version=7.0.4&build=331");
            assertNotNull(json);
            assertTrue(json.contains("\"slides\""));
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "update.frostwire.com unreachable: " + e.getMessage());
        }
    }

    @Test
    public void save_resumeWithMismatchedContentRangeDoesNotAppend(@TempDir File tempDir) throws Exception {
        File destination = new File(tempDir, "download.bin");
        try (FileOutputStream out = new FileOutputStream(destination)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        try (TestServer server = new TestServer(
                "HTTP/1.1 206 Partial Content\r\n"
                        + "Content-Range: bytes 2-5/6\r\n"
                        + "Content-Length: 4\r\n"
                        + "Connection: close\r\n\r\n"
                        + "cdef")) {
            ThreadPool pool = new ThreadPool("okhttp-test", 1, new LinkedBlockingQueue<>(), true);
            OkHttpClientWrapper client = new OkHttpClientWrapper(pool);

            assertThrows(
                    HttpClient.HttpRangeOutOfBoundsException.class,
                    () -> client.save(server.url(), destination, true));
            assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), java.nio.file.Files.readAllBytes(destination.toPath()));
            pool.shutdownNow();
        }
    }

    @Test
    public void save_doesNotEraseCancellationIssuedBeforeRetry(@TempDir File tempDir) throws Exception {
        File destination = new File(tempDir, "download.bin");
        AtomicBoolean cancelNotified = new AtomicBoolean();
        try (TestServer server = new TestServer(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Length: 4\r\n"
                        + "Connection: close\r\n\r\n"
                        + "data")) {
            ThreadPool pool = new ThreadPool("okhttp-test", 1, new LinkedBlockingQueue<>(), true);
            OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
            client.setListener(new HttpClient.HttpClientListenerAdapter() {
                @Override
                public void onCancel(HttpClient ignored) {
                    cancelNotified.set(true);
                }
            });
            client.cancel();

            client.save(server.url(), destination, false);

            assertEquals(0, destination.length());
            assertTrue(cancelNotified.get());
            pool.shutdownNow();
        }
    }

    @Test
    public void cancellationFlag_isVolatile() throws Exception {
        assertTrue(Modifier.isVolatile(AbstractHttpClient.class.getDeclaredField("canceled").getModifiers()));
    }

    @Test
    public void resetCancellation_allowsExplicitResume() {
        ThreadPool pool = new ThreadPool("okhttp-test", 1, new LinkedBlockingQueue<>(), true);
        OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
        client.cancel();

        client.resetCancellation();

        assertTrue(!client.isCanceled());
        pool.shutdownNow();
    }

    private static final class TestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;

        private TestServer(String response) throws IOException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> serve(response), "OkHttpClientWrapperTest-server");
            thread.start();
        }

        private String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/file";
        }

        private void serve(String response) {
            try (Socket socket = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    OutputStream out = socket.getOutputStream()) {
                while (!reader.readLine().isEmpty()) {
                    // Consume request headers before sending the response.
                }
                out.write(response.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(5000);
        }
    }
}
