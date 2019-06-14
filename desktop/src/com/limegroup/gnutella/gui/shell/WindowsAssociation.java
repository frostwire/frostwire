package com.limegroup.gnutella.gui.shell;

import java.io.IOException;

abstract class WindowsAssociation implements ShellAssociation {
    /**
     * Path to the native executable currently running
     */
    final String executable;

    WindowsAssociation(String executable) {
        this.executable = executable;
    }

    /**
     * Parses the path from a Windows Registry value.
     * <p>
     * Registry values look like this:
     *
     * <pre>
     * "C:\Program Files\Program\Program.exe" "%1"
     * C:\PROGRA~1\Program\Program.exe %1
     * </pre>
     * <p>
     * Additional information comes after the path.
     * If the path at the start contains spaces, it will be in quotes.
     * This method gets it either way.
     *
     * @param value A text value from the Windows Registry that contains a path
     * @return The path
     */
    static String parsePath(String value) {
        if ("".equals(value))
            return "";
        int begin, end;
        if (value.startsWith("\"")) {
            begin = 1;
            end = value.indexOf("\"", begin);
        } else {
            begin = 0;
            end = value.indexOf(" ");
        }
        if (end == -1)
            return value;
        return value.substring(begin, end);
    }

    public boolean isAvailable() {
        try {
            return "".equals(get());
        } catch (IOException readFailed) {
            return false;
        }
    }

    public boolean isRegistered() {
        try {
            return executable.equals(get());
        } catch (IOException readFailed) {
            return false;
        }
    }

    protected abstract String get() throws IOException;
}
