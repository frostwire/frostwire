/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.licenses;

import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Licenses {
    public static final License UNKNOWN = new License("Unknown", "https://www.google.com/#q=license");
    public static final License APACHE = new License("Apache License, Version 2.0", "https://www.apache.org/licenses/LICENSE-2.0.html");
    public static final License BSD_2_CLAUSE = new License("The FreeBSD Copyright License (BSD 2 Clause)", "http://www.freebsd.org/copyright/freebsd-license.html");
    public static final License BSD_3_CLAUSE = new License("The FreeBSD Copyright License (BSD 3 Clause)", "http://opensource.org/licenses/BSD-3-Clause");
    public static final License GPL3 = new License("GNU General Public License Version 3 (GPL 3)", "https://www.gnu.org/licenses/gpl.html");
    public static final License LGPL = new License("GNU Lesser General Public License Version 3 (LGPL 3)", "http://www.gnu.org/copyleft/lesser.html");
    public static final License MIT = new License("The MIT License (MIT)", "http://opensource.org/licenses/MIT");
    public static final License MOZILLA = new License("Mozilla Public License (MPL 2.0)", "http://www.mozilla.org/MPL/2.0/");
    public static final License CDDL = new License("Common Development and Distribution License (CDDL-1.0)", "http://opensource.org/licenses/CDDL-1.0");
    public static final License ECLIPSE = new License("Eclipse Public License Version 1.0 (EPL-1.0)", "http://www.eclipse.org/legal/epl-v10.html");
    public static final License CC_BY_4 = CreativeCommonsLicense.standard("Attribution", "BY", "4.0");
    public static final License CC_BY_SA_4 = CreativeCommonsLicense.standard("Attribution-ShareAlike", "BY-SA", "4.0");
    public static final License CC_BY_ND_4 = CreativeCommonsLicense.standard("Attribution-NoDerivs", "BY-ND", "4.0");
    public static final License CC_BY_NC_4 = CreativeCommonsLicense.standard("Attribution-NonCommercial", "BY-NC", "4.0");
    public static final License CC_BY_NC_SA_4 = CreativeCommonsLicense.standard("Attribution-NonCommercial-ShareAlike", "BY-NC-SA", "4.0");
    public static final License CC_BY_NC_ND_4 = CreativeCommonsLicense.standard("Attribution-NonCommercial-NoDerivs", "BY-NC-ND", "4.0");
    public static final License PUBLIC_DOMAIN_CC0 = new CreativeCommonsLicense("CC0 1.0 Universal Public Domain Dedication", "CC0 1.0", "http://creativecommons.org/publicdomain/zero/1.0/");
    public static final License PUBLIC_DOMAIN_MARK = new CreativeCommonsLicense("Public Domain Mark 1.0", "Public Domain Mark 1.0", "http://creativecommons.org/publicdomain/mark/1.0/");
    private static final List<License> CREATIVE_COMMONS = Arrays.asList(CC_BY_4, CC_BY_SA_4, CC_BY_ND_4, CC_BY_NC_4, CC_BY_NC_SA_4, CC_BY_NC_ND_4, PUBLIC_DOMAIN_CC0, PUBLIC_DOMAIN_MARK);
    private Licenses() {
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
