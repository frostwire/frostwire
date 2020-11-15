package com.limegroup.gnutella.util;

import java.io.IOException;

/**
 * Thin wrapper class to execute a command. Stores the command, the arguments
 * and the executed process.
 */
public class LimeProcess {
    private final String[] command;
    private Process process;

    private LimeProcess(String[] command) {
        this.command = command;
    }

    /**
     * Executes the specified command and arguments in <tt>cmdarray</tt>.
     *
     * @param cmdarray command and arguments
     * @return a wrapper object for the spawned process
     * @throws SecurityException If execution of the command is not allowed
     * @throws LaunchException   If an {@link IOException} occurs
     * @see Runtime#exec(String[])
     */
    static LimeProcess exec(String[] cmdarray) throws SecurityException,
            LaunchException {
        LimeProcess p = new LimeProcess(cmdarray);
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
