package com.limegroup.gnutella;

import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.util.URIUtils;
import com.limegroup.gnutella.util.URLDecoder;

import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Contains information fields extracted from a magnet link.
 */
public class MagnetOptions implements Serializable {
    private static final String MAGNET = "magnet:?";
    private static final String HTTP = "http://";
    private final Map<Option, List<String>> optionsMap;
    private transient String[] defaultURLs;

    private MagnetOptions(Map<Option, List<String>> options) {
        optionsMap = Collections.unmodifiableMap(options);
    }

    /**
     * Allows multiline parsing of magnet links.
     *
     * @param magnets
     * @return array may be empty, but is never <code>null</code>
     */
    public static MagnetOptions[] parseMagnets(String magnets) {
        List<MagnetOptions> list = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer
                (magnets, System.getProperty("line.separator"));
        while (tokens.hasMoreTokens()) {
            String next = tokens.nextToken();
            MagnetOptions[] options = MagnetOptions.parseMagnet(next);
            if (options.length > 0) {
                list.addAll(Arrays.asList(options));
            }
        }
        return list.toArray(new MagnetOptions[0]);
    }

    /**
     * Returns an empty array if the string could not be parsed.
     *
     * @param arg a string like "magnet:?xt.1=urn:sha1:49584DFD03&xt.2=urn:sha1:495345k"
     * @return array may be empty, but is never <code>null</code>
     */
    public static MagnetOptions[] parseMagnet(String arg) {
        Map<Integer, Map<Option, List<String>>> options = new HashMap<>();
        // Strip out any single quotes added to escape the string
        if (arg.startsWith("'"))
            arg = arg.substring(1);
        if (arg.endsWith("'"))
            arg = arg.substring(0, arg.length() - 1);
        // Parse query
        if (!arg.toLowerCase(Locale.US).startsWith(MagnetOptions.MAGNET))
            return new MagnetOptions[0];
        // Parse and assemble magnet options together.
        //
        arg = arg.substring(8);
        StringTokenizer st = new StringTokenizer(arg, "&");
        String keystr;
        String cmdstr;
        int start;
        int index;
        Integer iIndex;
        int periodLoc;
        // Process each key=value pair
        while (st.hasMoreTokens()) {
            Map<Option, List<String>> curOptions;
            keystr = st.nextToken();
            keystr = keystr.trim();
            start = keystr.indexOf("=") + 1;
            if (start == 0) continue; // no '=', ignore.
            cmdstr = keystr.substring(start);
            keystr = keystr.substring(0, start - 1);
            try {
                cmdstr = URLDecoder.decode(cmdstr);
            } catch (IOException e1) {
                continue;
            }
            // Process any numerical list of cmds
            if ((periodLoc = keystr.indexOf(".")) > 0) {
                try {
                    index = Integer.parseInt(keystr.substring(periodLoc + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
            } else {
                index = 0;
            }
            // Add to any existing options
            iIndex = index;
            curOptions = options.computeIfAbsent(iIndex, k -> new HashMap<>());
            Option option = Option.valueFor(keystr);
            if (option != null)
                addAppend(curOptions, option, cmdstr);
        }
        MagnetOptions[] ret = new MagnetOptions[options.size()];
        int i = 0;
        for (Map<Option, List<String>> current : options.values())
            ret[i++] = new MagnetOptions(current);
        return ret;
    }

    private static void addAppend(Map<Option, List<String>> map, Option key, String value) {
        List<String> l = map.computeIfAbsent(key, k -> new ArrayList<>(1));
        l.add(value);
    }

    public String toString() {
        return toExternalForm();
    }

    /**
     * Returns the magnet uri representation as it can be used in an html link.
     * <p>
     * Display name and keyword topic are url encoded.
     *
     * @return
     */
    private String toExternalForm() {
        StringBuilder ret = new StringBuilder(MAGNET);
        for (String xt : getExactTopics())
            ret.append("&xt=").append(xt);
        if (getDisplayName() != null)
            ret.append("&dn=").append(UrlUtils.encode(getDisplayName()));
        if (getKeywordTopic() != null)
            ret.append("&kt=").append(UrlUtils.encode(getKeywordTopic()));
        for (String xs : getXS())
            ret.append("&xs=").append(xs);
        for (String as : getAS())
            ret.append("&as=").append(as);
        for (String tr : getTR())
            ret.append("&tr=").append(tr);
        return ret.toString();
    }

    /**
     * Returns true if there are enough pieces of information to start a
     * download from it.
     * <p>At any rate there has to be at least one default url or a sha1 and
     * a non empty keyword topic/display name.
     *
     * @return
     */
    public boolean isDownloadable() {
        // Check if the XT is s BTIH hash.
        List<String> topics = getExactTopics();
        for (String xt : topics)
            if (xt.startsWith("urn:btih"))
                return true;
        return getDefaultURLs().length > 0;
    }

    /**
     * Returns true if only the keyword topic is specified.
     *
     * @return
     */
    public boolean isKeywordTopicOnly() {
        String kt = getKeywordTopic();
        String dn = getDisplayName();
        return kt != null && kt.length() > 0 &&
                (dn == null || dn.length() > 0) &&
                getAS().isEmpty() &&
                getXS().isEmpty() &&
                getExactTopics().isEmpty();
    }

    private List<String> getPotentialURLs() {
        List<String> urls = new ArrayList<>();
        urls.addAll(getPotentialURLs(getExactTopics()));
        urls.addAll(getPotentialURLs(getXS()));
        urls.addAll(getPotentialURLs(getAS()));
        return urls;
    }

    private List<String> getPotentialURLs(List<String> strings) {
        List<String> ret = new ArrayList<>();
        for (String str : strings) {
            if (str.toLowerCase(Locale.US).startsWith(HTTP))
                ret.add(str);
        }
        return ret;
    }

    /**
     * Returns all valid urls that can be tried for downloading.
     *
     * @return
     */
    private String[] getDefaultURLs() {
        if (defaultURLs == null) {
            List<String> urls = getPotentialURLs();
            for (Iterator<String> it = urls.iterator(); it.hasNext(); ) {
                try {
                    String nextURL = it.next();
                    URIUtils.toURI(nextURL);  // is it a valid URI?
                } catch (URISyntaxException e) {
                    it.remove(); // if not, remove it from the list.
                }
            }
            defaultURLs = urls.toArray(new String[0]);
        }
        return defaultURLs;
    }

    /**
     * Returns the display name, i.e. filename or <code>null</code>.
     *
     * @return
     */
    private String getDisplayName() {
        List<String> list = optionsMap.get(Option.DN);
        if (list == null || list.isEmpty())
            return null;
        else
            return list.get(0);
    }

    /**
     * Returns the keyword topic if there is one, otherwise <code>null</code>.
     *
     * @return
     */
    public String getKeywordTopic() {
        List<String> list = optionsMap.get(Option.KT);
        if (list == null || list.isEmpty())
            return null;
        else
            return list.get(0);
    }

    /**
     * Returns a list of exact topic strings, they can be url or urn string.
     *
     * @return
     */
    private List<String> getExactTopics() {
        return getList(Option.XT);
    }

    /**
     * Returns the list of exact source strings, they should be urls.
     *
     * @return
     */
    private List<String> getXS() {
        return getList(Option.XS);
    }

    /**
     * Returns the list of alternate source string, they should  be urls.
     *
     * @return
     */
    private List<String> getAS() {
        return getList(Option.AS);
    }

    private List<String> getTR() {
        return getList(Option.TR);
    }

    private List<String> getList(Option key) {
        List<String> l = optionsMap.get(key);
        if (l == null)
            return Collections.emptyList();
        else
            return l;
    }

    private enum Option {
        XS, XT, AS, DN, KT, TR;

        static Option valueFor(String str) {
            for (Option option : values()) {
                if (str.toUpperCase(Locale.US).startsWith(option.toString()))
                    return option;
            }
            return null;
        }
    }
}
