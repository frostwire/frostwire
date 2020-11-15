package com.limegroup.gnutella.gui.shell;

import com.frostwire.util.Logger;
import com.frostwire.util.filetypes.*;
import org.limewire.util.SystemUtils;

public class FileTypeAssociation implements ShellAssociation {
    private static final Logger LOG = Logger.getLogger(FileTypeAssociation.class);
    private static final AssociationService SERVICE = new AssociationService();
    private final String extension;
    private final String mimeType;
    private final String executable;
    private final String verb;
    private final Association association = new Association();

    public FileTypeAssociation(String extension,
                               String mimeType, String executable, String verb,
                               String description, String iconPath) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.executable = executable;
        this.verb = verb;
        Action action = new Action(verb, executable);
        association.addAction(action);
        association.addFileExtension(extension);
        association.setMimeType(mimeType);
        association.setName(description); // only used on unix
        association.setDescription(description);
        if (iconPath != null) // don't chance passing null to jdic
            association.setIconFileName(iconPath);
    }

    public boolean isAvailable() {
        try {
            // if no association at all, then it is available
            Association f = SERVICE.getFileExtensionAssociation(extension);
            if (f != null && f == SERVICE.getMimeTypeAssociation(mimeType))
                return true;
        } catch (IllegalArgumentException iae) {
            // SEE: LWC-1170
            // If JDIC bails on us, the registry might be a little confused...
            // so let's fix it by inserting ours.
            LOG.warn("Can't check availability!", iae);
            return true;
        }
        // still check for a default handler.
        String extHandler = SystemUtils.getDefaultExtensionHandler(extension);
        return ("".equals(extHandler) &&
                "".equals(SystemUtils.getDefaultMimeHandler(mimeType)));
    }

    public boolean isRegistered() {
        Association f;
        try {
            f = SERVICE.getFileExtensionAssociation(extension);
        } catch (IllegalArgumentException iae) {
            // SEE: LWC-1170
            LOG.warn("Can't check registration!", iae);
            return false;
        }
        if (f == null)
            return false;
        Action open = f.getActionByVerb(verb);
        if (open == null)
            return false;
        if (executable.equals(open.getCommand()))
            return true;
        return executable.equals(SystemUtils.getDefaultExtensionHandler(extension)) &&
                executable.equals(SystemUtils.getDefaultMimeHandler(mimeType));
    }

    public void register() {
        try {
            SERVICE.registerUserAssociation(association);
            SystemUtils.flushIconCache();
        } catch (AssociationAlreadyRegisteredException | RegisterFailedException ignore) {
            LOG.error("can't addRefreshListener", ignore);
        }
    }

    public void unregister() {
        try {
            forceUnregister(SERVICE.getFileExtensionAssociation(extension));
            forceUnregister(SERVICE.getMimeTypeAssociation(extension));
        } catch (IllegalArgumentException ignored) {
            //SEE: LWC-1170
            LOG.warn("Can't unregister!", ignored);
        }
    }

    private void forceUnregister(Association f) {
        if (f == null)
            return;
        try {
            SERVICE.unregisterUserAssociation(f);
            SystemUtils.flushIconCache();
        } catch (AssociationNotRegisteredException | RegisterFailedException ignore) {
            LOG.error("can't unregister", ignore);
        }
    }

    public String toString() {
        return extension + ":" + mimeType + ":" + executable + ":" + verb;
    }
}
