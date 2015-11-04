package com.limegroup.gnutella.util;

import java.io.IOException;

/**
 * Signals that an exception occurred during execution of a command.
 */
public class LaunchException extends IOException {
	
	/**
     * 
     */
    private static final long serialVersionUID = -3994751041116114570L;
    private final String[] command;

	/**
	 * @param cause the exception that occurred during execution of command
	 * @param command the executed command
	 */
	public LaunchException(IOException cause, String... command) {
		this.command = command;
		
		initCause(cause);
	}

	/**
	 * @param command the executed command
	 */
	public LaunchException(String... command) {
		this.command = command;
	}

	public String[] getCommand() {
		return command;
	}
	
}
