/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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


package com.frostwire.licences;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class License {

    public static final License UNKNOWN = new UnknownLicense();

    public static final License CC_BY = new CreativeCommonsLicense("CC BY", "Creative Commons Attribution", "http://creativecommons.org/licenses/by/3.0");
    public static final License CC_BY_SA = new CreativeCommonsLicense("CC BY-SA", "Creative Commons Attribution-ShareAlike", "http://creativecommons.org/licenses/by-sa/3.0");
    public static final License CC_BY_ND = new CreativeCommonsLicense("CC BY-ND", "Creative Commons Attribution-NoDerivs", "http://creativecommons.org/licenses/by-nd/3.0");
    public static final License CC_BY_NC = new CreativeCommonsLicense("CC BY-NC", "Creative Commons Attribution-NonCommercial", "http://creativecommons.org/licenses/by-nc/3.0");
    public static final License CC_BY_NC_SA = new CreativeCommonsLicense("CC BY-NC-SA", "Creative Commons Attribution-NonCommercial-ShareAlike", "http://creativecommons.org/licenses/by-nc-sa/3.0");
    public static final License CC_BY_NC_ND = new CreativeCommonsLicense("CC BY-NC-ND", "Creative Commons Attribution-NonCommercial-NoDerivs", "http://creativecommons.org/licenses/by-nc-nd/3.0");
    public static final License CC_CC0 = new CreativeCommonsLicense("CC0 1.0", "Creative Commons Public Domain Dedication", "http://creativecommons.org/publicdomain/zero/1.0");

    public static final License CC_PUBLIC_DOMAIN = new PublicDomainDedicationLicense();

    public static final List<License> CREATIVE_COMMONS = Arrays.asList(CC_BY, CC_BY_SA, CC_BY_ND, CC_BY_NC, CC_BY_NC_SA, CC_BY_NC_ND, CC_CC0, CC_PUBLIC_DOMAIN);

    private final String name;
    private final String url;

    License(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof License)) {
            return false;
        }

        return name.equals(((License) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    public static License creativeCommonsByUrl(String url) {
        License lic = UNKNOWN;

        if (url != null) {
            for (License cc : CREATIVE_COMMONS) {
                if (url.contains(cc.getUrl())) {
                    lic = cc;
                }
            }
        }

        return lic;
    }
}
