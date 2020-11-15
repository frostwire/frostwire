/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class CopyrightLicenseBroker implements Mappable<String, Map<String, String>> {
    private static final List<String> validLicenseUrls;
    private static final Map<String, License> urlToLicense;

    static {
        validLicenseUrls = new ArrayList<>();
        validLicenseUrls.add(Licenses.CC_BY_4.getUrl());
        validLicenseUrls.add(Licenses.CC_BY_SA_4.getUrl());
        validLicenseUrls.add(Licenses.CC_BY_ND_4.getUrl());
        validLicenseUrls.add(Licenses.CC_BY_NC_4.getUrl());
        validLicenseUrls.add(Licenses.CC_BY_NC_SA_4.getUrl());
        validLicenseUrls.add(Licenses.CC_BY_NC_ND_4.getUrl());
        urlToLicense = new HashMap<>();
        urlToLicense.put(Licenses.CC_BY_4.getUrl(), Licenses.CC_BY_4);
        urlToLicense.put(Licenses.CC_BY_SA_4.getUrl(), Licenses.CC_BY_SA_4);
        urlToLicense.put(Licenses.CC_BY_ND_4.getUrl(), Licenses.CC_BY_ND_4);
        urlToLicense.put(Licenses.CC_BY_NC_4.getUrl(), Licenses.CC_BY_NC_4);
        urlToLicense.put(Licenses.CC_BY_NC_SA_4.getUrl(), Licenses.CC_BY_NC_SA_4);
        urlToLicense.put(Licenses.CC_BY_NC_ND_4.getUrl(), Licenses.CC_BY_NC_ND_4);
        urlToLicense.put(Licenses.APACHE.getUrl(), Licenses.APACHE);
        urlToLicense.put(Licenses.BSD_2_CLAUSE.getUrl(), Licenses.BSD_2_CLAUSE);
        urlToLicense.put(Licenses.BSD_3_CLAUSE.getUrl(), Licenses.BSD_3_CLAUSE);
        urlToLicense.put(Licenses.GPL3.getUrl(), Licenses.GPL3);
        urlToLicense.put(Licenses.LGPL.getUrl(), Licenses.LGPL);
        urlToLicense.put(Licenses.MIT.getUrl(), Licenses.MIT);
        urlToLicense.put(Licenses.MOZILLA.getUrl(), Licenses.MOZILLA);
        urlToLicense.put(Licenses.CDDL.getUrl(), Licenses.CDDL);
        urlToLicense.put(Licenses.ECLIPSE.getUrl(), Licenses.ECLIPSE);
        urlToLicense.put(Licenses.PUBLIC_DOMAIN_MARK.getUrl(), Licenses.PUBLIC_DOMAIN_MARK);
        urlToLicense.put(Licenses.PUBLIC_DOMAIN_CC0.getUrl(), Licenses.PUBLIC_DOMAIN_CC0);
    }

    private final LicenseCategory licenseCategory;
    public final License license;
    private final String attributionTitle;
    private final String attributionAuthor;
    private final String attributionUrl;

    @SuppressWarnings("unused")
    public CopyrightLicenseBroker(boolean shareAlike, boolean nonCommercial, boolean noDerivatives, String attributionTitle, String attributionAuthor, String attributionURL) {
        licenseCategory = LicenseCategory.CreativeCommons;
        final String licenseUrl = getCreativeCommonsLicenseUrl(shareAlike, nonCommercial, noDerivatives);
        if (!isInvalidLicense(licenseUrl)) {
            this.license = urlToLicense.get(licenseUrl);
            this.attributionTitle = attributionTitle;
            this.attributionAuthor = attributionAuthor;
            this.attributionUrl = attributionURL;
        } else {
            throw new IllegalArgumentException("The given license string is invalid.");
        }
    }

    /**
     * Deserialization constructor
     *
     * @param map
     */
    public CopyrightLicenseBroker(Map<String, Entry> map) {
        if (map.containsKey("creative-commons")) {
            licenseCategory = LicenseCategory.CreativeCommons;
        } else if (map.containsKey("open-source")) {
            licenseCategory = LicenseCategory.OpenSource;
        } else if (map.containsKey("public-domain")) {
            licenseCategory = LicenseCategory.PublicDomain;
        } else {
            licenseCategory = LicenseCategory.NoLicense;
        }
        if (licenseCategory != LicenseCategory.NoLicense) {
            Map<String, Entry> innerMap = map.get(licenseCategory.toString()).dictionary();
            String licenseUrl = innerMap.get("licenseUrl").string();
            this.license = urlToLicense.get(licenseUrl);
            this.attributionTitle = innerMap.get("attributionTitle").string();
            this.attributionAuthor = innerMap.get("attributionAuthor").string();
            this.attributionUrl = innerMap.get("attributionUrl").string();
        } else {
            this.license = null;
            this.attributionTitle = null;
            this.attributionAuthor = null;
            this.attributionUrl = null;
        }
    }

    public CopyrightLicenseBroker(LicenseCategory category, License license, String title, String author, String attributionUrl) {
        licenseCategory = category;
        this.license = license;
        this.attributionTitle = title;
        this.attributionAuthor = author;
        this.attributionUrl = attributionUrl;
    }

    private static boolean isInvalidLicense(String licenseStr) {
        return licenseStr == null || licenseStr.isEmpty() || !validLicenseUrls.contains(licenseStr);
    }

    /**
     * This method makes sure you input a valid license, even if you make a mistake combining these parameters
     */
    public static String getCreativeCommonsLicenseUrl(boolean shareAlike, boolean nonCommercial, boolean noDerivatives) {
        if (nonCommercial && shareAlike) {
            noDerivatives = false;
        } else if (nonCommercial && noDerivatives) {
            shareAlike = false;
        } else if (shareAlike) {
            noDerivatives = false;
        }
        String licenseShortCode = "by-" + (nonCommercial ? "nc-" : "") + (shareAlike ? "sa" : "") + (noDerivatives ? "-nd" : "");
        licenseShortCode = licenseShortCode.replace("--", "-");
        if (licenseShortCode.endsWith("-")) {
            licenseShortCode = licenseShortCode.substring(0, licenseShortCode.length() - 1);
        }
        return "http://creativecommons.org/licenses/" + licenseShortCode + "/" + "4.0" + "/";
    }

    public Map<String, Map<String, String>> asMap() {
        Map<String, Map<String, String>> container = new HashMap<String, Map<String, String>>();
        Map<String, String> innerMap = new HashMap<String, String>();
        innerMap.put("licenseUrl", this.license.getUrl());
        innerMap.put("attributionTitle", this.attributionTitle);
        innerMap.put("attributionAuthor", this.attributionAuthor);
        innerMap.put("attributionUrl", this.attributionUrl);
        container.put(licenseCategory.toString(), innerMap);
        return container;
    }

    public String getLicenseName() {
        return license != null ? license.getName() : null;
    }

    public enum LicenseCategory {
        CreativeCommons("creative-commons"), OpenSource("open-source"), PublicDomain("public-domain"), NoLicense("no-license");
        private String name;

        LicenseCategory(String stringName) {
            name = stringName;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
