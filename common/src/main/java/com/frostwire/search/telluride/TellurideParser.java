/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.telluride;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;

public final class TellurideParser {
    final static Logger LOG = Logger.getLogger(TellurideParser.class);
    final static String DECIMAL_GROUP_FORMAT = "(?<%s>\\d{1,3}\\.\\d{1,3})";
    final static String ETA_GROUP = "(?<eta>\\d{1,3}\\:\\d{1,3})";
    // [download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29
    final static String REGEX_PROGRESS = "(?is)" +
            String.format(DECIMAL_GROUP_FORMAT, "percentage") +
            "\\% of .*?" +
            String.format(DECIMAL_GROUP_FORMAT, "size") +
            "(?<unitSize>[KMGTP]iB) at.*?" +
            String.format(DECIMAL_GROUP_FORMAT, "rate") +
            "(?<unitRate>[KMGTP]iB/s) ETA " + ETA_GROUP;
    final static Pattern downloadPattern = Pattern.compile(REGEX_PROGRESS);

    final TellurideListener processListener;
    final boolean metaOnly;
    final StringBuilder sb;

    TellurideParser(TellurideListener listener, boolean pMetaOnly) {
        processListener = listener;
        metaOnly = pMetaOnly;
        sb = new StringBuilder();
    }

    public void parse(String line) {
        if (line.contains("ERROR:")) {
            processListener.onError(line);
            return;
        }
        if (metaOnly) {
            sb.append(line);
        } else if (!line.isEmpty()) {
            // reports on the file name we're about to download - onDestination
            if (line.startsWith("[download] Destination: ")) {
                processListener.onDestination(line.substring("[download] Destination: ".length()));
                return;
            }

            // reports on progress
            Matcher progressMatcher = downloadPattern.matcher(line);
            if (progressMatcher.find()) {
                String percentage = progressMatcher.group("percentage");
                String size = progressMatcher.group("size");
                String unitSize = progressMatcher.group("unitSize");
                String rate = progressMatcher.group("rate");
                String unitRate = progressMatcher.group("unitRate");
                String eta = progressMatcher.group("eta");

                processListener.onProgress(
                        Float.parseFloat(percentage),
                        Float.parseFloat(size),
                        unitSize,
                        Float.parseFloat(rate),
                        // hardcoded for now, we should parse this, we'll see how it integrates with SearchResults
                        unitRate,
                        eta
                );
                return;
            }

            // reports on errors
        }
    }

    public void done() {
        if (metaOnly) {
            String JSON = sb.toString();
            if (JSON != null && JSON.length() > 0) {
                // try catch here and report error
                try {
                    processListener.onMeta(JSON.substring(JSON.indexOf("{")));
                } catch (Throwable t) {
                    LOG.error("TellurideParser.done(JSON=\"" + JSON + "\") error", t);
                    processListener.onError(t.getMessage());
                }
            } else {
                processListener.onError("No metadata returned by telluride");
            }
        }
    }
}
