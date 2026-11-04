/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.frostwire.bittorrent.CopyrightLicenseBroker.LicenseCategory;
import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The license a user picks in the Create Torrent dialog is written into the torrent by {@link
 * CopyrightLicenseBroker#asMap()} and read back from other people's torrents by its map
 * constructor.
 */
class CopyrightLicenseBrokerTest {

  @Test
  void everyCreativeCommonsTogglesCombinationMapsToAKnownLicense() {
    assertCreativeCommons(Licenses.CC_BY_4, false, false, false);
    assertCreativeCommons(Licenses.CC_BY_SA_4, true, false, false);
    assertCreativeCommons(Licenses.CC_BY_ND_4, false, false, true);
    assertCreativeCommons(Licenses.CC_BY_NC_4, false, true, false);
    assertCreativeCommons(Licenses.CC_BY_NC_SA_4, true, true, false);
    assertCreativeCommons(Licenses.CC_BY_NC_ND_4, false, true, true);
  }

  @Test
  void shareAlikeWinsOverNoDerivativesBecauseTheyCannotBeCombined() {
    assertCreativeCommons(Licenses.CC_BY_SA_4, true, false, true);
    assertCreativeCommons(Licenses.CC_BY_NC_SA_4, true, true, true);
  }

  @Test
  void aCreativeCommonsPickIsWrittenWithItsAttribution() {
    CopyrightLicenseBroker broker =
        new CopyrightLicenseBroker(
            true, true, false, "Jazz Collection", "Some Artist", "https://example.org/jazz");

    assertSame(Licenses.CC_BY_NC_SA_4, broker.license);
    assertEquals(Licenses.CC_BY_NC_SA_4.getName(), broker.getLicenseName());
    Map<String, Map<String, String>> map = broker.asMap();
    assertEquals(1, map.size());
    Map<String, String> fields = map.get("creative-commons");
    assertEquals(Licenses.CC_BY_NC_SA_4.getUrl(), fields.get("licenseUrl"));
    assertEquals("Jazz Collection", fields.get("attributionTitle"));
    assertEquals("Some Artist", fields.get("attributionAuthor"));
    assertEquals("https://example.org/jazz", fields.get("attributionUrl"));
  }

  @Test
  void aTorrentMadeBeforeCreativeCommonsUrlsMovedToHttpsStillShowsItsLicense() {
    CopyrightLicenseBroker broker =
        new CopyrightLicenseBroker(
            Map.of(
                "creative-commons",
                Map.of("licenseUrl", "http://creativecommons.org/licenses/by-sa/4.0/")));

    assertSame(Licenses.CC_BY_SA_4, broker.license);
    assertEquals(Licenses.CC_BY_SA_4.getName(), broker.getLicenseName());
  }

  @Test
  void aWrittenLicenseReadsBackTheSame() {
    CopyrightLicenseBroker written =
        new CopyrightLicenseBroker(
            LicenseCategory.OpenSource, Licenses.MIT, "Tool", "Dev", "https://example.org");

    @SuppressWarnings({"unchecked", "rawtypes"})
    CopyrightLicenseBroker read = new CopyrightLicenseBroker((Map) written.asMap());

    assertSame(Licenses.MIT, read.license);
    assertEquals(written.asMap(), read.asMap());
  }

  @Test
  void readsEachLicenseCategory() {
    assertCategory("creative-commons", Licenses.CC_BY_4);
    assertCategory("open-source", Licenses.APACHE);
    assertCategory("open-source", Licenses.GPL3);
    assertCategory("public-domain", Licenses.PUBLIC_DOMAIN_CC0);
    assertCategory("public-domain", Licenses.PUBLIC_DOMAIN_MARK);
  }

  @Test
  void aTorrentWithoutAKnownCategoryHasNoLicense() {
    CopyrightLicenseBroker broker =
        new CopyrightLicenseBroker(Map.of("something-else", Map.of("licenseUrl", "x")));

    assertNull(broker.license);
    assertNull(broker.getLicenseName());
  }

  @Test
  void aCategoryThatIsNotADictionaryHasNoFields() {
    CopyrightLicenseBroker broker = new CopyrightLicenseBroker(Map.of("open-source", "MIT"));

    assertNull(broker.license);
    assertNull(broker.getLicenseName());
  }

  @Test
  void licenseCategoriesUseTheirTorrentKeys() {
    assertEquals("creative-commons", LicenseCategory.CreativeCommons.toString());
    assertEquals("open-source", LicenseCategory.OpenSource.toString());
    assertEquals("public-domain", LicenseCategory.PublicDomain.toString());
    assertEquals("no-license", LicenseCategory.NoLicense.toString());
  }

  private static void assertCreativeCommons(
      License expected, boolean shareAlike, boolean nonCommercial, boolean noDerivatives) {
    assertEquals(
        expected.getUrl(),
        CopyrightLicenseBroker.getCreativeCommonsLicenseUrl(
            shareAlike, nonCommercial, noDerivatives));
    CopyrightLicenseBroker broker =
        new CopyrightLicenseBroker(shareAlike, nonCommercial, noDerivatives, "t", "a", "u");
    assertSame(expected, broker.license);
  }

  private static void assertCategory(String category, License license) {
    CopyrightLicenseBroker broker =
        new CopyrightLicenseBroker(
            Map.of(
                category,
                Map.of(
                    "licenseUrl", license.getUrl(),
                    "attributionTitle", "Title",
                    "attributionAuthor", "Author",
                    "attributionUrl", "https://example.org")));

    assertSame(license, broker.license);
    assertEquals(license.getName(), broker.getLicenseName());
    Map<String, String> fields = broker.asMap().get(category);
    assertEquals("Title", fields.get("attributionTitle"));
    assertEquals("Author", fields.get("attributionAuthor"));
    assertEquals("https://example.org", fields.get("attributionUrl"));
  }
}
