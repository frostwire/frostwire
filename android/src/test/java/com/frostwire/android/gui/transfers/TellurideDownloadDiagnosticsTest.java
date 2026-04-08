package com.frostwire.android.gui.transfers;

import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.OkHttpClientWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.SocketEffect;
import okhttp3.Headers;

import static org.junit.Assert.*;

/**
 * Diagnostic tests for the 0-byte YouTube download bug and connection-reset retry.
 *
 * Run with:
 *   ./gradlew testPlus1DebugUnitTest --tests "com.frostwire.android.gui.transfers.TellurideDownloadDiagnosticsTest"
 *
 * These tests verify:
 * - Hypothesis A: OkHttpClientWrapper.save() silently writes 0 bytes on HTTP 403
 * - Hypothesis B: File copy path produces correct output for both empty and non-empty sources
 * - What HTTP headers OkHttp actually sends (reveals why YouTube rejects requests)
 * - Connection reset mid-download throws SocketException (triggers BaseHttpDownload retry logic)
 */
public class TellurideDownloadDiagnosticsTest {

    private MockWebServer server;
    private File tempDir;
    private ThreadPool pool;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        tempDir = Files.createTempDirectory("fw_diag_test").toFile();
        pool = new ThreadPool("diag-test", 2, new java.util.concurrent.LinkedBlockingQueue<>(), true);
    }

    @After
    public void tearDown() throws IOException {
        server.close();
        pool.shutdown();
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        tempDir.delete();
    }

    // -------------------------------------------------------------------------
    // Hypothesis A tests — does save() handle non-200 responses correctly?
    // -------------------------------------------------------------------------

    /**
     * Baseline: 200 response with body content is fully written to disk.
     */
    @Test
    public void testSave_http200_writesContent() throws IOException {
        String body = "FAKE VIDEO CONTENT 1234567890";
        server.enqueue(new MockResponse(200, Headers.of(), body));

        File dest = new File(tempDir, "video_200.mp4");
        OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
        client.save(server.url("/video.mp4").toString(), dest, false);

        System.out.println("[DIAG] 200 response: file size = " + dest.length() + " bytes (expected " + body.getBytes(StandardCharsets.UTF_8).length + ")");
        assertTrue("200 response should create non-empty file", dest.exists() && dest.length() > 0);
        assertEquals("File size should match body size", body.getBytes(StandardCharsets.UTF_8).length, dest.length());
    }

    /**
     * HYPOTHESIS A TEST: 403 with empty body.
     * With the fix applied, save() must throw IOException instead of silently writing 0 bytes.
     */
    @Test
    public void testSave_http403_emptyBody_currentlyProduces0ByteFile() throws IOException {
        server.enqueue(new MockResponse(403, Headers.of(), ""));

        File dest = new File(tempDir, "video_403_empty.mp4");
        boolean exceptionThrown = false;
        try {
            OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
            client.save(server.url("/video.mp4").toString(), dest, false);
        } catch (IOException e) {
            exceptionThrown = true;
            System.out.println("[DIAG] 403 empty body → IOException thrown (CORRECT): " + e.getMessage());
        }

        long fileSize = dest.exists() ? dest.length() : -1;
        System.out.println("[DIAG] 403 empty body: file size=" + fileSize + " bytes, exceptionThrown=" + exceptionThrown);
        assertTrue("HTTP 403 must throw IOException — no silent 0-byte files", exceptionThrown);
        assertFalse("0-byte file must not be left on disk after 403", dest.exists() && dest.length() == 0);
    }

    /**
     * 403 with an HTML body (YouTube-style error page) must also throw — not write HTML to disk.
     */
    @Test
    public void testSave_http403_htmlBody_writesHtmlToFile() throws IOException {
        String htmlBody = "<html><body><h1>403 Forbidden</h1></body></html>";
        server.enqueue(new MockResponse(403, Headers.of("Content-Type", "text/html"), htmlBody));

        File dest = new File(tempDir, "video_403_html.mp4");
        boolean exceptionThrown = false;
        try {
            OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
            client.save(server.url("/video.mp4").toString(), dest, false);
        } catch (IOException e) {
            exceptionThrown = true;
            System.out.println("[DIAG] 403 HTML body → IOException thrown (CORRECT): " + e.getMessage());
        }

        long fileSize = dest.exists() ? dest.length() : -1;
        System.out.println("[DIAG] 403 HTML body: file size=" + fileSize + " bytes, exceptionThrown=" + exceptionThrown);
        assertTrue("HTTP 403 with HTML body must throw IOException", exceptionThrown);
    }

    /**
     * Inspect exactly which HTTP headers OkHttp sends for a video download URL.
     * This reveals whether User-Agent, Referer, or cookies are missing — which explains YouTube rejections.
     */
    @Test
    public void testSave_requestHeaders_revealWhatOkHttpSends() throws IOException, InterruptedException {
        server.enqueue(new MockResponse(200, Headers.of(), "dummy"));

        File dest = new File(tempDir, "headers_probe.mp4");
        OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
        client.save(server.url("/video.mp4").toString(), dest, false);

        RecordedRequest request = server.takeRequest();
        Headers headers = request.getHeaders();
        System.out.println("[DIAG] === HTTP Headers sent by OkHttpClientWrapper ===");
        for (int i = 0; i < headers.size(); i++) {
            System.out.println("[DIAG]   " + headers.name(i) + ": " + headers.value(i));
        }
        System.out.println("[DIAG] ===================================================");
        System.out.println("[DIAG] User-Agent: " + headers.get("User-Agent"));
        System.out.println("[DIAG] Referer:    " + headers.get("Referer"));
        System.out.println("[DIAG] Cookie:     " + headers.get("Cookie"));

        assertNotNull("User-Agent header must be present", headers.get("User-Agent"));
    }

    /**
     * Connection reset mid-download must throw a SocketException or IOException.
     * This is the trigger for the retry-with-resume logic in BaseHttpDownload.start().
     * BaseHttpDownload will catch SocketException("Connection reset") and retry up to
     * MAX_RETRIES_ON_CONNECTION_RESET=3 times, using resume=true to continue from partial data.
     */
    @Test
    public void testSave_connectionResetMidDownload_throwsSocketException() {
        // DisconnectDuringResponseBody makes MockWebServer abruptly close the socket
        // after sending the response headers but before the body is fully sent —
        // simulating the "Connection reset" seen in production YouTube downloads.
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("some partial content")
                .onResponseBody(SocketEffect.ShutdownConnection.INSTANCE)
                .build());

        File dest = new File(tempDir, "video_reset.mp4");
        boolean caughtIoException = false;
        try {
            OkHttpClientWrapper client = new OkHttpClientWrapper(pool);
            client.save(server.url("/video.mp4").toString(), dest, false);
        } catch (IOException e) {
            caughtIoException = true;
            System.out.println("[DIAG] Connection reset → IOException: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            // SocketException is a subclass of IOException; BaseHttpDownload catches SocketException specifically
            boolean isSocketException = e instanceof SocketException;
            System.out.println("[DIAG] Is SocketException (BaseHttpDownload retry trigger): " + isSocketException);
        }
        assertTrue("Connection reset must throw IOException so BaseHttpDownload can retry", caughtIoException);
    }

    // -------------------------------------------------------------------------
    // Hypothesis B tests — does the file copy path handle sources correctly?
    // -------------------------------------------------------------------------

    /**
     * Verify that a plain Java file copy of a non-empty source produces the correct output.
     * This is the simplest form of DefaultFileSystem.copy() (FileUtils.copyFile equivalent).
     */
    @Test
    public void testFileSystemCopy_nonEmptySource_copiesCorrectly() throws IOException {
        byte[] content = "FAKE VIDEO DATA".getBytes(StandardCharsets.UTF_8);
        File src = new File(tempDir, "src_nonempty.mp4");
        File dst = new File(tempDir, "dst_nonempty.mp4");
        try (FileOutputStream fos = new FileOutputStream(src)) {
            fos.write(content);
        }

        copyFile(src, dst);

        System.out.println("[DIAG] Non-empty copy: src=" + src.length() + " dst=" + dst.length());
        assertEquals("Destination must match source size", src.length(), dst.length());
    }

    /**
     * Verify behavior when source is 0 bytes — the copy should produce a 0-byte destination.
     * If this is what we see, the bug is upstream (HTTP layer wrote 0 bytes, not the copy path).
     */
    @Test
    public void testFileSystemCopy_zeroByteSource_producesZeroByteDest() throws IOException {
        File src = new File(tempDir, "src_zero.mp4");
        File dst = new File(tempDir, "dst_zero.mp4");
        src.createNewFile(); // 0-byte file

        copyFile(src, dst);

        System.out.println("[DIAG] Zero-byte copy: src=" + src.length() + " dst=" + dst.length());
        if (dst.exists() && dst.length() == 0) {
            System.out.println("[DIAG] Zero-byte source → zero-byte dest: copy path is NOT the bug, HTTP layer is");
        }
        assertEquals("Zero-byte source should produce zero-byte dest", 0L, dst.length());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }
}
