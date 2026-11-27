/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.setting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings machinery every Options pane stands on: typed values over a properties file, what is
 * written to disk (only what differs from the default), and what happens to values that cannot be
 * read back.
 */
class SettingsFactoryTest {
  @TempDir Path tmp;

  private File file;
  private SettingsFactory factory;

  @BeforeEach
  void open() {
    file = tmp.resolve("test.props").toFile();
    factory = new SettingsFactory(file, "Test settings");
  }

  private SettingsFactory reopen() {
    return new SettingsFactory(file, "Test settings");
  }

  @Test
  void aNewSettingStartsAtItsDefaultAndIsNotSaved() throws Exception {
    IntSetting size = factory.createIntSetting("SIZE", 7);
    assertEquals(7, size.getValue());
    assertTrue(size.isDefault());
    assertEquals(Integer.valueOf(7), size.getDefaultValue());
    assertEquals("7", size.getValueAsString());
    assertEquals("SIZE=7", size.toString());
    assertEquals("SIZE", size.getKey());

    factory.save();
    assertFalse(Files.readString(file.toPath()).contains("SIZE"), "defaults stay out of the file");
  }

  @Test
  void aChangedValueIsSavedAndComesBackInTheNextRun() {
    IntSetting size = factory.createIntSetting("SIZE", 7);
    StringSetting name = factory.createStringSetting("NAME", "x");
    BooleanSetting flag = factory.createBooleanSetting("FLAG", false);
    LongSetting big = factory.createLongSetting("BIG", 1L);
    size.setValue(9);
    name.setValue("hello");
    flag.setValue(true);
    big.setValue(5_000_000_000L);
    assertFalse(size.isDefault());
    factory.save();

    SettingsFactory again = reopen();
    assertEquals(9, again.createIntSetting("SIZE", 7).getValue());
    assertEquals("hello", again.createStringSetting("NAME", "x").getValue());
    assertTrue(again.createBooleanSetting("FLAG", false).getValue());
    assertEquals(5_000_000_000L, again.createLongSetting("BIG", 1L).getValue());
  }

  @Test
  void settingsThatAlwaysSaveAreWrittenEvenAtTheirDefault() throws Exception {
    factory.createStringSetting("KEEP", "d").setAlwaysSave(true);
    factory.createStringSetting("DROP", "d");
    factory.save();
    String text = Files.readString(file.toPath());
    assertTrue(text.contains("KEEP=d"), text);
    assertFalse(text.contains("DROP"), text);
  }

  @Test
  void revertingPutsTheDefaultBackAndSaysWhetherAnythingChanged() {
    IntSetting size = factory.createIntSetting("SIZE", 7);
    assertFalse(size.revertToDefault(), "already the default");
    size.setValue(8);
    assertTrue(size.revertToDefault());
    assertEquals(7, size.getValue());
    assertTrue(size.isDefault());

    BooleanSetting flag = factory.createBooleanSetting("FLAG", false);
    flag.invert();
    assertTrue(flag.getValue());
    assertTrue(factory.revertToDefault(), "the factory reverts every setting");
    assertFalse(flag.getValue());
    assertFalse(factory.revertToDefault());
  }

  @Test
  void aKeyCannotBeCreatedTwice() {
    factory.createIntSetting("SIZE", 1);
    assertThrows(IllegalArgumentException.class, () -> factory.createIntSetting("SIZE", 2));
  }

  @Test
  void aValueThatCannotBeReadIsReplacedByTheDefault() throws Exception {
    Properties props = new Properties();
    props.setProperty("SIZE", "lots");
    props.setProperty("BIG", "huge");
    props.setProperty("RATIO", "x");
    try (var out = Files.newOutputStream(file.toPath())) {
      props.store(out, null);
    }
    SettingsFactory again = reopen();
    assertEquals(7, again.createIntSetting("SIZE", 7).getValue());
    assertEquals(3L, again.createLongSetting("BIG", 3L).getValue());
    assertEquals(1.5f, again.createFloatSetting("RATIO", 1.5f).getValue());
  }

  @Test
  void settingsCreatedBeforeAReloadSeeTheNewFile() throws Exception {
    IntSetting size = factory.createIntSetting("SIZE", 7);
    Properties props = new Properties();
    props.setProperty("SIZE", "42");
    try (var out = Files.newOutputStream(file.toPath())) {
      props.store(out, null);
    }
    factory.reload();
    assertEquals(42, size.getValue());
  }

  @Test
  void floatsAndBytesKeepTheirValues() {
    FloatSetting ratio = factory.createFloatSetting("RATIO", 0.5f);
    ratio.setValue(2.25f);
    assertEquals(2.25f, ratio.getValue());
    ByteSetting level = factory.createByteSetting("LEVEL", (byte) 3);
    level.setValue((byte) -7);
    assertEquals((byte) -7, level.getValue());
    level.revertToDefault();
    assertEquals((byte) 3, level.getValue());
  }

  @Test
  void charArraysAreStoredTrimmed() {
    CharArraySetting letters = factory.createCharArraySetting("LETTERS", "abc".toCharArray());
    assertArrayEquals("abc".toCharArray(), letters.getValue());
    letters.setValue("  xyz ".toCharArray());
    assertArrayEquals("xyz".toCharArray(), letters.getValue());
  }

  @Test
  void colorsAreStoredAsHexAndBadHexFallsBackToTheDefault() throws Exception {
    ColorSetting color = factory.createColorSetting("COLOR", new Color(1, 2, 3));
    assertEquals(new Color(1, 2, 3), color.getValue());
    color.setValue(new Color(255, 16, 0));
    assertEquals("#ff1000", color.getValueAsString());
    assertEquals(new Color(255, 16, 0), color.getValue());

    Properties props = new Properties();
    props.setProperty("COLOR2", "#zzzzzz");
    props.setProperty("COLOR3", "#12");
    try (var out = Files.newOutputStream(file.toPath())) {
      props.store(out, null);
    }
    SettingsFactory again = reopen();
    assertEquals(
        new Color(9, 9, 9), again.createColorSetting("COLOR2", new Color(9, 9, 9)).getValue());
    assertEquals(
        new Color(8, 8, 8), again.createColorSetting("COLOR3", new Color(8, 8, 8)).getValue());
  }

  @Test
  void stringArraysAreSemicolonSeparated() {
    StringArraySetting words = factory.createStringArraySetting("WORDS", new String[] {"a", "b"});
    assertArrayEquals(new String[] {"a", "b"}, words.getValue());
    assertEquals("a;b", words.toString());
    words.setValue(new String[] {"x", "y", "z"});
    assertArrayEquals(new String[] {"x", "y", "z"}, words.getValue());
    words.setValue(new String[0]);
    assertEquals(0, words.getValue().length);
    assertEquals("", words.toString());
    words.setValue(null);
    assertEquals(0, words.getValue().length);

    StringArraySetting none = factory.createStringArraySetting("NONE", null);
    assertEquals(0, none.getValue().length);
  }

  @Test
  void stringSetsAddRemoveAndSurviveARestart() {
    StringSetSetting tags = factory.createStringSetSetting("TAGS", "a;b");
    assertEquals(Set.of("a", "b"), tags.getValue());
    assertTrue(tags.add("c"));
    assertFalse(tags.add("c"), "already there");
    assertTrue(tags.contains("c"));
    assertTrue(tags.remove("a"));
    assertFalse(tags.remove("a"));
    assertEquals(Set.of("b", "c"), tags.getValue());
    assertEquals(2, tags.getValueAsArray().length);
    tags.setValue(new HashSet<>());
    assertTrue(tags.getValue().isEmpty());
    tags.setValue(Set.of("z"));
    factory.save();
    assertEquals(Set.of("z"), reopen().createStringSetSetting("TAGS", "a;b").getValue());
  }

  @Test
  void filesAreStoredAsAbsolutePaths() {
    File defaultFile = tmp.resolve("data").resolve("x.txt").toFile();
    FileSetting setting = factory.createFileSetting("FILE", defaultFile);
    assertEquals(defaultFile.getAbsoluteFile(), setting.getValue());
    assertTrue(defaultFile.getParentFile().isDirectory(), "the parent folder is created");
    assertTrue(setting.isPrivate());
    File other = tmp.resolve("y.txt").toFile();
    setting.setValue(other);
    assertEquals(other.getAbsoluteFile(), setting.getValue());
    assertSame(setting, setting.setAlwaysSave(true));
    assertTrue(setting.shouldAlwaysSave());
  }

  @Test
  void fileSetsAddRemoveAndClear() {
    File a = tmp.resolve("a").toFile();
    File b = tmp.resolve("b").toFile();
    FileSetSetting dirs = factory.createFileSetSetting("DIRS", new File[] {a});
    assertEquals(Set.of(a.getAbsoluteFile()), dirs.getValue());
    dirs.add(b);
    assertTrue(dirs.contains(b));
    assertTrue(dirs.remove(a));
    assertFalse(dirs.remove(a));
    assertEquals(Set.of(b.getAbsoluteFile()), dirs.getValue());
    dirs.removeAll();
    assertTrue(dirs.getValue().isEmpty());
    assertTrue(dirs.isPrivate());
  }

  @Test
  void fileArraysKeepOrderAndCompareCanonically() {
    File a = tmp.resolve("a").toFile();
    File b = tmp.resolve("b").toFile();
    FileArraySetting dirs = factory.createFileArraySetting("DIRS", new File[] {a});
    assertEquals(1, dirs.length());
    dirs.add(b);
    dirs.add(null);
    assertEquals(2, dirs.length());
    assertEquals(b.getAbsoluteFile(), dirs.getValue()[1]);
    assertTrue(dirs.contains(new File(tmp.toFile(), "a/../a")), "same file by another path");
    assertFalse(dirs.contains(null));
    assertFalse(dirs.remove(null));
    assertFalse(dirs.remove(tmp.resolve("zzz").toFile()));
    assertTrue(dirs.remove(a));
    assertEquals(1, dirs.length());
    assertTrue(dirs.isPrivate());
  }

  @Test
  void fontNamesAreKeptAsText() {
    FontNameSetting font = factory.createFontNameSetting("FONT", "Dialog");
    assertEquals("Dialog", font.getValueAsString());
  }

  @Test
  void theFactoryListsItsSettingsAndExposesItsProperties() {
    factory.createIntSetting("ONE", 1);
    factory.createIntSetting("TWO", 2);
    int count = 0;
    for (AbstractSetting ignored : factory) {
      count++;
    }
    assertTrue(count >= 2, "plus the expiry marker");
    assertEquals("1", factory.getProperties().getProperty("ONE"));
    assertTrue(factory.toString().contains("TWO=2"));
  }

  @Test
  void aFolderInThePlaceOfTheSettingsFileIsReplaced() throws Exception {
    Path dir = tmp.resolve("asdir");
    Files.createDirectory(dir);
    SettingsFactory over = new SettingsFactory(dir.toFile(), "x");
    over.createIntSetting("SIZE", 1).setValue(2);
    over.save();
    assertTrue(dir.toFile().isFile(), "the folder made way for the settings file");
  }

  @Test
  void aSettingsFileThatCannotBeWrittenIsReportedAsAFailure() throws Exception {
    Path blocker = Files.writeString(tmp.resolve("blocker"), "i am a file");
    SettingsFactory broken = new SettingsFactory(blocker.resolve("inside.props").toFile(), "x");
    SettingsFactory.resetLoadSaveFailure();
    try {
      broken.createIntSetting("SIZE", 1).setValue(2);
      broken.save();
      assertTrue(SettingsFactory.hasLoadSaveFailure());
    } finally {
      SettingsFactory.resetLoadSaveFailure();
    }
    assertFalse(SettingsFactory.hasLoadSaveFailure());
  }

  private SettingsFactory reopenWithExpiry(long lastExpireTime) throws Exception {
    Properties props = new Properties();
    props.setProperty("LAST_EXPIRE_TIME", String.valueOf(lastExpireTime));
    props.setProperty("EXP", "9");
    try (var out = Files.newOutputStream(file.toPath())) {
      props.store(out, null);
    }
    return reopen();
  }

  @Test
  void anExpirableSettingKeepsItsValueUntilTheFileIsTwoWeeksOld() throws Exception {
    SettingsFactory fresh = reopenWithExpiry(System.currentTimeMillis());
    assertEquals(9, fresh.createExpirableIntSetting("EXP", 5).getValue());

    long fifteenDays = 15L * 24 * 60 * 60 * 1000;
    SettingsFactory old = reopenWithExpiry(System.currentTimeMillis() - fifteenDays);
    assertEquals(5, old.createExpirableIntSetting("EXP", 5).getValue());
  }

  @Test
  void aGroupSavesAndReloadsItsFactory() throws Exception {
    // Groups register with the process-wide manager for good, so the file is not under the
    // temporary folder, which is deleted after the test.
    File groupFile = new File(System.getProperty("user.home"), "settings-test/group.props");
    groupFile.delete();
    BasicSettingsGroup group = new BasicSettingsGroup(groupFile, "Group") {};
    IntSetting size = group.getFactory().createIntSetting("SIZE", 1);
    size.setValue(6);
    assertTrue(group.save());
    assertTrue(Files.readString(groupFile.toPath()).contains("SIZE=6"));
    assertSame(size, group.getSetting("SIZE"));
    assertNull(group.getSetting("MISSING"));
    assertNotNull(group.getProperties());
    assertTrue(group.toString().contains("SIZE=6"));

    size.setValue(7);
    group.reload();
    assertEquals(6, size.getValue(), "reload reads the saved file back");
    assertTrue(group.revertToDefault());
    assertEquals(1, size.getValue());
    groupFile.delete();
  }
}
