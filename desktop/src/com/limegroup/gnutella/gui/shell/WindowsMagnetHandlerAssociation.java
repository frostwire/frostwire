package com.limegroup.gnutella.gui.shell;

import org.limewire.util.SystemUtils;

import java.io.IOException;

public class WindowsMagnetHandlerAssociation extends WindowsAssociation {
    private static final String HKCU = "HKEY_CURRENT_USER";
    private static final String HANDLER = "SOFTWARE\\Magnet\\Handlers\\";
    private final String program;

    public WindowsMagnetHandlerAssociation(String program, String executable) {
        super(executable);
        this.program = program;
    }

    protected String get() throws IOException {
        return parsePath(SystemUtils.registryReadText(HKCU, HANDLER + program, "ShellExecute"));
    }

    public void register() {
        /*
         * Create the following Registry keys and values:
         *
         * Root                Key          Variable      Value
         * ------------------  -----------  ------------  ---------------------------------------------
         * HKEY_LOCAL_MACHINE  SOFTWARE
         *                      Magnet
         *                       Handlers
         *                        program                program
         *                                  DefaultIcon   executable,0
         *                                  Description   program
         *                                  kt            0
         *                                  ShellExecute  program "%URL"
         *                         Type
         *                                  urn:sha1      0
         */
        SystemUtils.registryWriteText(HKCU, HANDLER + program, "", program);
        SystemUtils.registryWriteText(HKCU, HANDLER + program, "DefaultIcon", "\"" + executable + "\",0");
        SystemUtils.registryWriteText(HKCU, HANDLER + program, "Description", program);
        SystemUtils.registryWriteNumber(HKCU, HANDLER + program, "kt", 0);
        SystemUtils.registryWriteText(HKCU, HANDLER + program, "ShellExecute", "\"" + executable + "\" \"%URL\"");
        SystemUtils.registryWriteNumber(HKCU, HANDLER + program + "\\Type", "urn:sha1", 0);
    }

    public void unregister() {
        SystemUtils.registryDelete(HKCU, HANDLER + program);
    }
}
