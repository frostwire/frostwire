/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Replacing the identity (new identity, seed phrase, import) keeps the previous one in {@code
 * identity.dat.bak}. A download whose distributed source is that previous identity must not be
 * endorsed automatically either: it would be endorsing ourselves (frostwire/frostwire#1414).
 */
class IdentityLifecycleOwnKeysTest {

  @TempDir Path dir;

  @Test
  void ownKeysIncludeTheIdentityThatWasReplaced() throws Exception {
    File identityFile = dir.resolve(RelayConstants.IDENTITY_FILE).toFile();
    IdentityKeys first = IdentityLifecycle.generateAndInstall(identityFile, 4);
    IdentityKeys second = IdentityLifecycle.generateAndInstall(identityFile, 4);

    List<byte[]> keys = IdentityLifecycle.ownEd25519Keys(second, identityFile);

    assertEquals(2, keys.size());
    assertArrayEquals(second.ed25519PubRaw(), keys.get(0), "current identity first");
    assertArrayEquals(first.ed25519PubRaw(), keys.get(1));
  }

  @Test
  void aFirstIdentityHasOnlyItsOwnKey() throws Exception {
    File identityFile = dir.resolve(RelayConstants.IDENTITY_FILE).toFile();
    IdentityKeys only = IdentityLifecycle.generateAndInstall(identityFile, 4);

    List<byte[]> keys = IdentityLifecycle.ownEd25519Keys(only, identityFile);

    assertEquals(1, keys.size());
    assertArrayEquals(only.ed25519PubRaw(), keys.get(0));
  }

  @Test
  void anUnreadableBackupIsIgnored() throws Exception {
    File identityFile = dir.resolve(RelayConstants.IDENTITY_FILE).toFile();
    IdentityKeys only = IdentityLifecycle.generateAndInstall(identityFile, 4);
    Files.write(dir.resolve(RelayConstants.IDENTITY_FILE + ".bak"), new byte[] {1, 2, 3});

    List<byte[]> keys = IdentityLifecycle.ownEd25519Keys(only, identityFile);

    assertEquals(1, keys.size());
  }

  @Test
  void aBackupOfTheSameIdentityIsNotListedTwice() throws Exception {
    File identityFile = dir.resolve(RelayConstants.IDENTITY_FILE).toFile();
    IdentityKeys keys = IdentityLifecycle.generateAndInstall(identityFile, 4);
    IdentityLifecycle.install(keys, identityFile, true);

    assertEquals(1, IdentityLifecycle.ownEd25519Keys(keys, identityFile).size());
  }
}
