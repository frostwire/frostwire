/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.PatternMatcher;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads MainActivity's intent filters from {@code AndroidManifest.xml} into the framework's own
 * {@link IntentFilter} and checks that the links FrostWire claims are really routed to it.
 *
 * <p>A magnet link is an opaque URI with no host. The magnet filter used to list {@code
 * android:host="*"} as well, and a filter that lists a host rejects every URI without one, so
 * browsers never offered FrostWire for magnet links (frostwire/frostwire#1513).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public final class MainActivityMagnetIntentFilterTest {

  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String MAIN_ACTIVITY = "com.frostwire.android.gui.activities.MainActivity";

  @Test
  public void aMagnetLinkTappedInABrowserOpensFrostWire() throws Exception {
    assertViewMatches(
        "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a&dn=ubuntu.iso", null);
  }

  @Test
  public void everyMagnetFormOpensFrostWire() throws Exception {
    assertViewMatches(
        "magnet:?dn=Name&tr=udp%3A%2F%2Ftracker.example%3A80"
            + "&xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a",
        null);
    assertViewMatches("magnet:?xt=urn:btih:YEX6DQDLXISUUTOJ6UM3GNK2PQJWPKEK", null);
    assertViewMatches(
        "magnet:?xt=urn:btmh:1220caf1e1c30e81cb361b9ee167c4aa64228a7fa4fa9f6105232b28ad099f3a302e",
        null);
  }

  @Test
  public void aTorrentFileServedOverHttpsStillOpensFrostWire() throws Exception {
    assertViewMatches("https://example.com/files/ubuntu.torrent", "application/x-bittorrent");
  }

  @Test
  public void anOrdinaryWebPageDoesNotOpenFrostWire() throws Exception {
    assertFalse(viewMatches("https://example.com/index.html", null));
  }

  private static void assertViewMatches(String uri, String type) throws Exception {
    assertTrue("MainActivity's intent filters don't accept " + uri, viewMatches(uri, type));
  }

  /** As a browser does: ACTION_VIEW with CATEGORY_BROWSABLE, and DEFAULT from startActivity. */
  private static boolean viewMatches(String uri, String type) throws Exception {
    Uri data = Uri.parse(uri);
    Set<String> categories = new HashSet<>();
    categories.add(Intent.CATEGORY_BROWSABLE);
    categories.add(Intent.CATEGORY_DEFAULT);
    for (IntentFilter filter : mainActivityFilters()) {
      if (filter.match(Intent.ACTION_VIEW, type, data.getScheme(), data, categories, "test") >= 0) {
        return true;
      }
    }
    return false;
  }

  private static List<IntentFilter> mainActivityFilters() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document manifest = factory.newDocumentBuilder().parse(manifestFile());
    NodeList activities = manifest.getElementsByTagName("activity");
    List<IntentFilter> filters = new ArrayList<>();
    for (int i = 0; i < activities.getLength(); i++) {
      Element activity = (Element) activities.item(i);
      if (!MAIN_ACTIVITY.equals(activity.getAttributeNS(ANDROID_NS, "name"))) {
        continue;
      }
      for (Element filterElement : children(activity, "intent-filter")) {
        filters.add(toIntentFilter(filterElement));
      }
    }
    assertFalse("MainActivity has no intent filters in the manifest", filters.isEmpty());
    return filters;
  }

  private static IntentFilter toIntentFilter(Element element) throws Exception {
    IntentFilter filter = new IntentFilter();
    for (Element action : children(element, "action")) {
      filter.addAction(action.getAttributeNS(ANDROID_NS, "name"));
    }
    for (Element category : children(element, "category")) {
      filter.addCategory(category.getAttributeNS(ANDROID_NS, "name"));
    }
    for (Element data : children(element, "data")) {
      String scheme = data.getAttributeNS(ANDROID_NS, "scheme");
      String host = data.getAttributeNS(ANDROID_NS, "host");
      String mimeType = data.getAttributeNS(ANDROID_NS, "mimeType");
      String pathPattern = data.getAttributeNS(ANDROID_NS, "pathPattern");
      if (!scheme.isEmpty()) {
        filter.addDataScheme(scheme);
      }
      if (!host.isEmpty()) {
        filter.addDataAuthority(host, null);
      }
      if (!mimeType.isEmpty()) {
        filter.addDataType(mimeType);
      }
      if (!pathPattern.isEmpty()) {
        // aapt turns the manifest's "\\." into "\.", PatternMatcher's escaped dot.
        filter.addDataPath(pathPattern.replace("\\\\", "\\"), PatternMatcher.PATTERN_SIMPLE_GLOB);
      }
    }
    return filter;
  }

  private static List<Element> children(Element parent, String tag) {
    List<Element> result = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element && tag.equals(((Element) n).getTagName())) {
        result.add((Element) n);
      }
    }
    return result;
  }

  private static File manifestFile() {
    File manifest = new File("AndroidManifest.xml");
    if (!manifest.isFile()) {
      manifest = new File("android", "AndroidManifest.xml");
    }
    assertTrue("AndroidManifest.xml not found", manifest.isFile());
    return manifest;
  }
}
