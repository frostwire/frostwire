/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.mplayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class ISO639 {
    private static final Map<String, String> ISO_639_2_to_ISO_639_1;

    static {
        ISO_639_2_to_ISO_639_1 = new HashMap<>();
        addCode("﻿aar", "aa");
        addCode("abk", "ab");
        addCode("afr", "af");
        addCode("aka", "ak");
        addCode("alb", "sq");
        addCode("amh", "am");
        addCode("ara", "ar");
        addCode("arg", "an");
        addCode("arm", "hy");
        addCode("asm", "as");
        addCode("ava", "av");
        addCode("ave", "ae");
        addCode("aym", "ay");
        addCode("aze", "az");
        addCode("bak", "ba");
        addCode("bam", "bm");
        addCode("baq", "eu");
        addCode("bel", "be");
        addCode("ben", "bn");
        addCode("bih", "bh");
        addCode("bis", "bi");
        addCode("bos", "bs");
        addCode("bre", "br");
        addCode("bul", "bg");
        addCode("bur", "my");
        addCode("cat", "ca");
        addCode("cha", "ch");
        addCode("che", "ce");
        addCode("chi", "zh");
        addCode("chu", "cu");
        addCode("chv", "cv");
        addCode("cor", "kw");
        addCode("cos", "co");
        addCode("cre", "cr");
        addCode("cze", "cs");
        addCode("dan", "da");
        addCode("div", "dv");
        addCode("dut", "nl");
        addCode("dzo", "dz");
        addCode("eng", "en");
        addCode("epo", "eo");
        addCode("est", "et");
        addCode("ewe", "ee");
        addCode("fao", "fo");
        addCode("fij", "fj");
        addCode("fin", "fi");
        addCode("fre", "fr");
        addCode("fry", "fy");
        addCode("ful", "ff");
        addCode("gaa", "Ga");
        addCode("geo", "ka");
        addCode("ger", "de");
        addCode("gla", "gd");
        addCode("gle", "ga");
        addCode("glg", "gl");
        addCode("glv", "gv");
        addCode("gre", "el");
        addCode("grn", "gn");
        addCode("guj", "gu");
        addCode("hat", "ht");
        addCode("hau", "ha");
        addCode("heb", "he");
        addCode("her", "hz");
        addCode("hin", "hi");
        addCode("hmo", "ho");
        addCode("hrv", "hr");
        addCode("hun", "hu");
        addCode("ibo", "ig");
        addCode("ice", "is");
        addCode("ido", "io");
        addCode("iii", "ii");
        addCode("iku", "iu");
        addCode("ile", "ie");
        addCode("ina", "ia");
        addCode("ind", "id");
        addCode("ipk", "ik");
        addCode("ita", "it");
        addCode("jav", "jv");
        addCode("jpn", "ja");
        addCode("kal", "kl");
        addCode("kan", "kn");
        addCode("kas", "ks");
        addCode("kau", "kr");
        addCode("kaz", "kk");
        addCode("khm", "km");
        addCode("kik", "ki");
        addCode("kin", "rw");
        addCode("kir", "ky");
        addCode("kom", "kv");
        addCode("kon", "kg");
        addCode("kor", "ko");
        addCode("kua", "kj");
        addCode("kur", "ku");
        addCode("lao", "lo");
        addCode("lat", "la");
        addCode("lav", "lv");
        addCode("lim", "li");
        addCode("lin", "ln");
        addCode("lit", "lt");
        addCode("ltz", "lb");
        addCode("lub", "lu");
        addCode("lug", "lg");
        addCode("mac", "mk");
        addCode("mah", "mh");
        addCode("mal", "ml");
        addCode("mao", "mi");
        addCode("mar", "mr");
        addCode("may", "ms");
        addCode("mlg", "mg");
        addCode("mlt", "mt");
        addCode("mon", "mn");
        addCode("nau", "na");
        addCode("nav", "nv");
        addCode("nbl", "nr");
        addCode("nde", "nd");
        addCode("ndo", "ng");
        addCode("nep", "ne");
        addCode("nno", "nn");
        addCode("nob", "nb");
        addCode("nor", "no");
        addCode("nya", "ny");
        addCode("oci", "oc");
        addCode("oji", "oj");
        addCode("ori", "or");
        addCode("orm", "om");
        addCode("oss", "os");
        addCode("pan", "pa");
        addCode("per", "fa");
        addCode("pli", "pi");
        addCode("pol", "pl");
        addCode("por", "pt");
        addCode("pus", "ps");
        addCode("que", "qu");
        addCode("roh", "rm");
        addCode("rum", "ro");
        addCode("run", "rn");
        addCode("rus", "ru");
        addCode("sag", "sg");
        addCode("san", "sa");
        addCode("sin", "si");
        addCode("slo", "sk");
        addCode("slv", "sl");
        addCode("sme", "se");
        addCode("smo", "sm");
        addCode("sna", "sn");
        addCode("snd", "sd");
        addCode("som", "so");
        addCode("sot", "st");
        addCode("spa", "es");
        addCode("srd", "sc");
        addCode("srp", "sr");
        addCode("ssw", "ss");
        addCode("sun", "su");
        addCode("swa", "sw");
        addCode("swe", "sv");
        addCode("tah", "ty");
        addCode("tam", "ta");
        addCode("tat", "tt");
        addCode("tel", "te");
        addCode("tgk", "tg");
        addCode("tgl", "tl");
        addCode("tha", "th");
        addCode("tib", "bo");
        addCode("tir", "ti");
        addCode("ton", "to");
        addCode("tsn", "tn");
        addCode("tso", "ts");
        addCode("tuk", "tk");
        addCode("tur", "tr");
        addCode("twi", "tw");
        addCode("uig", "ug");
        addCode("ukr", "uk");
        addCode("urd", "ur");
        addCode("uzb", "uz");
        addCode("ven", "ve");
        addCode("vie", "vi");
        addCode("vol", "vo");
        addCode("wel", "cy");
        addCode("wln", "wa");
        addCode("wol", "wo");
        addCode("xho", "xh");
        addCode("yid", "yi");
        addCode("yor", "yo");
        addCode("zha", "za");
        addCode("zul", "zu");
    }

    private static void addCode(String iso3, String iso2) {
        ISO_639_2_to_ISO_639_1.put(iso3, iso2);
    }

    private static String getCode(String iso3) {
        return ISO_639_2_to_ISO_639_1.get(iso3);
    }

    static Locale getLocaleFromISO639_2(String iso3) {
        if (iso3 == null) return null;
        String iso2 = ISO639.getCode(iso3);
        if (iso2 == null) return null;
        return new Locale.Builder().setLanguage(iso2).build();
    }
}
