package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainActivityShutdownStructureTest {

    @Test
    public void shutdownFinishesWithoutRemovingTask() throws Exception {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/activities/MainActivity.java");
        String shutdownBlock = blockStartingAt(source, "public void shutdown()");
        String finishForShutdownBlock = blockStartingAt(source, "private void finishForShutdown()");
        String finishOverrideBlock = blockStartingAt(source, "public void finish()");

        assertTrue(shutdownBlock.contains("finishForShutdown();"));
        assertFalse(shutdownBlock.contains("finish();"));
        assertTrue(source.contains("import android.os.StrictMode;"));
        assertTrue(finishForShutdownBlock.contains("StrictMode.ThreadPolicy previousPolicy = StrictMode.allowThreadDiskWrites();"));
        assertTrue(finishForShutdownBlock.contains("super.finish();"));
        assertTrue(finishForShutdownBlock.contains("StrictMode.setThreadPolicy(previousPolicy);"));
        assertFalse(finishForShutdownBlock.contains("finishAndRemoveTask"));
        assertTrue(finishOverrideBlock.contains("super.finishAndRemoveTask();"));
    }

    private static String blockStartingAt(String source, String marker) {
        int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf("\n    }\n", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        Path file = root.resolve(relativePath);
        if (!Files.exists(file)) {
            file = root.resolve("android").resolve(relativePath);
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
