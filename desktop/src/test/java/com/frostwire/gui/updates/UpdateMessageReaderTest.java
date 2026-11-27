/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * The update.xml the server publishes, parsed by the reader: which message is the update, which
 * announcements are shown, which overlays win, and what is dropped for another OS, version or
 * language. The documents are fed to the reader directly; the network read is not exercised.
 */
class UpdateMessageReaderTest {
  private String language;
  private String country;
  private String variant;

  @BeforeEach
  void pinTheLanguage() {
    language = ApplicationSettings.LANGUAGE.getValue();
    country = ApplicationSettings.COUNTRY.getValue();
    variant = ApplicationSettings.LOCALE_VARIANT.getValue();
    ApplicationSettings.LANGUAGE.setValue("en");
    ApplicationSettings.COUNTRY.setValue("US");
    ApplicationSettings.LOCALE_VARIANT.setValue("");
  }

  @AfterEach
  void restore() {
    ApplicationSettings.LANGUAGE.setValue(language);
    ApplicationSettings.COUNTRY.setValue(country);
    ApplicationSettings.LOCALE_VARIANT.setValue(variant);
  }

  private static UpdateMessageReader read(String body) throws Exception {
    return read("2000000", body);
  }

  private static UpdateMessageReader read(String serverTime, String body) throws Exception {
    String xml = "<update time=\"" + serverTime + "\">" + body + "</update>";
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    XMLReader xmlReader = factory.newSAXParser().getXMLReader();
    UpdateMessageReader reader = new UpdateMessageReader();
    xmlReader.setContentHandler(reader);
    xmlReader.parse(new InputSource(new StringReader(xml)));
    return reader;
  }

  /** The os attribute that addresses this machine. */
  private static String myOs() {
    if (OSUtils.isMacOSX()) {
      return "mac." + OSUtils.getMacOSArchitecture();
    }
    return OSUtils.isWindows() ? "windows" : "linux";
  }

  /** An os attribute that addresses some other machine. */
  private static String otherOs() {
    return OSUtils.isWindows() ? "linux" : "windows";
  }

  @Test
  void anEmptyDocumentHasNothingToShow() throws Exception {
    UpdateMessageReader reader = read("");
    assertFalse(reader.hasUpdateMessage());
    assertFalse(reader.hasAnnouncements());
    assertNull(reader.getAnnouncements());
  }

  @Test
  void theUpdateElementSetsTheServerTime() throws Exception {
    read("1234567", "");
    assertEquals(1234567L, UpdateManager.getInstance().getServerTime().getTime());
  }

  @Test
  void anUnreadableServerTimeFallsBackToTheLocalClock() throws Exception {
    long before = System.currentTimeMillis();
    read("soon", "");
    long serverTime = UpdateManager.getInstance().getServerTime().getTime();
    assertTrue(serverTime >= before && serverTime <= System.currentTimeMillis() + 1000);
  }

  @Test
  void theUpdateMessageCarriesEveryAttribute() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"update\" value=\"New build\" valueInstallerReady=\"Ready\""
                + " version=\"9.9.9\" build=\"9999\" url=\"https://example.com/dl\""
                + " torrent=\"http://example.com/a.torrent\" installerUrl=\"https://example.com/a.dmg\""
                + " saveAs=\"FrostWire.dmg\" md5=\"abcdef\" showOnce=\"true\" language=\"*\" />");
    assertTrue(reader.hasUpdateMessage());
    UpdateMessage m = reader.getUpdateMessage();
    assertEquals("update", m.getMessageType());
    assertEquals("New build", m.getMessage());
    assertEquals("Ready", m.getMessageInstallerReady());
    assertEquals("9.9.9", m.getVersion());
    assertEquals("9999", m.getBuild());
    assertEquals("https://example.com/dl", m.getUrl());
    assertEquals("http://example.com/a.torrent", m.getTorrent());
    assertEquals("https://example.com/a.dmg", m.getInstallerUrl());
    assertEquals("FrostWire.dmg", m.getSaveAs());
    assertEquals("ABCDEF", m.getRemoteMD5());
    assertTrue(m.isShownOnce());
    assertEquals("*", m.getLanguage());
  }

  @Test
  void onlyTheFirstUpdateMessageCounts() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"update\" value=\"newest\" version=\"9.9.9\" />"
                + "<message type=\"update\" value=\"older\" version=\"9.9.8\" />");
    assertEquals("newest", reader.getUpdateMessage().getMessage());
  }

  @Test
  void anUpdateForAnotherOsIsDiscardedAndOneForThisOsIsKept() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"update\" value=\"other\" version=\"9.9.9\" os=\""
                + otherOs()
                + "\" />"
                + "<message type=\"update\" value=\"mine\" version=\"9.9.9\" os=\""
                + myOs()
                + "\" />");
    assertEquals("mine", reader.getUpdateMessage().getMessage());
  }

  @Test
  void anOsWildcardAddressesEveryone() throws Exception {
    UpdateMessageReader reader =
        read("<message type=\"update\" value=\"all\" version=\"9.9.9\" os=\"*\" />");
    assertEquals("all", reader.getUpdateMessage().getMessage());
  }

  @Test
  void aMacMessageForTheOtherArchitectureIsDiscarded() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(OSUtils.isMacOSX());
    String otherArch = "arm64".equals(OSUtils.getMacOSArchitecture()) ? "x86_64" : "arm64";
    UpdateMessageReader reader =
        read(
            "<message type=\"update\" value=\"x\" version=\"9.9.9\" os=\"mac."
                + otherArch
                + "\" />");
    assertFalse(reader.hasUpdateMessage());
  }

  @Test
  void announcementsThatHaveNotExpiredAreKeptAndExpiredOnesDropped() throws Exception {
    UpdateMessageReader reader =
        read(
            "5000",
            "<message type=\"announcement\" value=\"forever\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"later\" url=\"https://b\" expiration=\"6000\" />"
                + "<message type=\"announcement\" value=\"gone\" url=\"https://c\" expiration=\"4000\" />");
    assertTrue(reader.hasAnnouncements());
    HashSet<UpdateMessage> announcements = reader.getAnnouncements();
    assertEquals(2, announcements.size());
    List<String> texts = announcements.stream().map(UpdateMessage::getMessage).sorted().toList();
    assertEquals(List.of("forever", "later"), texts);
  }

  @Test
  void anAnnouncementForANewerVersionWaitsUntilFrostWireIsThatNew() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"announcement\" value=\"future\" version=\"999.0.0\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"past\" version=\"0.0.1\" url=\"https://b\" />"
                + "<message type=\"announcement\" value=\"now\" version=\""
                + FrostWireUtils.getFrostWireVersion()
                + "\" url=\"https://c\" />");
    List<String> texts =
        reader.getAnnouncements().stream().map(UpdateMessage::getMessage).sorted().toList();
    assertEquals(List.of("now", "past"), texts);
  }

  @Test
  void aBuildNumberAheadOfThisOneMakesAnnouncementsWait() throws Exception {
    int build = FrostWireUtils.getBuildNumber();
    UpdateMessageReader reader =
        read(
            "<message type=\"announcement\" value=\"ahead\" version=\"0.0.1\" build=\""
                + (build + 1)
                + "\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"same\" version=\"0.0.1\" build=\""
                + build
                + "\" url=\"https://b\" />");
    assertEquals(
        List.of("same"),
        reader.getAnnouncements().stream().map(UpdateMessage::getMessage).toList());
  }

  @Test
  void anUpdateMessageIsKeptWhateverVersionItNames() throws Exception {
    UpdateMessageReader reader =
        read("<message type=\"update\" value=\"u\" version=\"0.0.1\" url=\"https://a\" />");
    assertNotNull(reader.getUpdateMessage());
  }

  @Test
  void aMessageInAnotherLanguageIsDiscarded() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"announcement\" value=\"es\" language=\"es\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"en\" language=\"en\" url=\"https://b\" />"
                + "<message type=\"announcement\" value=\"enUS\" language=\"en_US\" url=\"https://c\" />"
                + "<message type=\"announcement\" value=\"enGB\" language=\"en_GB\" url=\"https://d\" />"
                + "<message type=\"announcement\" value=\"enAny\" language=\"en*\" url=\"https://e\" />"
                + "<message type=\"announcement\" value=\"any\" language=\"*\" url=\"https://f\" />");
    List<String> texts =
        reader.getAnnouncements().stream().map(UpdateMessage::getMessage).sorted().toList();
    assertEquals(List.of("any", "en", "enAny", "enUS"), texts);
  }

  @Test
  void aSpanishAppSeesSpanishAndWildcardMessages() throws Exception {
    ApplicationSettings.LANGUAGE.setValue("es");
    ApplicationSettings.COUNTRY.setValue("VE");
    UpdateMessageReader reader =
        read(
            "<message type=\"announcement\" value=\"es\" language=\"es\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"esVE\" language=\"es_VE\" url=\"https://b\" />"
                + "<message type=\"announcement\" value=\"esMX\" language=\"es_MX\" url=\"https://c\" />"
                + "<message type=\"announcement\" value=\"esAny\" language=\"es*\" url=\"https://d\" />"
                + "<message type=\"announcement\" value=\"en\" language=\"en\" url=\"https://e\" />");
    List<String> texts =
        reader.getAnnouncements().stream().map(UpdateMessage::getMessage).sorted().toList();
    assertEquals(List.of("es", "esAny", "esVE"), texts);
  }

  @Test
  void aMessageWithoutALanguageIsValidForEveryLanguage() throws Exception {
    ApplicationSettings.LANGUAGE.setValue("es");
    ApplicationSettings.COUNTRY.setValue("VE");
    UpdateMessageReader reader =
        read(
            "<message type=\"update\" version=\"9.9.9\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"any\" url=\"https://b\" />");
    assertNotNull(reader.getUpdateMessage(), "no language attribute means valid for any language");
    assertNull(reader.getUpdateMessage().getLanguage());
    assertEquals(
        List.of("any"), reader.getAnnouncements().stream().map(UpdateMessage::getMessage).toList());
  }

  @Test
  void anAnnouncementForAFutureBuildWithoutAVersionWaits() throws Exception {
    int build = FrostWireUtils.getBuildNumber();
    UpdateMessageReader reader =
        read(
            "<message type=\"announcement\" value=\"ahead\" build=\""
                + (build + 1)
                + "\" url=\"https://a\" />"
                + "<message type=\"announcement\" value=\"same\" build=\""
                + build
                + "\" url=\"https://b\" />"
                + "<message type=\"announcement\" value=\"none\" url=\"https://c\" />");
    assertEquals(
        List.of("none", "same"),
        reader.getAnnouncements().stream().map(UpdateMessage::getMessage).sorted().toList());
  }

  @Test
  void overlaysAreNeitherTheUpdateNorAnnouncements() throws Exception {
    UpdateMessageReader reader =
        read(
            "<message type=\"overlay\" value=\"generic intro\" intro=\"true\" src=\"a.png\" />"
                + "<message type=\"overlay\" value=\"generic after\" intro=\"0\" src=\"b.png\" />"
                + "<message type=\"overlay\" value=\"specific intro\" intro=\"yes\" src=\"c.png\" />");
    assertFalse(reader.hasUpdateMessage());
    assertFalse(reader.hasAnnouncements());
  }

  @Test
  void aMessageIsFoundNoMatterHowDeepItIsNested() throws Exception {
    UpdateMessageReader reader =
        read("<group><message type=\"update\" value=\"nested\" version=\"9.9.9\" /></group>");
    assertEquals("nested", reader.getUpdateMessage().getMessage());
  }
}
