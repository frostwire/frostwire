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

package com.frostwire.android.core;

import com.frostwire.android.R;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * A generic type of media, i.e., "video" or "audio".
 * Many different file formats can be of the same media type.
 * MediaType's are immutable.
 *
 * // See http://www.mrc-cbu.cam.ac.uk/Help/mimedefault.html
 * 
 * Implementation note: Since MediaType implements serialization and there
 * are inner anonymous classes be careful where to add new inner classes
 * and fields.
 */
public class MediaType implements Serializable {

    private static final long serialVersionUID = 3999062781289258389L;

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

    // These are used as resource keys to retrieve descriptions in the GUI 
    public static final int DOCUMENTS_STRING_RESOURCE_ID = R.string.media_type_documents;
    public static final int APPLICATIONS_STRING_RESOURCE_ID = R.string.media_type_applications;
    public static final int AUDIO_STRING_RESOURCE_ID = R.string.media_type_audio;
    public static final int VIDEO_STRING_RESOURCE_ID = R.string.media_type_video;
    public static final int IMAGES_STRING_RESOURCE_ID = R.string.media_type_images;
    public static final int TORRENTS_STRING_RESOURCE_ID = R.string.media_type_torrents;

    /**
     * Type for 'documents'
     */
    private static final MediaType TYPE_DOCUMENTS = new MediaType(Constants.FILE_TYPE_DOCUMENTS, SCHEMA_DOCUMENTS, DOCUMENTS_STRING_RESOURCE_ID, new String[] { "html", "htm", "xhtml", "mht", "mhtml",
            "xml", "txt", "ans", "asc", "diz", "eml", "pdf", "ps", "eps", "epsf", "dvi", "rtf", "wri", "doc", "docx", "mcw", "wps", "xls", "wk1", "dif", "csv", "ppt", "tsv", "hlp", "chm", "lit",
            "tex", "texi", "latex", "info", "man", "wp", "wpd", "wp5", "wk3", "wk4", "shw", "sdd", "sdw", "sdp", "sdc", "sxd", "sxw", "sxp", "sxc", "abw", "kwd", "mobi", "azw", "aeh", "lrf", "lrx",
            "cbr", "cbz", "cb7", "chm", "dnl", "djvu", "epub", "pdb", "fb2", "xeb", "ceb", "prc", "pkg", "opf", "pdg", "pdb", "tr2", "tr3", "cbr", "cbz", "cb7", "cbt", "cba", "zip", "7z", "rar",
            "gzip", "tar", "gz", "cab", "msi", "ace", "sit", "dmg", "taz", "sh", "awk", "pl", "java", "py", "rb", "c", "cpp", "h", "hpp" });

    /**
     * Type for applications.
     */
    private static final MediaType TYPE_APPLICATIONS = new MediaType(
            Constants.FILE_TYPE_APPLICATIONS, SCHEMA_PROGRAMS,
            APPLICATIONS_STRING_RESOURCE_ID, new String[] {"apk"});
    /**
     * desktop world
     "zip", "jar", "cab", "msi", "msp", "arj", "rar", "ace",
    "lzh", "lha", "bin", "nrg", "cue", "iso", "jnlp", "bin",
    "mdb", "sh", "csh", "awk", "pl", "rpm", "deb", "gz",
    "gzip", "z", "bz2", "zoo", "tar", "tgz", "taz", "shar",
    "hqx", "sit", "dmg", "7z", "jar", "zip"

     */

    /**
     * Type for 'audio'
     */
    private static final MediaType TYPE_AUDIO = new MediaType(Constants.FILE_TYPE_AUDIO, SCHEMA_AUDIO, AUDIO_STRING_RESOURCE_ID, new String[] { "mp3", "mpa", "mp1", "mpga", "mp2", "ra", "rm", "ram", "rmj", "wma", "wav", "m4a", "m4p", "lqt", "ogg", "med", "aif", "aiff", "aifc", "au", "snd", "s3m", "aud", "mid", "midi",
            "rmi", "mod", "kar", "ac3", "shn", "fla", "flac", "cda", "mka, aac" });

    /**
     * Type for 'video'
     */
    private static final MediaType TYPE_VIDEO = new MediaType(Constants.FILE_TYPE_VIDEOS, SCHEMA_VIDEO, VIDEO_STRING_RESOURCE_ID, new String[] { "mpg", "mpeg", "mpe", "mng", "mpv", "m1v", "vob", "mp2", "mpv2", "mp2v", "m2p", "m2v", "mpgv", "vcd", "mp4", "dv", "dvd", "div", "divx", "dvx", "smi", "smil", "rm", "ram", "rv",
            "rmm", "rmvb", "avi", "asf", "asx", "wmv", "qt", "mov", "fli", "flc", "flx", "flv", "wml", "vrml", "swf", "dcr", "jve", "nsv", "mkv", "ogm", "cdg", "srt", "sub", "idx", "webm", "3gp" });

    /**
     * Type for 'images'
     */
    private static final MediaType TYPE_PICTURES = new MediaType(Constants.FILE_TYPE_PICTURES, SCHEMA_IMAGES, IMAGES_STRING_RESOURCE_ID, new String[] { "gif", "png", "jpg", "jpeg", "jpe", "jif", "jiff", "jfif", "tif", "tiff", "iff", "lbm", "ilbm", "eps", "mac", "drw", "pct", "img", "bmp", "dib", "rle", "ico", "ani", "icl",
            "cur", "emf", "wmf", "pcx", "pcd", "tga", "pic", "fig", "psd", "wpg", "dcx", "cpt", "mic", "pbm", "pnm", "ppm", "xbm", "xpm", "xwd", "sgi", "fax", "rgb", "ras" });

    /**
     * Type for 'torrents'
     */
    public static final MediaType TYPE_TORRENTS = new MediaType(Constants.FILE_TYPE_TORRENTS, SCHEMA_TORRENTS, TORRENTS_STRING_RESOURCE_ID, new String[] { "torrent" });

    /**
     * All media types.
     */
    private static final MediaType[] ALL_MEDIA_TYPES = new MediaType[] {
            TYPE_AUDIO, TYPE_DOCUMENTS, TYPE_PICTURES, TYPE_TORRENTS, TYPE_VIDEO,
            TYPE_APPLICATIONS};

    private final int id;

    /**
     * The description of this MediaType.
     */
    private final String schema;

    /**
     * The key to look up this MediaType.
     */
    private final int descriptionKeyResourceId;

    /**
     * The list of extensions within this MediaType.
     */
    private final Set<String> exts;

    /**
     * Whether or not this is one of the default media types.
     */
    private final boolean isDefault;

    /**
     * @param schema a MIME compliant non-localizable identifier,
     *  that matches file categories (and XSD schema names).
     * @param descriptionKeyResourceId a media identifier that can be used
     *  to retreive a localizable descriptive text.
     * @param extensions a list of all file extensions of this
     *  type.  Must be all lowercase.  If null, this matches
     *  any file.
     */
    public MediaType(int id, String schema, int descriptionKeyResourceId, String[] extensions) {
        if (schema == null) {
            throw new NullPointerException("schema must not be null");
        }
        this.id = id;
        this.schema = schema;
        this.descriptionKeyResourceId = descriptionKeyResourceId;
        this.isDefault = true;
        if (extensions == null) {
            this.exts = Collections.emptySet();
        } else {
            Set<String> set = new TreeSet<>(new CaseInsensitiveStringComparator());
            set.addAll(Arrays.asList(extensions));
            this.exts = set;
        }
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
     * Returns this' description key in localizable resources
     * (now distinct from the result of the toString method)
     */
    public int getDescriptionKeyResourceId() {
        return descriptionKeyResourceId;
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
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0;)
            if (schema.equals(ALL_MEDIA_TYPES[i].schema))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Retrieves the media type for the specified extension.
     */
    public static MediaType getMediaTypeForExtension(String ext) {
        if (ext == null) {
            return null;
        }
        
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0;)
            if (ALL_MEDIA_TYPES[i].exts.contains(ext))
                return ALL_MEDIA_TYPES[i];
        return null;
    }

    /**
     * Determines whether or not the specified schema is a default.
     */
    public static boolean isDefaultType(String schema) {
        for (int i = ALL_MEDIA_TYPES.length; --i >= 0;)
            if (schema.equals(ALL_MEDIA_TYPES[i].schema))
                return true;
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MediaType) {
            MediaType type = (MediaType) obj;
            return schema.equals(type.schema) && exts.equals(type.exts) && isDefault == type.isDefault;
        }
        return false;
    }
    
    public int hashCode() {
        return 31 * (1+this.id) * (11*getExtensions().hashCode()); 
    }

    /*
     * We canonicalize the default mediatypes, but since MediaType has
     * a public constructor only 'equals' comparisons should be used.
     */
    Object readResolve() throws ObjectStreamException {
        for (MediaType type : ALL_MEDIA_TYPES) {
            if (equals(type)) {
                return type;
            }
        }
        return this;
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
        return TYPE_PICTURES;
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
    public static MediaType getApplicationsMediaType() {
        return TYPE_APPLICATIONS;
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

    public static int getFileTypeIconId(String ext) {
        MediaType mt = MediaType.getMediaTypeForExtension(ext);
        if (mt == null) {
            return R.drawable.question_mark;
        }
        if (mt.equals(MediaType.getApplicationsMediaType())) {
            return R.drawable.my_files_application_icon_selector_menu;
        } else if (mt.equals(MediaType.getAudioMediaType())) {
            return R.drawable.my_files_audio_icon_selector_menu;
        } else if (mt.equals(MediaType.getDocumentMediaType())) {
            return R.drawable.my_files_document_icon_selector_menu;
        } else if (mt.equals(MediaType.getImageMediaType())) {
            return R.drawable.my_files_picture_icon_selector_menu;
        } else if (mt.equals(MediaType.getVideoMediaType())) {
            return R.drawable.my_files_video_icon_selector_menu;
        } else if (mt.equals(MediaType.getTorrentMediaType())) {
            return R.drawable.my_files_torrent_icon_selector_menu;
        } else {
            return R.drawable.question_mark;
        }
    }
}
