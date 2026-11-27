/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.filetypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure-logic parts of the file association model; the registry-backed classes are Windows-only. */
final class AssociationTest {

  @Test
  void actionExposesFieldsAndEquality() {
    Action open = new Action("open", "player.exe %1", "Open it");
    assertEquals("open", open.getVerb());
    assertEquals("player.exe %1", open.getCommand());
    assertEquals("Open it", open.getDescription());
    assertEquals(open, new Action("open", "player.exe %1", "Open it"));
    assertNotEquals(open, new Action("open", "player.exe %1"));
    assertNotEquals(open, new Action("play", "player.exe %1", "Open it"));
    assertNotEquals(open, new Action("open", "other.exe", "Open it"));
    assertNotEquals(open, "open");
    assertNull(new Action("open", "x").getDescription());
    String text = open.toString();
    assertTrue(text.contains("Description: Open it"));
    assertTrue(text.contains("Verb: open"));
    assertTrue(text.contains("Command: player.exe %1"));
    assertTrue(new Action(null, null).toString().contains("Verb: \r\n"));
  }

  @Test
  void appUtilityHandlesExtensionsAndUrls() throws Exception {
    assertEquals(".mp3", AppUtility.addDotToFileExtension("mp3"));
    assertEquals(".mp3", AppUtility.addDotToFileExtension(".mp3"));
    assertEquals(".mp3", AppUtility.getFileExtensionByURL(new URI("file:///music/a.mp3").toURL()));
    assertEquals(
        ".gz", AppUtility.getFileExtensionByURL(new URI("http://x.org/dir/a.tar.gz").toURL()));
    assertNull(AppUtility.getFileExtensionByURL(new URI("http://x.org/").toURL()));
    assertNull(AppUtility.getFileExtensionByURL(new URI("http://x.org").toURL()));
    assertNull(AppUtility.getFileExtensionByURL(new URI("http://x.org/dir/noext").toURL()));
    assertNull(AppUtility.getFileExtensionByURL(new URI("http://x.org/dir/trailing.").toURL()));
  }

  @Test
  void associationValidatesArguments() {
    Association a = new Association();
    assertThrows(IllegalArgumentException.class, () -> a.setName(null));
    assertThrows(IllegalArgumentException.class, () -> a.setDescription(null));
    assertThrows(IllegalArgumentException.class, () -> a.setMimeType(null));
    assertThrows(IllegalArgumentException.class, () -> a.addFileExtension(null));
    assertThrows(IllegalArgumentException.class, () -> a.setIconFileName(null));
    assertThrows(IllegalArgumentException.class, () -> a.addAction(null));
    assertThrows(IllegalArgumentException.class, () -> a.addAction(new Action(null, "cmd")));
    assertThrows(IllegalArgumentException.class, () -> a.addAction(new Action("open", null)));
  }

  @Test
  void associationStoresDefensiveCopies() {
    Association a = new Association();
    assertNull(a.getFileExtList());
    assertTrue(a.getActionList().isEmpty());
    a.setName("FrostWire torrent");
    a.setDescription("Torrent file");
    a.setMimeType("application/x-bittorrent");
    a.setIconFileName("fw.ico");
    a.addFileExtension("torrent");
    a.addFileExtension(".magnet");
    a.addAction(new Action("open", "fw.exe %1", "Open"));
    assertEquals("Torrent file", a.getDescription());
    assertEquals("application/x-bittorrent", a.getMimeType());
    assertEquals("fw.ico", a.getIconFileName());
    assertEquals(Arrays.asList(".torrent", ".magnet"), a.getFileExtList());
    a.getFileExtList().clear();
    assertEquals(2, a.getFileExtList().size());
    assertEquals(1, a.getActionList().size());
    a.getActionList().clear();
    assertEquals(1, a.getActionList().size());
    assertEquals("fw.exe %1", a.getActionByVerb("OPEN").getCommand());
    assertNull(a.getActionByVerb("edit"));
    assertNull(new Association().getActionByVerb("open"));
  }

  private static Association full(String desc, String ext, String verb) {
    Association a = new Association();
    a.setDescription(desc);
    a.setMimeType("m/t");
    a.setIconFileName("i.ico");
    a.addFileExtension(ext);
    a.addAction(new Action(verb, "cmd"));
    return a;
  }

  @Test
  void associationEquality() {
    assertEquals(full("d", "x", "open"), full("d", "x", "open"));
    assertNotEquals(full("d", "x", "open"), full("e", "x", "open"));
    assertNotEquals(full("d", "x", "open"), full("d", "y", "open"));
    assertNotEquals(full("d", "x", "open"), full("d", "x", "edit"));
    assertNotEquals(full("d", "x", "open"), "d");
    Association noExt = new Association();
    Association noExt2 = new Association();
    assertEquals(noExt, noExt2);
    noExt2.addFileExtension("a");
    assertNotEquals(noExt, noExt2);
    assertNotEquals(noExt2, noExt);
    // extension order does not matter
    Association p = new Association();
    p.addFileExtension("a");
    p.addFileExtension("b");
    Association q = new Association();
    q.addFileExtension("b");
    q.addFileExtension("a");
    assertEquals(p, q);
  }

  /** In-memory stand-ins for the registry-backed reader and writer. */
  private static final class FakeRegistry implements AppAssociationReader, AppAssociationWriter {
    final List<Association> registered = new ArrayList<>();
    boolean validationFails;

    public String getDescriptionByMimeType(String m) {
      return "desc-" + m;
    }

    public String getDescriptionByFileExt(String e) {
      return "desc-" + e;
    }

    public String getMimeTypeByURL(URL url) {
      return "m/url";
    }

    public List<String> getFileExtListByMimeType(String m) {
      return Arrays.asList("a", ".b");
    }

    public String getMimeTypeByFileExt(String e) {
      return "mime-for" + e;
    }

    public String getIconFileNameByMimeType(String m) {
      return null;
    }

    public String getIconFileNameByFileExt(String e) {
      return "icon" + e;
    }

    public List<Action> getActionListByFileExt(String e) {
      return Collections.singletonList(new Action("open", "run" + e));
    }

    public List<Action> getActionListByMimeType(String m) {
      return null;
    }

    public boolean isMimeTypeExist(String m) {
      return !m.startsWith("unknown");
    }

    public boolean isFileExtExist(String e) {
      return !e.equals(".unknown");
    }

    public void checkAssociationValidForRegistration(Association assoc) {
      if (validationFails) throw new IllegalArgumentException("invalid");
    }

    public void checkAssociationValidForUnregistration(Association assoc) {
      if (validationFails) throw new IllegalArgumentException("invalid");
    }

    public boolean isAssociationExist(Association assoc, int level) {
      return registered.contains(assoc);
    }

    public void registerAssociation(Association assoc, int level) {
      registered.add(assoc);
    }

    public void unregisterAssociation(Association assoc, int level) {
      registered.remove(assoc);
    }
  }

  private static AssociationService serviceOver(FakeRegistry registry) throws Exception {
    AssociationService service = new AssociationService();
    for (String name : new String[] {"appAssocReader", "appAssocWriter"}) {
      Field f = AssociationService.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(service, registry);
    }
    return service;
  }

  @Test
  void serviceBuildsAssociationsFromTheReader() throws Exception {
    AssociationService service = serviceOver(new FakeRegistry());
    Association byMime = service.getMimeTypeAssociation("audio/x");
    assertEquals("audio/x", byMime.getMimeType());
    assertEquals("desc-audio/x", byMime.getDescription());
    assertEquals(Arrays.asList(".a", ".b"), byMime.getFileExtList());
    assertNull(byMime.getIconFileName());
    assertTrue(byMime.getActionList().isEmpty());
    assertNull(service.getMimeTypeAssociation("unknown/x"));
    assertThrows(IllegalArgumentException.class, () -> service.getMimeTypeAssociation(null));

    Association byExt = service.getFileExtensionAssociation("mp3");
    assertEquals(Collections.singletonList(".mp3"), byExt.getFileExtList());
    assertEquals("mime-for.mp3", byExt.getMimeType());
    assertEquals("icon.mp3", byExt.getIconFileName());
    assertEquals("run.mp3", byExt.getActionByVerb("open").getCommand());
    assertNull(service.getFileExtensionAssociation("unknown"));
    assertThrows(IllegalArgumentException.class, () -> service.getFileExtensionAssociation(null));
  }

  @Test
  void serviceRegistersAndUnregisters() throws Exception {
    FakeRegistry registry = new FakeRegistry();
    AssociationService service = serviceOver(registry);
    Association a = full("d", "x", "open");
    assertThrows(
        AssociationNotRegisteredException.class, () -> service.unregisterUserAssociation(a));
    service.registerUserAssociation(a);
    assertEquals(1, registry.registered.size());
    assertThrows(
        AssociationAlreadyRegisteredException.class, () -> service.registerUserAssociation(a));
    service.unregisterUserAssociation(a);
    assertFalse(registry.registered.contains(a));
    assertThrows(IllegalArgumentException.class, () -> service.registerUserAssociation(null));
    assertThrows(IllegalArgumentException.class, () -> service.unregisterUserAssociation(null));
    registry.validationFails = true;
    assertThrows(IllegalArgumentException.class, () -> service.registerUserAssociation(a));
    assertThrows(IllegalArgumentException.class, () -> service.unregisterUserAssociation(a));
  }
}
