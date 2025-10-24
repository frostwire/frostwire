package com.limegroup.gnutella.util;

import java.io.IOException;

/**
 * Thin wrapper class to execute a command. Stores the command, the arguments
 * and the executed process.
 */
public class FWProcess {
    private final String[] command;
    private Process process;

    private FWProcess(String[] command) {
        this.command = command;
    }

    /**
     * Executes the specified command and arguments in `cmdarray`.
     *
     * @param cmdarray command and arguments
     * @return a wrapper object for the spawned process
     * @throws SecurityException If execution of the command is not allowed
     * @throws LaunchException   If an {@link IOException} occurs
     * @see Runtime#exec(String[])
     */
    static FWProcess exec(String[] cmdarray) throws SecurityException,
            LaunchException {
        FWProcess p = new FWProcess(cmdarray);
        p.exec();
        return p;
    }

    private void exec() throws SecurityException, LaunchException {
        try {
            process = Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            throw new LaunchException(e, command);
        }
    }

    /**
     * Returns the command and arguments.
     */
    public String[] getCommand() {
        return command;
    }

    /**
     * Returns the process.
     */
    public Process getProcess() {
        return process;
    }
}
