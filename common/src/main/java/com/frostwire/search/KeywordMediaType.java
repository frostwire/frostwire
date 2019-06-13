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

package com.frostwire.search;

import java.io.ObjectStreamException;
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
final class KeywordMediaType implements Serializable {
    // These values should match standard MIME content-type
    // categories and/or XSD schema names.
    public static final String SCHEMA_CUSTOM = "custom";
    public static final String SCHEMA_DOCUMENTS = "document";
    public static final String SCHEMA_PROGRAMS = "application";
    public static final String SCHEMA_AUDIO = "audio";
    public static final String SCHEMA_VIDEO = "video";
    public static final String SCHEMA_IMAGES = "image";
    public static final String SCHEMA_TORRENTS = "torrent"; //possibly magnet might be added in the future
    public static final String SCHEMA_OTHER = "other";
    /**
     * Type for 'torrents'
     */
    public static final KeywordMediaType TYPE_TORRENTS = new KeywordMediaType(6, SCHEMA_TORRENTS, new String[]{"torrent"});
    private static final long serialVersionUID = 3999062781289258389L;
    /**
     * Type for 'documents'
     */
    private static final KeywordMediaType TYPE_DOCUMENTS = new KeywordMediaType(1, SCHEMA_DOCUMENTS, new String[]{"html", "htm", "xhtml", "mht", "mhtml",
            "xml", "txt", "ans", "asc", "diz", "eml", "pdf", "ps", "eps", "epsf", "dvi", "rtf", "wri", "doc", "docx", "mcw", "wps", "xls", "wk1", "dif", "csv", "ppt", "tsv", "hlp", "chm", "lit",
            "tex", "texi", "latex", "info", "man", "wp", "wpd", "wp5", "wk3", "wk4", "shw", "sdd", "sdw", "sdp", "sdc", "sxd", "sxw", "sxp", "sxc", "abw", "kwd", "mobi", "azw", "aeh", "lrf", "lrx",
            "cbr", "cbz", "cb7", "chm", "dnl", "djvu", "epub", "pdb", "fb2", "xeb", "ceb", "prc", "pkg", "opf", "pdg", "pdb", "tr2", "tr3", "cbr", "cbz", "cb7", "cbt", "cba", "zip", "7z", "rar",
            "gzip", "tar", "gz", "cab", "msi", "ace", "sit", "dmg", "taz", "sh", "awk", "pl", "java", "py", "rb", "c", "cpp", "h", "hpp"});
    /**
     * Type for applications.
     */
    private static final KeywordMediaType TYPE_APPLICATIONS = new KeywordMediaType(
            2, SCHEMA_PROGRAMS, new String[]{"apk"});
    /**
     * Type for 'audio'
     */
    private static final KeywordMediaType TYPE_AUDIO = new KeywordMediaType(3, SCHEMA_AUDIO, new String[]{"mp3", "mpa", "mp1", "mpga", "mp2", "ra", "rm", "ram", "rmj", "wma", "wav", "m4a", "m4p", "lqt", "ogg", "med", "aif", "aiff", "aifc", "au", "snd", "s3m", "aud", "mid", "midi",
            "rmi", "mod", "kar", "ac3", "shn", "fla", "flac", "cda", "mka, aac"});
    /**
     * Type for 'video'
     */
    private static final KeywordMediaType TYPE_VIDEO = new KeywordMediaType(4, SCHEMA_VIDEO, new String[]{"mpg", "mpeg", "mpe", "mng", "mpv", "m1v", "vob", "mp2", "mpv2", "mp2v", "m2p", "m2v", "mpgv", "vcd", "mp4", "dv", "dvd", "div", "divx", "dvx", "smi", "smil", "rm", "ram", "rv",
            "rmm", "rmvb", "avi", "asf", "asx", "wmv", "qt", "mov", "fli", "flc", "flx", "flv", "wml", "vrml", "swf", "dcr", "jve", "nsv", "mkv", "ogm", "cdg", "srt", "sub", "idx", "webm", "3gp"});
    /**
     * Type for 'images'
     */
    private static final KeywordMediaType TYPE_PICTURES = new KeywordMediaType(5, SCHEMA_IMAGES, new String[]{"gif", "png", "jpg", "jpeg", "jpe", "jif", "jiff", "jfif", "tif", "tiff", "iff", "lbm", "ilbm", "eps", "mac", "drw", "pct", "img", "bmp", "dib", "rle", "ico", "ani", "icl",
            "cur", "emf", "wmf", "pcx", "pcd", "tga", "pic", "fig", "psd", "wpg", "dcx", "cpt", "mic", "pbm", "pnm", "ppm", "xbm", "xpm", "xwd", "sgi", "fax", "rgb", "ras"});
    /**
     * All media types.
     */
    private static final KeywordMediaType[] ALL_MEDIA_TYPES = new KeywordMediaType[]{
            TYPE_AUDIO, TYPE_DOCUMENTS, TYPE_PICTURES, TYPE_TORRENTS, TYPE_VIDEO,
            TYPE_APPLICATIONS};
    private final int id;
    /**
     * The description of this MediaType.
     */
    private final String schema;
    /**
     * The list of extensions within this MediaType.
     */
    private final Set<String> exts;
    /**
     * Whether or not this is one of the default media types.
     */
    private final boolean isDefault;

    /**
     * @param schema                   a MIME compliant non-localizable identifier,
     *                                 that matches file categories (and XSD schema names).
     * @param descriptionKeyResourceId a media identifier that can be used
     *                                 to retreive a localizable descriptive text.
     * @param extensions               a list of all file extensions of this
     *                                 type.  Must be all lowercase.  If null, this matches
     *                                 any file.
     */
    public KeywordMediaType(int id, String schema, String[] extensions) {
        if (schema == null) {
            throw new NullPointerException("schema must not be null");
        }
        this.id = id;
        this.schema = schema;
        this.isDefault = true;
        if (extensions == null) {
            this.exts = Collections.emptySet();
        } else {
            Set<String> set = new TreeSet<String>(new CaseInsensitiveStringComparator());
            set.addAll(Arrays.asList(extensions));
            this.exts = set;
        }
    }

    /**
     * Returns all default media types.
     */
    public static KeywordMediaType[] getDefaultMediaTypes() {
        return ALL_MEDIA_TYPES;
    }

    /**
     * Retrieves the media type for the specified schema's description.
     */
    public static KeywordMediaType getMediaTypeForSchema(String schema) {
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0; )
            if (schema.equals(ALL_MEDIA_TYPES[i].schema))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Retrieves the media type for the specified extension.
     */
    public static KeywordMediaType getMediaTypeForExtension(String ext) {
        if (ext == null) {
            return null;
        }
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0; )
            if (ALL_MEDIA_TYPES[i].exts.contains(ext))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Determines whether or not the specified schema is a default.
     */
    public static boolean isDefaultType(String schema) {
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0; )
            if (schema.equals(ALL_MEDIA_TYPES[i].schema))
                return true;
        return false;
    }

    /**
     * Retrieves the audio media type.
     */
    public static KeywordMediaType getAudioMediaType() {
        return TYPE_AUDIO;
    }

    /**
     * Retrieves the video media type.
     */
    public static KeywordMediaType getVideoMediaType() {
        return TYPE_VIDEO;
    }

    /**
     * Retrieves the image media type.
     */
    public static KeywordMediaType getImageMediaType() {
        return TYPE_PICTURES;
    }

    /**
     * Retrieves torrent type.
     */
    public static KeywordMediaType getTorrentMediaType() {
        return TYPE_TORRENTS;
    }

    /**
     * Retrieves the document media type.
     */
    public static KeywordMediaType getDocumentMediaType() {
        return TYPE_DOCUMENTS;
    }

    /**
     * Retrieves the programs media type.
     */
    public static KeywordMediaType getApplicationsMediaType() {
        return TYPE_APPLICATIONS;
    }

    public int getId() {
        return id;
    }

    /**
     * Returns true if a file with the given name is of this
     * media type, i.e., the suffix of the filename matches
     * one of this' extensions.
     */
    public boolean matches(String filename) {
        if (exts == null)
            return true;
        //Get suffix of filename.
        int j = filename.lastIndexOf(".");
        if (j == -1 || j == filename.length())
            return false;
        String suffix = filename.substring(j + 1);
        // Match with extensions.
        return exts.contains(suffix);
    }

    /**
     * Returns this' media-type (a MIME content-type category)
     * (previously returned a description key)
     */
    public String toString() {
        return schema;
    }

    /**
     * Returns the MIME-Type of this.
     */
    public String getMimeType() {
        return schema;
    }

    /**
     * Determines whether or not this is a default media type.
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Returns the extensions for this media type.
     */
    public Set<String> getExtensions() {
        return exts;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KeywordMediaType) {
            KeywordMediaType type = (KeywordMediaType) obj;
            return schema.equals(type.schema) && exts.equals(type.exts) && isDefault == type.isDefault;
        }
        return false;
    }

    public int hashCode() {
        return 31 * (1 + this.id) * (11 * getExtensions().hashCode());
    }

    /*
     * We canonicalize the default mediatypes, but since MediaType has
     * a public constructor only 'equals' comparisons should be used.
     */
    Object readResolve() throws ObjectStreamException {
        for (KeywordMediaType type : ALL_MEDIA_TYPES) {
            if (equals(type)) {
                return type;
            }
        }
        return this;
    }

    /**
     * Compares <code>String</code> objects without regard to case.
     */
    public static final class CaseInsensitiveStringComparator implements Comparator<String>, Serializable {
        private static final long serialVersionUID = 263123571237995212L;

        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }
}
