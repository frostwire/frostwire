/*
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

package com.frostwire.search.youtube;

import com.frostwire.logging.Logger;
import com.frostwire.search.FileSearchResult;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.search.youtube.jd.Browser;
import com.frostwire.search.youtube.jd.Encoding;
import com.frostwire.search.youtube.jd.Regex;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YouTubeExtractor {

    private static final Logger LOG = Logger.getLogger(YouTubeExtractor.class);

    private static final Pattern FILENAME_PATTERN = Pattern.compile("<meta name=\"title\" content=\"(.*?)\">", Pattern.CASE_INSENSITIVE);
    private static final String UNSUPPORTEDRTMP = "itag%2Crtmpe%2";

    private static final Map<Integer, Format> FORMATS = buildFormats();

    // using the signature decoding per running session (LruCache)
    private static LRUCacheMap<String, YouTubeSig> YT_SIG_MAP = new LRUCacheMap<String, YouTubeSig>(50);

    private YouTubeSig currentYTSig;

    public List<LinkInfo> extract(String videoUrl, boolean testConnection) {
        try {
            Thread.sleep(100);

            Browser br = new Browser();

            HashMap<Integer, String> LinksFound = getLinks(videoUrl, false, br);

            checkError(videoUrl, br, LinksFound);

            String filename = LinksFound.remove(-1);
            filename = cleanupFilename(filename);

            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH);
            String dateStr = br.getRegex("id=\"eow-date\" class=\"watch-video-date\" >(\\d{2}\\.\\d{2}\\.\\d{4})</span>").getMatch(0);
            if (dateStr == null) {
                formatter = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                dateStr = br.getRegex("class=\"watch-video-date\" >([ ]+)?(\\d{1,2} [A-Za-z]{3} \\d{4})</span>").getMatch(1);
            }
            Date date = dateStr != null ? formatter.parse(dateStr) : new Date();

            String videoId = getVideoID(videoUrl);
            String channelName = br.getRegex("feature=watch\"[^>]+dir=\"ltr[^>]+>(.*?)</a>(\\s+)?<span class=\"yt-user").getMatch(0);
            String userName = br.getRegex("temprop=\"url\" href=\"http://(www\\.)?youtube\\.com/user/([^<>\"]*?)\"").getMatch(1);

            ThumbnailLinks thumbnailLinks = createThumbnailLink(videoId);

            List<LinkInfo> infos = new LinkedList<LinkInfo>();

            //if (!testConnection || testConnection(br, getFirstLink(LinksFound))) {
                for (int fmt : LinksFound.keySet()) {
                    Format format = FORMATS.get(fmt);
                    if (format == null) {
                        continue;
                    }
                    String link = LinksFound.get(fmt);
                    LinkInfo info = new LinkInfo(link, fmt, filename, FileSearchResult.UNKNOWN_SIZE, date, videoId, userName, channelName, thumbnailLinks, format);
                    infos.add(info);
                }
            //}

            // work with the DASH manifest
            String dashmpd = br.getRegex("\"dashmpd\":\"([^\"]+)\"").getMatch(0);
            if (dashmpd != null && currentYTSig != null) {
                List<LinkInfo> dashInfos = extractLinksFromDashManifest(dashmpd, currentYTSig, filename, date, videoId, userName, channelName, thumbnailLinks);
                infos.addAll(dashInfos);
            }

            return infos;

        } catch (Throwable e) {
            throw new RuntimeException("General extractor error", e);
        }
    }

    private List<LinkInfo> extractLinksFromDashManifest(String dashManifestUrl, YouTubeSig ytSig, String filename,
                                                        Date date, String videoId, String userName, String channelName, ThumbnailLinks thumbnailLinks) throws IOException, ParserConfigurationException, SAXException {
        dashManifestUrl = dashManifestUrl.replace("\\/", "/");
        Pattern p = Pattern.compile("/s/([a-fA-F0-9\\.]+)/");
        Matcher m = p.matcher(dashManifestUrl);
        if (m.find()) {
            String sig = m.group(1);
            String signature = ytSig.calc(sig);

            dashManifestUrl = dashManifestUrl.replaceAll("/s/([a-fA-F0-9\\.]+)/", "/signature/" + signature + "/");
        } else if (dashManifestUrl.contains("/signature/")) {
            // dashManifestUrl as it is, empty block to review
        } else {
            return Collections.emptyList();
        }

        HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
        String dashDoc = httpClient.get(dashManifestUrl);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(dashDoc)));

        NodeList nodes = doc.getElementsByTagName("BaseURL");

        List<LinkInfo> infos = new ArrayList<LinkInfo>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node item = nodes.item(i);
            String url = item.getTextContent();
            int contentLength = -1;
            try {
                contentLength = Integer.parseInt(item.getAttributes().item(0).getTextContent());
            } catch (Throwable e) {
                // ignore
            }
            int fmt = Integer.parseInt(new Regex(url, "itag=(\\d+)").getMatch(0));

            Format format = FORMATS.get(fmt);
            if (format == null) {
                continue;
            }

            LinkInfo info = new LinkInfo(url, fmt, filename, contentLength, date, videoId, userName, channelName, thumbnailLinks, format);
            infos.add(info);
        }

        return infos;
    }

    private void checkError(String videoUrl, Browser br, HashMap<Integer, String> LinksFound) {

        String error = br.getRegex("<div id=\"unavailable\\-message\" class=\"\">[\t\n\r ]+<span class=\"yt\\-alert\\-vertical\\-trick\"></span>[\t\n\r ]+<div class=\"yt\\-alert\\-message\">([^<>\"]*?)</div>").getMatch(0);

        if (error == null) {
            error = br.getRegex("reason=([^<>\"/]*?)(\\&|$)").getMatch(0);
        }

        if (br.containsHTML(UNSUPPORTEDRTMP)) {
            error = "RTMP video download isn't supported yet!";
        }

        if ((LinksFound == null || LinksFound.isEmpty()) && error != null) {
            error = Encoding.urlDecode(error, false);
            if (error != null) {
                error = error.trim();
            }
            throw new RuntimeException("Reasig: " + error.trim());
        }
    }

    private HashMap<Integer, String> getLinks(final String video, final boolean prem, Browser br) throws Exception {

        br.setFollowRedirects(true);
        /* this cookie makes html5 available and skip controversy check */
        br.setCookie("youtube.com", "PREF", "f2=40100000&hl=en-US");
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_2) AppleWebKit/600.3.18 (KHTML, like Gecko) Version/8.0.3 Safari/600.3.18");
        br.getPage(video);

        if (br.containsHTML("id=\"unavailable-submessage\" class=\"watch-unavailable-submessage\"")) {
            return null;
        }

        String videoId = new Regex(video, "watch\\?v=([\\w_\\-]+)").getMatch(0);

        boolean fileNameFound = false;
        String filename = videoId;
        if (br.containsHTML("&title=")) {
            filename = Encoding.htmlDecode(br.getRegex("&title=([^&$]+)").getMatch(0).replaceAll("\\+", " ").trim());
            fileNameFound = true;
        }

        final String page = br.toString();
        final String prefix = "<script src=\"//s.ytimg.com/yts/jsbin/player-";
        final int startIndex = page.indexOf(prefix) + prefix.length();
        final int endIndex = page.indexOf("/base.js\" name=\"player/base\"></script>", startIndex); //don't search from the start
        final String playerId = page.substring(startIndex,
                                               endIndex);
        YouTubeSig ytSig = getYouTubeSig("http://s.ytimg.com/yts/jsbin/player-" + playerId + "/base.js");
        currentYTSig = ytSig;

        /* html5_fmt_map */
        if (br.getRegex(FILENAME_PATTERN).count() != 0 && fileNameFound == false) {
            filename = Encoding.htmlDecode(br.getRegex(FILENAME_PATTERN).getMatch(0).trim());
        }

        return parseLinks(br, video, filename, false, false, ytSig);
    }

    private HashMap<Integer, String> parseLinks(Browser br, final String videoURL, String filename, boolean ythack, boolean tryGetDetails, YouTubeSig ytSig) throws Exception {
        final HashMap<Integer, String> links = new HashMap<Integer, String>();
        String html5_fmt_map = br.getRegex("\"html5_fmt_map\": \\[(.*?)\\]").getMatch(0);

        if (html5_fmt_map != null) {
            String[] html5_hits = new Regex(html5_fmt_map, "\\{(.*?)\\}").getColumn(0);
            if (html5_hits != null) {
                for (String hit : html5_hits) {
                    String hitUrl = new Regex(hit, "url\": \"(http:.*?)\"").getMatch(0);
                    String hitFmt = new Regex(hit, "itag\": (\\d+)").getMatch(0);
                    if (hitUrl != null && hitFmt != null) {
                        hitUrl = unescape(hitUrl.replaceAll("\\\\/", "/"));
                        links.put(Integer.parseInt(hitFmt), Encoding.htmlDecode(Encoding.urlDecode(hitUrl, true)));
                    }
                }
            }
        } else {
            // New format since ca. 1.8.2011.
            html5_fmt_map = br.getRegex("\"url_encoded_fmt_stream_map\": \"(.*?)\"").getMatch(0);

            // New format since 1.27.2014, they removed a space.
            if (html5_fmt_map == null) {
                html5_fmt_map = br.getRegex("\"url_encoded_fmt_stream_map\":\"(.*?)\"").getMatch(0);
            }

            if (html5_fmt_map == null) {
                html5_fmt_map = br.getRegex("url_encoded_fmt_stream_map=(.*?)(&|$)").getMatch(0);

                if (html5_fmt_map != null) {
                    html5_fmt_map = html5_fmt_map.replaceAll("%2C", ",");
                    if (!html5_fmt_map.contains("url=")) {
                        html5_fmt_map = html5_fmt_map.replaceAll("%3D", "=");
                        html5_fmt_map = html5_fmt_map.replaceAll("%26", "&");
                    }
                }
            }
            if (html5_fmt_map != null && !html5_fmt_map.contains("signature") && !html5_fmt_map.contains("sig") && !html5_fmt_map.contains("s=")) {
                Thread.sleep(5000);
                br.clearCookies("youtube.com");
                return null;
            }
            if (html5_fmt_map != null) {
                HashMap<Integer, String> ret = parseLinks(html5_fmt_map, ytSig);
                if (ret.size() == 0)
                    return links;
                links.putAll(ret);
                if (true) {
                    /* not playable by vlc */
                    /* check for adaptive fmts */
                    String adaptive = br.getRegex("\"adaptive_fmts\": \"(.*?)\"").getMatch(0);
                    ret = parseLinks(adaptive, ytSig);
                    links.putAll(ret);
                }
            } else {
                if (br.containsHTML("reason=Unfortunately"))
                    return null;
                if (tryGetDetails == true) {
                    br.getPage("http://www.youtube.com/get_video_info?el=detailpage&video_id=" + getVideoID(videoURL));
                    return parseLinks(br, videoURL, filename, ythack, false, ytSig);
                } else {
                    return null;
                }
            }
        }

        /* normal links */
        final HashMap<String, String> fmt_list = new HashMap<String, String>();
        String fmt_list_str = "";
        if (ythack) {
            fmt_list_str = (br.getMatch("&fmt_list=(.+?)&") + ",").replaceAll("%2F", "/").replaceAll("%2C", ",");
        } else {
            fmt_list_str = (br.getMatch("\"fmt_list\":\\s+\"(.+?)\",") + ",").replaceAll("\\\\/", "/");
        }
        final String fmt_list_map[][] = new Regex(fmt_list_str, "(\\d+)/(\\d+x\\d+)/\\d+/\\d+/\\d+,").getMatches();
        for (final String[] fmt : fmt_list_map) {
            fmt_list.put(fmt[0], fmt[1]);
        }
        if (links.size() == 0 && ythack) {
            /* try to find fallback links */
            String urls[] = br.getRegex("url%3D(.*?)($|%2C)").getColumn(0);
            int index = 0;
            for (String vurl : urls) {
                String hitUrl = new Regex(vurl, "(.*?)%26").getMatch(0);
                String hitQ = new Regex(vurl, "%26quality%3D(.*?)%").getMatch(0);
                if (hitUrl != null && hitQ != null) {
                    hitUrl = unescape(hitUrl.replaceAll("\\\\/", "/"));
                    if (fmt_list_map.length >= index) {
                        links.put(Integer.parseInt(fmt_list_map[index][0]), Encoding.htmlDecode(Encoding.urlDecode(hitUrl, false)));
                        index++;
                    }
                }
            }
        }
        if (filename != null && links != null && !links.isEmpty()) {
            links.put(-1, filename);
        }

        return links;
    }

    private HashMap<Integer, String> parseLinks(String html5_fmt_map, YouTubeSig ytSig) {
        final HashMap<Integer, String> links = new HashMap<Integer, String>();
        if (html5_fmt_map != null) {
            if (html5_fmt_map.contains(UNSUPPORTEDRTMP)) {
                return links;
            }
            String[] html5_hits = new Regex(html5_fmt_map, "(.*?)(,|$)").getColumn(0);
            if (html5_hits != null) {
                for (String hit : html5_hits) {
                    hit = unescape(hit);
                    String hitUrl = new Regex(hit, "url=(http.*?)(\\&|$)").getMatch(0);
                    String sig = new Regex(hit, "url=http.*?(\\&|$)(sig|signature)=(.*?)(\\&|$)").getMatch(2);
                    if (sig == null)
                        sig = new Regex(hit, "(sig|signature)=(.*?)(\\&|$)").getMatch(1);
                    if (sig == null)
                        sig = new Regex(hit, "(sig|signature)%3D(.*?)%26").getMatch(1);
                    if (sig == null) {
                        String temp = new Regex(hit, "(\\&|^)s=(.*?)(\\&|$)").getMatch(1);
                        sig = ytSig != null && temp != null ? ytSig.calc(temp) : null;
                    }

                    String hitFmt = new Regex(hit, "itag=(\\d+)").getMatch(0);
                    if (hitUrl != null && hitFmt != null) {
                        hitUrl = unescape(hitUrl.replaceAll("\\\\/", "/"));
                        if (hitUrl.startsWith("http%253A")) {
                            hitUrl = Encoding.htmlDecode(hitUrl);
                        }
                        String inst = null;
                        if (hitUrl.contains("sig")) {
                            inst = Encoding.htmlDecode(Encoding.urlDecode(hitUrl, true));
                        } else {
                            inst = Encoding.htmlDecode(Encoding.urlDecode(hitUrl, true) + "&signature=" + sig);
                        }
                        links.put(Integer.parseInt(hitFmt), inst);
                    }
                }
            }
        }
        return links;
    }

    private String getVideoID(String URL) {
        String vuid = new Regex(URL, "v=([A-Za-z0-9\\-_]+)").getMatch(0);
        if (vuid == null) {
            vuid = new Regex(URL, "(v|embed)/([A-Za-z0-9\\-_]+)").getMatch(1);
        }
        return vuid;
    }

    private YouTubeSig getYouTubeSig(String html5playerUrl) {
        // concurrency issues are not important in this point
        YouTubeSig sig = null;
        if (!YT_SIG_MAP.containsKey(html5playerUrl)) {
            String jscode = "";
            try {
                html5playerUrl = html5playerUrl.replace("\\", "");
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                jscode = httpClient.get(html5playerUrl);
                sig = new YouTubeSig(jscode);
                YT_SIG_MAP.put(html5playerUrl, sig);
            } catch (Throwable t) {
                LOG.error("Could not getYouTubeSig", t);
                LOG.error("jscode:\n" + jscode);
            }
        } else {
            //cache hit, it worked with this url.            
            sig = YT_SIG_MAP.get(html5playerUrl);
        }

        return sig;
    }

    private ThumbnailLinks createThumbnailLink(String videoId) {
        String normal = "http://img.youtube.com/vi/" + videoId + "/default.jpg";
        String mq = "http://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
        String hq = "http://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
        String maxres = "http://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";

        return new ThumbnailLinks(normal, mq, hq, maxres);
    }

    private String unescape(final String s) {
        char ch;
        char ch2;
        final StringBuilder sb = new StringBuilder();
        int ii;
        int i;
        for (i = 0; i < s.length(); i++) {
            ch = s.charAt(i);
            switch (ch) {
                case '%':
                case '\\':
                    ch2 = ch;
                    ch = s.charAt(++i);
                    StringBuilder sb2 = null;
                    switch (ch) {
                        case 'u':
                    /* unicode */
                            sb2 = new StringBuilder();
                            i++;
                            ii = i + 4;
                            for (; i < ii; i++) {
                                ch = s.charAt(i);
                                if (sb2.length() > 0 || ch != '0') {
                                    sb2.append(ch);
                                }
                            }
                            i--;
                            sb.append((char) Long.parseLong(sb2.toString(), 16));
                            continue;
                        case 'x':
                    /* normal hex coding */
                            sb2 = new StringBuilder();
                            i++;
                            ii = i + 2;
                            for (; i < ii; i++) {
                                ch = s.charAt(i);
                                sb2.append(ch);
                            }
                            i--;
                            sb.append((char) Long.parseLong(sb2.toString(), 16));
                            continue;
                        default:
                            if (ch2 == '%') {
                                sb.append(ch2);
                            }
                            sb.append(ch);
                            continue;
                    }

            }
            sb.append(ch);
        }

        return sb.toString();
    }

    private String cleanupFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|\\[\\];,]+", "_");
    }

    private void log(String message) {
        LOG.info(message);
    }

    private static Map<Integer, Format> buildFormats() {

        Map<Integer, Format> formats = new HashMap<Integer, Format>();

        formats.put(5, new Format("flv", "H263", "MP3", "240p"));
        formats.put(6, new Format("flv", "H263", "MP3", "270p"));
        formats.put(17, new Format("3gp", "H264", "AAC", "144p"));
        formats.put(18, new Format("mp4", "H264", "AAC", "360p"));
        formats.put(22, new Format("mp4", "H264", "AAC", "720p"));
        formats.put(34, new Format("flv", "H264", "AAC", "360p"));
        formats.put(35, new Format("flv", "H264", "AAC", "480p"));
        formats.put(36, new Format("3gp", "H264", "AAC", "240p"));
        formats.put(37, new Format("mp4", "H264", "AAC", "1080p"));
        formats.put(38, new Format("mp4", "H264", "AAC", "3072p"));
        formats.put(43, new Format("webm", "VP8", "Vorbis", "360p"));
        formats.put(44, new Format("webm", "VP8", "Vorbis", "480p"));
        formats.put(45, new Format("webm", "VP8", "Vorbis", "720p"));
        formats.put(46, new Format("webm", "VP8", "Vorbis", "1080p"));
        formats.put(59, new Format("mp4", "H264", "AAC", "480p"));
        formats.put(78, new Format("mp4", "H264", "AAC", "480p"));
        formats.put(82, new Format("mp4", "H264", "AAC", "360p"));
        formats.put(83, new Format("mp4", "H264", "AAC", "240p"));
        formats.put(84, new Format("mp4", "H264", "AAC", "720p"));
        formats.put(85, new Format("mp4", "H264", "AAC", "520p"));
        formats.put(100, new Format("webm", "VP8", "Vorbis", "360p"));
        formats.put(101, new Format("webm", "VP8", "Vorbis", "360p"));
        formats.put(102, new Format("webm", "VP8", "Vorbis", "720p"));
        // dash video
        formats.put(133, new Format("m4v", "H264", "", "240p"));
        formats.put(134, new Format("m4v", "H264", "", "360p"));
        formats.put(135, new Format("m4v", "H264", "", "480p"));
        formats.put(136, new Format("m4v", "H264", "", "720p"));
        formats.put(137, new Format("m4v", "H264", "", "1080p"));
        // dash audio
        formats.put(139, new Format("m4a", "", "AAC", "48k"));
        formats.put(140, new Format("m4a", "", "AAC", "128k"));
        formats.put(141, new Format("m4a", "", "AAC", "256k"));

        return formats;
    }

    public static final class LinkInfo {

        private LinkInfo(String link, int fmt, String filename, long size, Date date, String videoId, String user, String channel, ThumbnailLinks thumbnails, Format format) {
            this.link = link;
            this.fmt = fmt;
            this.filename = filename;
            this.size = size;
            this.date = date;
            this.videoId = videoId;
            this.user = user;
            this.channel = channel;
            this.thumbnails = thumbnails;
            this.format = format;
        }

        public final String link;
        public final int fmt;
        public final String filename;
        public final long size;
        public final Date date;
        public final String videoId;
        public final String user;
        public final String channel;
        public final ThumbnailLinks thumbnails;
        public final Format format;
    }

    public static final class ThumbnailLinks {

        private ThumbnailLinks(String normal, String mq, String hq, String maxres) {
            this.normal = normal;
            this.mq = mq;
            this.hq = hq;
            this.maxres = maxres;
        }

        public final String normal;
        public final String mq;
        public final String hq;
        public final String maxres;
    }

    public static final class Format {

        private Format(String ext, String video, String audio, String quality) {
            this.ext = ext;
            this.video = video;
            this.audio = audio;
            this.quality = quality;
        }

        public final String ext;
        public final String video;
        public final String audio;
        public final String quality;
    }

    private static class LRUCacheMap<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;

        public LRUCacheMap(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }
}
