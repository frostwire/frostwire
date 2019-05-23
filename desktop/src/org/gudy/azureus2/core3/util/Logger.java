/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */
package org.gudy.azureus2.core3.util;

import java.io.PrintStream;

/**
 * A static implementation of the LoggerImpl class.
 * 
 * @note Currently, LoggerImpl and Logger could be combined, but they are split
 *        for future consideration (ie. allowing multiple LoggerImpl) 
 * 
 * @author TuxPaper
 * @since 2.3.0.7
 */
class Logger {
	private static final LogIDs LOGID = LogIDs.LOGGER;

	private static final LoggerImpl loggerImpl = new LoggerImpl();


	static {
		try {
			loggerImpl.init();

			if (loggerImpl.isEnabled()) {
				log(new LogEvent(LOGID, "**** Logging starts: " +
						Constants.APP_NAME + " " + Constants.AZUREUS_VERSION + " ****"));

				log(new LogEvent(LOGID, "java.home=" + System.getProperty("java.home")));

				log(new LogEvent(LOGID, "java.version="
					+Constants.JAVA_VERSION));

				log(new LogEvent(LOGID, "os=" + System.getProperty("os.arch") + "/"
					+ System.getProperty("os.name") + "/"
					+ System.getProperty("os.version")));

				log(new LogEvent(LOGID, "user.dir=" + System.getProperty("user.dir")));

				log(new LogEvent(LOGID, "user.home=" + System.getProperty("user.home")));
			}
		} catch (Throwable t) {
			t.printStackTrace();
			Debug.out("Error initializing Logger", t);
			// loggerImpl will always be set, except for cases where there wasn't
			// enough memory. In that case, app will block with null pointer exception
			// on first Logger.* call.  However, since there's not enough memory,
			// application will probably block somewhere else in the code first.
		}
	}

	/**
	 * Determines whether events are logged
	 * 
	 * @return true if events are logged
	 */
	public static boolean isEnabled() {
		return loggerImpl.isEnabled();
	}

	/**
	 * Log an event
	 * 
	 * @param event
	 *            event to log
	 */
	public static void log(LogEvent event) {
		loggerImpl.log(event);
	}

	public static void log(LogAlert alert) {
		loggerImpl.log(alert);
	}

	/**
	 * Retrieve the original stderr output before we hooked it.  Handy for
	 * printing out critical errors that need to bypass the logger capture.
	 * 
	 * @return stderr
	 */
	static PrintStream getOldStdErr() {
		return loggerImpl.getOldStdErr();
	}

}
