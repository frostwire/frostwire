/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real thing, in a child JVM: the JDK's own {@code HBShaper} is recompiled with a static
 * initializer that fails the way the one on some Windows machines does (frostwire/frostwire#1587; a
 * failed FFM symbol lookup), patched into {@code java.desktop} with {@code --patch-module}. The
 * tests show the three facts the fix rests on:
 *
 * <ol>
 *   <li>without the guard, text that needs shaping throws {@code NoClassDefFoundError: Could not
 *       initialize class sun.font.HBShaper} (the crash in the reports);
 *   <li>with the guard first, the same text lays out through the JNI shaper;
 *   <li>the guard is useless after {@code SunLayoutEngine} has initialized, which is why it is the
 *       first call in {@code main}.
 * </ol>
 *
 * Skipped on a JDK without {@code lib/src.zip}, without a compiler, or whose HBShaper static block
 * does not look as expected.
 */
class TextShapingGuardEndToEndTest {
  private static final Pattern STATIC_BLOCK = Pattern.compile("static\\s*\\{(\\s*)MH_LOOKUP\\s*=");

  private static Path patched;

  @BeforeAll
  static void patchHbShaper(@TempDir Path directory) throws Exception {
    // @TempDir on a static method parameter is per-class and lives until the class is done.
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assumeTrue(compiler != null, "the test JVM has no compiler");
    Path sources = Path.of(System.getProperty("java.home"), "lib", "src.zip");
    assumeTrue(Files.isRegularFile(sources), "the JDK has no lib/src.zip");
    String original;
    try (ZipFile zip = new ZipFile(sources.toFile())) {
      ZipEntry entry = zip.getEntry("java.desktop/sun/font/HBShaper.java");
      assumeTrue(entry != null, "src.zip has no java.desktop/sun/font/HBShaper.java");
      original = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }
    var matcher = STATIC_BLOCK.matcher(original);
    assumeTrue(matcher.find(), "this JDK's HBShaper static block is not the one this test patches");
    String failing =
        matcher.replaceFirst(
            "static {$1if (System.nanoTime() != 0) throw new java.util.NoSuchElementException("
                + "\"Symbol not found: malloc\");$1MH_LOOKUP =");
    Path source = directory.resolve("src/sun/font/HBShaper.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, failing, StandardCharsets.UTF_8);
    Path out = directory.resolve("out");
    Files.createDirectories(out);
    int status =
        compiler.run(
            null,
            null,
            null,
            "--patch-module",
            "java.desktop=" + directory.resolve("src"),
            "-d",
            out.toString(),
            source.toString());
    assumeTrue(status == 0, "the patched HBShaper did not compile on this JDK");
    // Keep the compiled classes beyond the TempDir's own lifetime by copying them next to it.
    patched = Files.createTempDirectory("hbshaper-patched");
    try (var files = Files.walk(out)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        Path target = patched.resolve(out.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.copy(file, target);
        }
      }
    }
  }

  private static String run(String mode) throws IOException, InterruptedException {
    String classes =
        List.of(
                location(TextShapingGuard.class),
                location(TextShapingGuardChild.class),
                location(Logger.class))
            .stream()
            .distinct()
            .reduce((a, b) -> a + java.io.File.pathSeparator + b)
            .orElseThrow();
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--patch-module",
                "java.desktop=" + patched,
                "-Djava.awt.headless=true",
                "-cp",
                classes,
                TextShapingGuardChild.class.getName(),
                mode)
            .redirectErrorStream(true)
            .start();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    process.getInputStream().transferTo(output);
    assertTrue(process.waitFor(90, TimeUnit.SECONDS), "the child JVM did not finish");
    assertEquals(0, process.exitValue(), output.toString(StandardCharsets.UTF_8));
    return output.toString(StandardCharsets.UTF_8);
  }

  private static String location(Class<?> type) {
    try {
      return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void withoutTheGuardTextThatNeedsShapingThrowsTheReportedError() throws Exception {
    String output = run("noguard");
    assertTrue(
        output.contains("LAYOUT_FAILED hebrew java.lang.ExceptionInInitializerError"),
        "the first use throws the initializer's error:\n" + output);
    assertTrue(
        output.contains(
            "LAYOUT_FAILED hebrew-again java.lang.NoClassDefFoundError: Could not initialize class"
                + " sun.font.HBShaper"),
        "every later use throws the error in the reports:\n" + output);
  }

  @Test
  void withTheGuardFirstTheSameTextLaysOutThroughTheJniShaper() throws Exception {
    String output = run("guard");
    assertTrue(output.contains("GUARD false"), "the guard saw the failure:\n" + output);
    assertTrue(output.contains("LAYOUT_OK hebrew\n"), output);
    assertTrue(output.contains("LAYOUT_OK hebrew-again"), output);
    assertFalse(output.contains("LAYOUT_FAILED"), output);
  }

  @Test
  void theGuardIsTooLateOnceTheLayoutEngineHasInitialized() throws Exception {
    String output = run("late");
    assertTrue(output.contains("LAYOUT_FAILED latin-first"), output);
    assertTrue(
        output.contains("LAYOUT_FAILED hebrew java.lang.NoClassDefFoundError"),
        "SunLayoutEngine already read the property, so it must come first in main:\n" + output);
  }
}
