/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.torrentz2;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;
import org.apache.commons.io.FilenameUtils;

public final class Torrentz2SearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    Torrentz2SearchResult(String detailsUrl,
                          String infoHash,
                          String filename,
                          String fileSizeMagnitude,
                          String fileSizeUnit,
                          String ageString,
                          int seeds,
                          String trackers) {
        this.detailsUrl = detailsUrl;
        this.infoHash = infoHash;
        this.displayName = HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename));
        String extension = FilenameUtils.getExtension(filename);
        this.filename = filename + "." + (extension.isEmpty() ? "torrent" : extension);
        this.size = parseSize(fileSizeMagnitude + " " + fileSizeUnit);
        this.creationTime = parseCreationTime(ageString);
        this.seeds = seeds;
        this.torrentUrl = UrlUtils.buildMagnetUrl(infoHash, filename, trackers);
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return "Torrentz2";
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            String[] ds = dateString.split(" ");
            if (ds[1].contains("hours")) {
                try {
                    int hours = Integer.parseInt(ds[0]);
                    return result - (hours * 60 * 60 * 1000L);
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("years")) {
                try {
                    int years = Integer.parseInt(ds[0]);
                    return result - (years * 365L * 24L * 60L * 60L * 1000L); // a year in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("year")) {
                try {
                    return result - (365L * 24L * 60L * 60L * 1000L); // a year in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("months")) {
                try {
                    int months = Integer.parseInt(ds[0]);
                    return result - (months * 31L * 24L * 60L * 60L * 1000L); // a month in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (dateString.contains("1 month")) {
                return result - 31L * 24L * 60L * 60L * 1000L; // a month in milliseconds
            }
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return result;
    }
}
