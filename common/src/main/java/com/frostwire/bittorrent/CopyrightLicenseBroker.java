/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.licences.ApacheLicense;
import com.frostwire.licences.BSD2ClauseLicense;
import com.frostwire.licences.BSD3ClauseLicense;
import com.frostwire.licences.CDDLLicense;
import com.frostwire.licences.CreativeCommonsLicense;
import com.frostwire.licences.EclipseLicense;
import com.frostwire.licences.GPL3License;
import com.frostwire.licences.LGPLLicense;
import com.frostwire.licences.License;
import com.frostwire.licences.MITLicense;
import com.frostwire.licences.MozillaLicense;
import com.frostwire.licences.PublicDomainDedicationLicense;
import com.frostwire.licences.PublicDomainMarkLicense;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class CopyrightLicenseBroker implements Mappable<String, Map<String, String>> {

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

    public final LicenseCategory licenseCategory;

    public final License license;
    public final String attributionTitle;
    public final String attributionAuthor;
    public final String attributionUrl;

    public static final String CC_VERSION = "4.0";

    /** attribution */
    public static final String CC_BY_URL = "http://creativecommons.org/licenses/by/" + CC_VERSION + "/";

    /** attribution - share alike */
    public static final String CC_BY_SA_URL = "http://creativecommons.org/licenses/by-sa/" + CC_VERSION + "/";

    /** attribution - no derivatives */
    public static final String CC_BY_ND_URL = "http://creativecommons.org/licenses/by-nd/" + CC_VERSION + "/";

    /** attribution - non commercial */
    public static final String CC_BY_NC_URL = "http://creativecommons.org/licenses/by-nc/" + CC_VERSION + "/";

    /** attribution - non commercial - share alike */
    public static final String CC_BY_NC_SA_URL = "http://creativecommons.org/licenses/by-nc-sa/" + CC_VERSION + "/";

    /** attribution - non commercial - no derivatives */
    public static final String CC_BY_NC_ND_URL = "http://creativecommons.org/licenses/by-nc-nd/" + CC_VERSION + "/";

    //creative-commons licenses
    public static final License CC_BY_LICENSE = new CreativeCommonsLicense("CC-BY", "Creative Commons Attribution " + CC_VERSION, CC_BY_URL);
    public static final License CC_BY_SA_LICENSE = new CreativeCommonsLicense("CC-BY-SA", "Creative Commons Attribution-ShareAlike " + CC_VERSION, CC_BY_SA_URL);
    public static final License CC_BY_ND_LICENSE = new CreativeCommonsLicense("CC-BY-ND", "Creative Commons Attribution-NoDerivs " + CC_VERSION, CC_BY_ND_URL);
    public static final License CC_BY_NC_LICENSE = new CreativeCommonsLicense("CC-BY-NC", "Creative Commons Attribution-NonCommercial " + CC_VERSION, CC_BY_NC_URL);
    public static final License CC_BY_NC_SA_LICENSE = new CreativeCommonsLicense("CC-BY-NC-SA", "Creative Commons Attribution-NonCommercial-ShareAlike " + CC_VERSION, CC_BY_NC_SA_URL);
    public static final License CC_BY_NC_ND_LICENSE = new CreativeCommonsLicense("CC-BY-NC-ND", "Creative Commons Attribution-NonCommercial-NoDerivs " + CC_VERSION, CC_BY_NC_ND_URL);

    //open-source licenses
    public static final License APACHE_LICENSE = new ApacheLicense();
    public static final License BSD_2_CLAUSE_LICENSE = new BSD2ClauseLicense();
    public static final License BSD_3_CLAUSE_LICENSE = new BSD3ClauseLicense();
    public static final License GPL3_LICENSE = new GPL3License();
    public static final License LGPL_LICENSE = new LGPLLicense();
    public static final License MIT_LICENSE = new MITLicense();
    public static final License MOZILLA_LICENSE = new MozillaLicense();
    public static final License CDDL_LICENSE = new CDDLLicense();
    public static final License ECLIPSE_LICENSE = new EclipseLicense();

    //public-domain licenses
    public static final License PUBLIC_DOMAIN_MARK_LICENSE = new PublicDomainMarkLicense();
    public static final License PUBLIC_DOMAIN_CC0_LICENSE = new PublicDomainDedicationLicense();

    public static final List<String> validLicenseUrls;

    public static final Map<String, License> urlToLicense;

    public final String BY_WORD = "Attribution";
    public final String SA_WORD = "Share-Alike";
    public final String ND_WORD = "NoDerivatives";
    public final String NC_WORD = "NonCommercial";
    public final String INTERNATIONAL_LICENSE = "International License";

    static {
        validLicenseUrls = new ArrayList<String>();
        validLicenseUrls.add(CC_BY_URL);
        validLicenseUrls.add(CC_BY_SA_URL);
        validLicenseUrls.add(CC_BY_ND_URL);
        validLicenseUrls.add(CC_BY_NC_URL);
        validLicenseUrls.add(CC_BY_NC_SA_URL);
        validLicenseUrls.add(CC_BY_NC_ND_URL);

        urlToLicense = new HashMap<String, License>();
        urlToLicense.put(CC_BY_URL, CC_BY_LICENSE);
        urlToLicense.put(CC_BY_SA_URL, CC_BY_SA_LICENSE);
        urlToLicense.put(CC_BY_ND_URL, CC_BY_ND_LICENSE);
        urlToLicense.put(CC_BY_NC_URL, CC_BY_NC_LICENSE);
        urlToLicense.put(CC_BY_NC_SA_URL, CC_BY_NC_SA_LICENSE);
        urlToLicense.put(CC_BY_NC_ND_URL, CC_BY_NC_ND_LICENSE);

        urlToLicense.put(APACHE_LICENSE.getUrl(), APACHE_LICENSE);
        urlToLicense.put(BSD_2_CLAUSE_LICENSE.getUrl(), BSD_2_CLAUSE_LICENSE);
        urlToLicense.put(BSD_3_CLAUSE_LICENSE.getUrl(), BSD_3_CLAUSE_LICENSE);
        urlToLicense.put(GPL3_LICENSE.getUrl(), GPL3_LICENSE);
        urlToLicense.put(LGPL_LICENSE.getUrl(), LGPL_LICENSE);
        urlToLicense.put(MIT_LICENSE.getUrl(), MIT_LICENSE);
        urlToLicense.put(MOZILLA_LICENSE.getUrl(), MOZILLA_LICENSE);
        urlToLicense.put(CDDL_LICENSE.getUrl(), CDDL_LICENSE);
        urlToLicense.put(ECLIPSE_LICENSE.getUrl(), ECLIPSE_LICENSE);
        
        urlToLicense.put(PUBLIC_DOMAIN_MARK_LICENSE.getUrl(), PUBLIC_DOMAIN_MARK_LICENSE);
        urlToLicense.put(PUBLIC_DOMAIN_CC0_LICENSE.getUrl(), PUBLIC_DOMAIN_CC0_LICENSE);
    }

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

    /** Deserialization constructor
     * @param map*/
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

    public String getLicenseShortCode() {
        return license.getName();
    }

    public String getLicenseName() {
        if (license instanceof CreativeCommonsLicense) {
            return ((CreativeCommonsLicense) license).getLongName();
        } else {
            return license.getName();
        }
    }

    private static boolean isInvalidLicense(String licenseStr) {
        return licenseStr == null || licenseStr.isEmpty() || !validLicenseUrls.contains(licenseStr);
    }

    /** This method makes sure you input a valid license, even if you make a mistake combining these parameters */
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
        return "http://creativecommons.org/licenses/" + licenseShortCode + "/" + CC_VERSION + "/";
    }

    private static void testValidLicenseStringGeneration() {
        String licenseUrl = getCreativeCommonsLicenseUrl(false, false, false);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(false, false, true);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(false, true, false);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(false, true, true);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(true, false, false);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(true, false, true);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(true, true, false);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));

        licenseUrl = getCreativeCommonsLicenseUrl(true, true, true);
        System.out.println(licenseUrl + " is valid license? " + !isInvalidLicense(licenseUrl));
    }

    public static void main(String[] arg) {
        testValidLicenseStringGeneration();
    }
}
