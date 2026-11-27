/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

/** What the update.xml values become: normalized types, OS filters, expiry and identity. */
class UpdateMessageTest {

  @Test
  void anUnknownOrMissingTypeIsAnUpdate() {
    assertEquals("update", new UpdateMessage(null, "m").getMessageType());
    assertEquals("update", new UpdateMessage("", "m").getMessageType());
    assertEquals("update", new UpdateMessage("bogus", "m").getMessageType());
  }

  @Test
  void knownTypesAreKeptLowerCasedAndTrimmed() {
    assertEquals("announcement", new UpdateMessage("  Announcement ", "m").getMessageType());
    assertEquals("overlay", new UpdateMessage("OVERLAY", "m").getMessageType());
    assertEquals("chat_server", new UpdateMessage("chat_server", "m").getMessageType());
    assertEquals("uxstats", new UpdateMessage("uxstats", "m").getMessageType());
    assertEquals("update", new UpdateMessage("update", "m").getMessageType());
  }

  @Test
  void osIsNormalizedAndWildcardMeansEveryOs() {
    UpdateMessage m = new UpdateMessage("update", "m");
    m.setOs(" Windows ");
    assertEquals("windows", m.getOs());
    m.setOs("LINUX");
    assertEquals("linux", m.getOs());
    m.setOs("mac.arm64");
    assertEquals("mac.arm64", m.getOs());
    m.setOs("*");
    assertNull(m.getOs());
    m.setOs("beos");
    assertNull(m.getOs(), "an OS we do not know addresses nobody in particular");
    m.setOs(null);
    assertNull(m.getOs());
  }

  @Test
  void blankVersionAndBuildReadAsNull() {
    UpdateMessage m = new UpdateMessage("update", "m");
    assertNull(m.getVersion());
    assertNull(m.getBuild());
    m.setVersion("");
    m.setBuild("");
    assertNull(m.getVersion());
    assertNull(m.getBuild());
    m.setVersion("7.1.6");
    m.setBuild("339");
    assertEquals("7.1.6", m.getVersion());
    assertEquals("339", m.getBuild());
  }

  @Test
  void md5IsTrimmedAndUpperCased() {
    UpdateMessage m = new UpdateMessage("update", "m");
    assertEquals("", m.getRemoteMD5());
    m.setRemoteMD5("  abcDEF0123  ");
    assertEquals("ABCDEF0123", m.getRemoteMD5());
  }

  @Test
  void showOnceDefaultsToFalseAndParsesTrueInAnyCase() {
    UpdateMessage m = new UpdateMessage("announcement", "m");
    assertFalse(m.isShownOnce());
    m.setShowOnce("TRUE");
    assertTrue(m.isShownOnce());
    m.setShowOnce(null);
    assertTrue(m.isShownOnce(), "null leaves the value alone");
    m.setShowOnce("no");
    assertFalse(m.isShownOnce());
  }

  @Test
  void noExpirationNeverExpiresAndBadTimestampsAreIgnored() {
    UpdateMessage m = new UpdateMessage("announcement", "m");
    assertFalse(m.hasExpired());
    m.setExpiration(null);
    assertFalse(m.hasExpired());
    m.setExpiration("0");
    assertFalse(m.hasExpired());
    m.setExpiration("tomorrow");
    assertFalse(m.hasExpired(), "an expiration we cannot read means it does not expire");
  }

  @Test
  void expirationIsComparedWithTheServerTime() {
    UpdateManager.getInstance().setServerTime("2000000");
    UpdateMessage m = new UpdateMessage("announcement", "m");
    m.setExpiration("1999999");
    assertTrue(m.hasExpired());
    m.setExpiration("2000001");
    assertFalse(m.hasExpired());
  }

  @Test
  void messagesWithTheSameContentAreEqual() {
    UpdateMessage a = new UpdateMessage("announcement", "hello");
    a.setUrl("https://example.com");
    UpdateMessage b = new UpdateMessage("announcement", "hello");
    b.setUrl("https://example.com");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    b.setIntro(true);
    assertNotEquals(a, b, "an intro overlay is not the same message");
    assertNotEquals(a, "hello");
    assertNotEquals(a, new UpdateMessage("announcement", "bye"));
  }

  @Test
  void aMessageSurvivesSerializationBecauseSeenMessagesAreKeptOnDisk() throws Exception {
    UpdateMessage m = new UpdateMessage("announcement", "hello");
    m.setUrl("https://example.com");
    m.setTorrent("http://example.com/a.torrent");
    m.setShowOnce("true");
    m.setOs("linux");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(m);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      UpdateMessage copy = (UpdateMessage) in.readObject();
      assertEquals(m, copy);
      assertEquals("linux", copy.getOs());
      assertTrue(copy.isShownOnce());
    }
  }

  @Test
  void toStringShowsTheFieldsAThrowawayLogLineNeeds() {
    UpdateMessage m = new UpdateMessage("update", "hello");
    m.setVersion("9.9.9");
    m.setSaveAs("FrostWire.dmg");
    String text = m.toString();
    assertTrue(text.contains("_message : hello"), text);
    assertTrue(text.contains("_version : 9.9.9"), text);
    assertTrue(text.contains("_saveAs : FrostWire.dmg"), text);
  }
}
