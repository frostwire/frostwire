/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogs;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

/**
 * Regression test: dialog fragments must NOT use BundleCompat.getSerializable(),
 * which internally calls Bundle.getSerializable(String, Class) — an API 33+
 * method that crashes with NoSuchMethodError on Android 12 and lower.
 *
 * Pure JVM test — no Android framework required.
 */
public class DialogBundleCompatRegressionTest {

    private static final String FORBIDDEN_PATTERN = "BundleCompat.getSerializable";
    private static final String DIALOGS_PACKAGE_DIR = "src/main/java/com/frostwire/android/gui/dialogs";

    @Test
    public void noDialogFilesUseBundleCompatGetSerializable() throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path dialogsDir = projectRoot.resolve(DIALOGS_PACKAGE_DIR);

        if (!Files.exists(dialogsDir)) {
            // Fall back to resolving from the test class location for IDE runs
            String classFile = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
            Path buildDir = Paths.get(classFile).normalize();
            // Walk up to project root (android/)
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            dialogsDir = projectRoot.resolve(DIALOGS_PACKAGE_DIR);
        }

        try (Stream<Path> paths = Files.walk(dialogsDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> {
                     try {
                         String source = new String(Files.readAllBytes(p));
                         if (source.contains(FORBIDDEN_PATTERN)) {
                             fail(p.getFileName() + " must NOT use " + FORBIDDEN_PATTERN
                                 + " — it crashes on API < 33. Use Bundle.getSerializable(String) with cast instead.");
                         }
                     } catch (IOException e) {
                         fail("Could not read " + p + ": " + e.getMessage());
                     }
                 });
        }
    }
}
