/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.filetypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Association and Action used to hash every instance to 0. */
class AssociationHashCodeTest {
  private static Association association(String name, String ext, String verb, String command) {
    Association a = new Association();
    a.setName(name);
    a.setDescription("Torrent file");
    a.setMimeType("application/x-bittorrent");
    a.setIconFileName("icon.ico");
    a.addFileExtension(ext);
    a.addAction(new Action(verb, command, "Open it"));
    return a;
  }

  @Test
  void actionHashCodeIsNotConstantZero() {
    assertNotEquals(0, new Action("open", "frostwire %1", "Open").hashCode());
    assertNotEquals(
        new Action("open", "frostwire %1", "Open").hashCode(),
        new Action("play", "frostwire --play %1", "Play").hashCode());
  }

  @Test
  void equalActionsHashAlike() {
    Action a = new Action("open", "frostwire %1", "Open");
    Action b = new Action("open", "frostwire %1", "Open");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a.hashCode(), a.hashCode());
  }

  @Test
  void associationHashCodeIsNotConstantZero() {
    int one = association("bt", "torrent", "open", "frostwire %1").hashCode();
    int two = association("bt", "magnet", "open", "frostwire %1").hashCode();
    assertNotEquals(0, one);
    assertNotEquals(one, two);
  }

  @Test
  void equalAssociationsHashAlike() {
    // equals() does not look at the name, so neither may hashCode()
    Association a = association("first", "torrent", "open", "frostwire %1");
    Association b = association("second", "torrent", "open", "frostwire %1");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equalsAndHashCodeIgnoreTheOrderOfExtensionsAndActions() {
    Association a = association("a", "torrent", "open", "frostwire %1");
    a.addFileExtension("magnet");
    a.addAction(new Action("play", "frostwire --play %1", "Play"));
    Association b = new Association();
    b.setDescription("Torrent file");
    b.setMimeType("application/x-bittorrent");
    b.setIconFileName("icon.ico");
    b.addFileExtension("magnet");
    b.addFileExtension("torrent");
    b.addAction(new Action("play", "frostwire --play %1", "Play"));
    b.addAction(new Action("open", "frostwire %1", "Open it"));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void aMutatedAssociationStillFindsItselfInAHashSet() {
    Association a = association("a", "torrent", "open", "frostwire %1");
    a.hashCode();
    a.addFileExtension("magnet");
    Set<Association> set = new HashSet<>();
    set.add(a);
    assertTrue(set.contains(a));
    Association same = association("a", "torrent", "open", "frostwire %1");
    same.addFileExtension("magnet");
    assertTrue(set.contains(same));
  }
}
