/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * minSdk is 26, so every device draws the launcher icon as an adaptive icon. Without one, launchers
 * shrink the legacy 192px bitmap into their own shape and the icon looks small and blurry (#1209).
 */
public final class LauncherIconStructureTest {

    private static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final double[] SCALES = {1, 1.5, 2, 3, 4};

    private static File androidDir() {
        File dir = new File(".");
        if (!new File(dir, "AndroidManifest.xml").isFile()) {
            dir = new File("android");
        }
        assertTrue("android/ not found from " + new File(".").getAbsolutePath(),
                new File(dir, "AndroidManifest.xml").isFile());
        return dir;
    }

    @Test
    public void launcherIconIsAdaptive() throws IOException {
        File xml = new File(androidDir(), "res/mipmap-anydpi-v26/ic_launcher.xml");
        assertTrue(xml.getPath() + " missing", xml.isFile());
        String content = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("<adaptive-icon"));
        assertTrue(content.contains("@mipmap/ic_launcher_foreground"));
        assertTrue(content.contains("@color/ic_launcher_background"));
    }

    /** Adaptive icon layers are 108dp square. */
    @Test
    public void foregroundLayerExistsAtEveryDensityWithTheLayerSize() throws IOException {
        for (int i = 0; i < DENSITIES.length; i++) {
            File webp = new File(androidDir(), "res/mipmap-" + DENSITIES[i] + "/ic_launcher_foreground.webp");
            assertTrue(webp.getPath() + " missing", webp.isFile());
            int expected = (int) Math.round(108 * SCALES[i]);
            assertEquals(webp.getPath(), expected, webpWidth(webp));
        }
    }

    /** Reads the canvas width from a VP8L (lossless) or VP8X WebP header. */
    private static int webpWidth(File webp) throws IOException {
        byte[] b = Files.readAllBytes(webp.toPath());
        String chunk = new String(b, 12, 4, StandardCharsets.US_ASCII);
        if (chunk.equals("VP8L")) {
            return 1 + ((b[21] & 0xFF) | ((b[22] & 0x3F) << 8));
        }
        if (chunk.equals("VP8X")) {
            return 1 + ((b[24] & 0xFF) | ((b[25] & 0xFF) << 8) | ((b[26] & 0xFF) << 16));
        }
        throw new AssertionError(webp + ": unexpected WebP chunk " + chunk);
    }
}
