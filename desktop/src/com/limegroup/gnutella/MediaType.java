package com.limegroup.gnutella;

import com.limegroup.gnutella.gui.I18n;
import org.limewire.collection.Comparators;
import org.limewire.util.FileUtils;

import java.io.Serializable;
import java.util.*;

/**
 * A generic type of media, i.e., "video" or "audio".
 * Many different file formats can be of the same media type.
 * MediaType's are immutable.
 * <p>
 * // See http://www.mrc-cbu.cam.ac.uk/Help/mimedefault.html
 * <p>
 * Implementation note: Since MediaType implements serialization and there
 * are inner anonymous classes be careful where to add new inner classes
 * and fields.
 */
public class MediaType implements Serializable {
    public static final String SCHEMA_DOCUMENTS = "document";
    public static final String SCHEMA_PROGRAMS = "application";
    public static final String SCHEMA_AUDIO = "audio";
    public static final String SCHEMA_VIDEO = "video";
    public static final String SCHEMA_IMAGES = "image";
    public static final String SCHEMA_TORRENTS = "torrent"; //possibly magnet might be added in the future
    // These values should match standard MIME content-type
    // categories and/or XSD schema names.
    private static final String SCHEMA_ANY_TYPE = "*";
    // These are used as resource keys to retreive descriptions in the GUI
    private static final String ANY_TYPE = I18n.tr("All Types");
    private static final long serialVersionUID = 3999062781289258389L;
    private static final String DOCUMENTS = I18n.tr("Books/Docs");
    private static final String PROGRAMS = I18n.tr("Programs");
    private static final String AUDIO = I18n.tr("Audio");
    private static final String VIDEO = I18n.tr("Video");
    private static final String IMAGES = I18n.tr("Images");
    private static final String TORRENTS = I18n.tr("Torrents");
    /**
     * Type for 'any file'
     */
    private static final MediaType TYPE_ANY =
            new MediaType(SCHEMA_ANY_TYPE, ANY_TYPE, null) {
                // required SVUID because we're constructing an anonymous class.
                // the id is taken from old limewire builds, versions 4.4 to 4.12
                private static final long serialVersionUID = 8621997774686329539L; //3728385699213635375L;

                public boolean matches(String ext) {
                    return true;
                }
            };
    /**
     * Type for 'documents'
     */
    private static final MediaType TYPE_DOCUMENTS =
            new MediaType(SCHEMA_DOCUMENTS, DOCUMENTS,
                    new String[]{
                            "html", "htm", "xhtml", "mht", "mhtml", "xml",
                            "txt", "ans", "asc", "diz", "eml",
                            "pdf", "ps", "eps", "epsf", "dvi",
                            "rtf", "wri", "doc", "mcw", "wps",
                            "xls", "wk1", "dif", "csv", "ppt", "tsv",
                            "hlp", "chm", "lit",
                            "tex", "texi", "latex", "info", "man",
                            "wp", "wpd", "wp5", "wk3", "wk4", "shw",
                            "sdd", "sdw", "sdp", "sdc",
                            "sxd", "sxw", "sxp", "sxc",
                            "abw", "kwd", "mobi", "azw", "aeh", "lrf", "lrx",
                            "cbr", "cbz", "cb7", "chm", "dnl", "djvu",
                            "epub", "pdb", "fb2", "xeb", "ceb", "prc", "pkg",
                            "opf", "pdg", "pdb", "tr2", "tr3", "cbr", "cbz", "cb7",
                            "cbt", "cba", "docx"
                    });
    /**
     * Type for linux/osx programs, used for Aggregator.
     */
    private static final MediaType TYPE_LINUX_OSX_PROGRAMS =
            new MediaType(SCHEMA_PROGRAMS, PROGRAMS,
                    new String[]{
                            "bin", "mdb", "sh", "csh", "awk", "pl",
                            "rpm", "deb", "gz", "gzip", "z", "bz2", "zoo", "tar", "tgz",
                            "taz", "shar", "hqx", "sit", "dmg", "7z", "jar", "zip", "nrg",
                            "cue", "iso", "jnlp", "rar", "sh", "apk"
                    });
    /**
     * Type for windows programs, used for Aggregator.
     */
    private static final MediaType TYPE_WINDOWS_PROGRAMS =
            new MediaType(SCHEMA_PROGRAMS, PROGRAMS,
                    new String[]{
                            "exe", "zip", "jar", "cab", "msi", "msp",
                            "arj", "rar", "ace", "lzh", "lha", "bin", "nrg", "cue",
                            "iso", "jnlp", "apk"
                    });
    /**
     * Type for 'programs'
     */
    private static final MediaType TYPE_PROGRAMS =
            new MediaType(SCHEMA_PROGRAMS, PROGRAMS,
                    makeArray(TYPE_LINUX_OSX_PROGRAMS.exts,
                            TYPE_WINDOWS_PROGRAMS.exts)
            );
    /**
     * Type for 'audio'
     */
    private static final MediaType TYPE_AUDIO =
            new MediaType(SCHEMA_AUDIO, AUDIO,
                    new String[]{
                            "mp3", "mpa", "mp1", "mpga", "mp2",
                            "ra", "rm", "ram", "rmj",
                            "wma", "wav", "m4a", "m4p",
                            "lqt", "ogg", "med",
                            "aif", "aiff", "aifc",
                            "au", "snd", "s3m", "aud",
                            "mid", "midi", "rmi", "mod", "kar",
                            "ac3", "shn", "fla", "flac", "cda",
                            "mka", "aac"
                    });
    /**
     * Type for 'video'
     */
    private static final MediaType TYPE_VIDEO =
            new MediaType(SCHEMA_VIDEO, VIDEO,
                    new String[]{
                            "mpg", "mpeg", "mpe", "mng", "mpv", "m1v",
                            "vob", "mp2", "mpv2", "mp2v", "m2p", "m2v", "mpgv",
                            "vcd", "mp4", "dv", "dvd", "div", "divx", "dvx",
                            "smi", "smil", "rm", "ram", "rv", "rmm", "rmvb",
                            "avi", "asf", "asx", "wmv", "qt", "mov",
                            "fli", "flc", "flx", "flv",
                            "wml", "vrml", "swf", "dcr", "jve", "nsv",
                            "mkv", "ogm",
                            "cdg", "srt", "sub", "idx",
                            "webm", "3gp", "m4v"
                    });
    /**
     * Type for 'images'
     */
    private static final MediaType TYPE_IMAGES =
            new MediaType(SCHEMA_IMAGES, IMAGES,
                    new String[]{
                            "gif", "png",
                            "jpg", "jpeg", "jpe", "jif", "jiff", "jfif",
                            "tif", "tiff", "iff", "lbm", "ilbm", "eps",
                            "mac", "drw", "pct", "img",
                            "bmp", "dib", "rle", "ico", "ani", "icl", "cur",
                            "emf", "wmf", "pcx",
                            "pcd", "tga", "pic", "fig",
                            "psd", "wpg", "dcx", "cpt", "mic",
                            "pbm", "pnm", "ppm", "xbm", "xpm", "xwd",
                            "sgi", "fax", "rgb", "ras"
                    });
    /**
     * Type for 'torrents'
     */
    private static final MediaType TYPE_TORRENTS =
            new MediaType(SCHEMA_TORRENTS, TORRENTS,
                    new String[]{
                            "torrent"
                    });
    /**
     * All media types.
     */
    private static final MediaType[] ALL_MEDIA_TYPES =
            new MediaType[]{TYPE_AUDIO, TYPE_DOCUMENTS, TYPE_IMAGES, TYPE_PROGRAMS, TYPE_TORRENTS, TYPE_VIDEO, TYPE_ANY};
    /**
     * The description of this MediaType.
     */
    private final String schema;
    /**
     * The key to look up this MediaType.
     */
    private final String descriptionKey;
    /**
     * The list of extensions within this MediaType.
     */
    private final Set<String> exts;
    private final String[] extsArray;

    /**
     * @param schema         a MIME compliant non-localizable identifier,
     *                       that matches file categories (and XSD schema names).
     * @param descriptionKey a media identifier that can be used
     *                       to retreive a localizable descriptive text.
     * @param extensions     a list of all file extensions of this
     *                       type.  Must be all lowercase.  If null, this matches
     *                       any file.
     */
    private MediaType(String schema, String descriptionKey,
                      String[] extensions) {
        if (schema == null) {
            throw new NullPointerException("schema must not be null");
        }
        this.schema = schema;
        this.descriptionKey = descriptionKey;
        if (extensions == null) {
            this.exts = Collections.emptySet();
            this.extsArray = new String[0];
        } else {
            Set<String> set =
                    new TreeSet<>(Comparators.caseInsensitiveStringComparator());
            set.addAll(Arrays.asList(extensions));
            this.exts = set;
            this.extsArray = exts.toArray(new String[0]);
        }
    }

    /**
     * Returns all default media types.
     */
    public static MediaType[] getDefaultMediaTypes() {
        return ALL_MEDIA_TYPES;
    }

    /**
     * Retrieves the media type for the specified schema's description.
     */
    public static MediaType getMediaTypeForSchema(String schema) {
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0; )
            if (schema.equals(ALL_MEDIA_TYPES[i].schema))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Retrieves the media type for the specified extension.
     */
    public static MediaType getMediaTypeForExtension(String ext) {
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0; )
            if (ALL_MEDIA_TYPES[i].exts.contains(ext))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Retrieves the any media type.
     */
    public static MediaType getAnyTypeMediaType() {
        return TYPE_ANY;
    }

    /**
     * Retrieves the audio media type.
     */
    public static MediaType getAudioMediaType() {
        return TYPE_AUDIO;
    }

    /**
     * Retrieves the video media type.
     */
    public static MediaType getVideoMediaType() {
        return TYPE_VIDEO;
    }

    /**
     * Retrieves the image media type.
     */
    public static MediaType getImageMediaType() {
        return TYPE_IMAGES;
    }

    /**
     * Retrieves torrent type.
     */
    public static MediaType getTorrentMediaType() {
        return TYPE_TORRENTS;
    }

    /**
     * Retrieves the document media type.
     */
    public static MediaType getDocumentMediaType() {
        return TYPE_DOCUMENTS;
    }

    /**
     * Retrieves the programs media type.
     */
    public static MediaType getProgramMediaType() {
        return TYPE_PROGRAMS;
    }

    /**
     * Utility that makes an array out of two sets.
     */
    private static String[] makeArray(Set<String> one, Set<String> two) {
        Set<String> all = new HashSet<>(one);
        all.addAll(two);
        return all.toArray(new String[0]);
    }

    /**
     * Returns true if a file with the given name is of this
     * media type, i.e., the suffix of the filename matches
     * one of this' extensions.
     */
    public boolean matches(String filename) {
        if (exts == null)
            return true;
        return FileUtils.hasExtension(filename, extsArray);
    }

    /**
     * Returns this' media-type (a MIME content-type category)
     * (previously returned a description key)
     */
    public String toString() {
        return schema;
    }

    /**
     * Returns this' description key in localizable resources
     * (now distinct from the result of the toString method)
     */
    public String getDescriptionKey() {
        return descriptionKey;
    }

    /**
     * Returns the MIME-Type of this.
     */
    public String getMimeType() {
        return schema;
    }

    /**
     * Returns the extensions for this media type.
     */
    public Set<String> getExtensions() {
        return exts;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MediaType) {
            MediaType type = (MediaType) obj;
            return schema.equals(type.schema)
                    && exts.equals(type.exts);
        }
        return false;
    }
}
