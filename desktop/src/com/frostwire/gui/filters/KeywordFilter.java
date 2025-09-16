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

package com.frostwire.gui.filters;

import com.limegroup.gnutella.gui.search.UISearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A spam filter that removes certain "bad" keywords.
 * If <i>any</i> words in a query are in the banned set, the
 * query is disallowed.
 *
 * @author gubatron
 * @author aldenml
 */
public class KeywordFilter implements SearchFilter {
    /**
     * INVARIANT: strings in ban contain only lowercase
     */
    private final List<String> ban = new ArrayList<>();

    /**
     * @modifies this
     * @effects bans the given phrase.  Capitalization does not matter.
     */
    public void disallow(String phrase) {
        String canonical = phrase.toLowerCase(Locale.US);
        if (!ban.contains(canonical)) {
            ban.add(canonical);
        }
    }

    /**
     * @modifies this
     * @effects bans several well-known "adult" words.
     */
    public void disallowAdult() {
        disallow("adult");
        disallow("anal");
        disallow("anul");
        disallow("ass");
        disallow("bisex");
        disallow("boob");
        disallow("bukake");
        disallow("bukkake");
        disallow("blow");
        disallow("blowjob");
        disallow("bondage");
        disallow("centerfold");
        disallow("cock");
        disallow("cum");
        disallow("cunt");
        disallow("crack");
        disallow("cracked");
        disallow("dick");
        disallow("facial");
        disallow("fetish");
        disallow("fisting");
        disallow("fuck");
        disallow("gangbang");
        disallow("gay");
        disallow("hentai");
        disallow("horny");
        disallow("incest");
        disallow("jenna");
        disallow("masturbat");
        disallow("menage");
        disallow("milf");
        disallow("keygen");
        disallow("nipple");
        disallow("orgy");
        disallow("penis");
        disallow("playboy");
        disallow("porn");
        disallow("pedo");
        disallow("pussy");
        disallow("penetration");
        disallow("rape");
        disallow("sex");
        disallow("shaved");
        disallow("slut");
        disallow("slutty");
        disallow("squirt");
        disallow("stripper");
        disallow("suck");
        disallow("tittie");
        disallow("titty");
        disallow("trois");
        disallow("twat");
        disallow("vagina");
        disallow("whore");
        disallow("xxx");
        disallow("shaking orgasm");
        disallow("orgasm");
        disallow("teenfuns");
    }

    public boolean allow(UISearchResult m) {
        return !matches(m.getDisplayName());
    }

    /**
     * Returns true if phrase matches any of the entries in ban.
     */
    private boolean matches(String phrase) {
        String canonical = phrase.toLowerCase(Locale.US);
        for (String badWord : ban) {
            if (canonical.contains(badWord))
                return true;
        }
        return false;
    }
}
