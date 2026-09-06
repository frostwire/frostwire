/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IceBridgeTokensTest {
  @TempDir Path directory;

  @Test
  void boundedFileReadsFailClosed() throws Exception {
    Path file = directory.resolve("tokens.txt");
    Files.writeString(file, "x".repeat(65537));
    assertTrue(new IceBridgeTokens(file.toFile()).isEmpty());
    Files.writeString(file, "x".repeat(257));
    assertTrue(new IceBridgeTokens(file.toFile()).isEmpty());
    StringBuilder entries = new StringBuilder();
    for (int i = 0; i < 129; i++) entries.append("fixture-").append(i).append('\n');
    Files.writeString(file, entries);
    assertTrue(new IceBridgeTokens(file.toFile()).isEmpty());
  }

  @Test
  void reloadAndDeletionRevokeFileTokensButPreserveRuntimeTokens() throws Exception {
    Path path = directory.resolve("tokens.txt");
    Files.writeString(path, "# fixture\nfirst-token\n");
    File file = path.toFile();
    IceBridgeTokens tokens = new IceBridgeTokens(file);
    tokens.addRuntimeToken("runtime-token");
    assertTrue(tokens.isValid("first-token"));
    long modified = file.lastModified();
    Files.writeString(path, "second-token\n");
    assertTrue(file.setLastModified(modified + 2000));
    assertFalse(tokens.isValid("first-token"));
    assertTrue(tokens.isValid("second-token"));
    assertTrue(tokens.isValid("runtime-token"));
    Files.delete(path);
    assertFalse(tokens.isValid("second-token"));
    assertTrue(tokens.isValid("runtime-token"));
  }

  @Test
  void generationRestrictsExistingFileBeforeAppending() throws Exception {
    assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path file = directory.resolve("tokens.txt");
    Files.writeString(file, "existing-fixture");
    Files.setPosixFilePermissions(
        file,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ));
    IceBridgeTokens tokens = new IceBridgeTokens(file.toFile());
    String generated = tokens.generateAndAdd();
    assertTrue(tokens.isValid(generated));
    assertTrue(tokens.isValid("existing-fixture"));
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(file));
  }

  @Test
  void generationRejectsSymlinkAndMissingDestination() throws Exception {
    assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path target = directory.resolve("target.txt");
    Files.writeString(target, "unchanged-fixture\n");
    Path link = directory.resolve("link.txt");
    Files.createSymbolicLink(link, target);
    assertThrows(RuntimeException.class, () -> new IceBridgeTokens(link.toFile()).generateAndAdd());
    assertEquals("unchanged-fixture\n", Files.readString(target));
    assertThrows(IllegalStateException.class, () -> new IceBridgeTokens(null).generateAndAdd());
  }
}
