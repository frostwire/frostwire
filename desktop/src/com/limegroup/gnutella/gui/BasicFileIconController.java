package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.search.NamedMediaType;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.io.File;

/**
 * A FileIconController based off NamedMediaType's default icons.
 */
public class BasicFileIconController implements FileIconController {
    /**
     * Returns the icon associated with the extension of the file.
     */
    public Icon getIconForFile(File f) {
        if (f == null)
            return null;
        String extension = FilenameUtils.getExtension(f.getName());
        if (extension != null)
            return getIconForExtension(extension);
        else
            return null;
    }

    /**
     * Returns the icon assocated with the extension.
     */
    public Icon getIconForExtension(String ext) {
        NamedMediaType nmt = null;
        if (ext != null)
            nmt = NamedMediaType.getFromExtension(ext);
        if (nmt == null)
            nmt = NamedMediaType.getFromDescription("*"); // any type
        return nmt.getIcon();
    }

    /**
     * Icons are always available immediately.
     */
    public boolean isIconForFileAvailable(File f) {
        return true;
    }

    /**
     * This basic controller is always valid.
     */
    public boolean isValid() {
        return true;
    }
}
