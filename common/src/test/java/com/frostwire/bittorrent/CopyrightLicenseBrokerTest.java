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
