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

import java.util.ArrayList;

/**
 * @author TuxPaper
 */
class LogAlert /*implements org.gudy.azureus2.plugins.logging.LogAlert*/ {

	/**
	 * Log Type: Information
	 */
	private static final int LT_INFORMATION = 1;

	/**
	 * Log Type: Warning
	 */
	private static final int LT_WARNING = 2;

	/**
	 * Log Type: Error
	 */
	private static final int LT_ERROR = 3;

	// log types
	private static final int AT_INFORMATION = LogEvent.LT_INFORMATION;

	private static final int AT_WARNING = LogEvent.LT_WARNING;

	private static final int AT_ERROR = LogEvent.LT_ERROR;

	public static final boolean REPEATABLE = true;

	public static final boolean UNREPEATABLE = false;

	public final int entryType;

	public Throwable err = null;

	private final boolean repeatable;

	public String text;

	/** A list of events that this entry is related to */
	private Object[] relatedTo;

		// -1 -> default
		private int	timeoutSecs	= -1;
	
	public String details;
	
	public boolean forceNotify;
	
	/**
	 * @param type
	 * @param text
	 * @param repeatable
	 */
	private LogAlert(boolean repeatable, int type, String text) {
		entryType = type;
		this.text = text;
		this.repeatable = repeatable;
	}

	/**
	 * @param type
	 * @param text
	 * @param repeatable
	 * @param timeoutSecs  -1 -> use defaults 0 -> no timeout
	 */
	public LogAlert(boolean repeatable, int type, String text, int timeoutSecs) {
		entryType = type;
		this.text = text;
		this.repeatable = repeatable;
		this.timeoutSecs = timeoutSecs;
	}
	
	public LogAlert(Object[] relatedTo, boolean repeatable, int type, String text) {
		this(repeatable, type, text);
		this.relatedTo = relatedTo;
	}

	public LogAlert(Object relatedTo, boolean repeatable, int type, String text) {
		this(repeatable, type, text);
		this.relatedTo = new Object[] { relatedTo };
	}

	private LogAlert(boolean repeatable, String text, Throwable err) {
		this(repeatable, AT_ERROR, text);
		this.err = err;
	}
	
	public LogAlert(boolean repeatable, int type, String text, Throwable err) {
		this(repeatable, type, text);
		this.err = err;
	}

	/**
     */
	public LogAlert(Object relatedTo, boolean repeatable,
			String text, Throwable err) {
		this(repeatable, text, err);
		this.relatedTo = new Object[] { relatedTo };
	}
	
	// Plugin methods.
	public int getGivenTimeoutSecs() {return timeoutSecs;}
	public String getText() {return text;}
	public Throwable getError() {return err;}
	public int getType() {
		switch (entryType) {
			case AT_INFORMATION:
				return LT_INFORMATION;
			case AT_ERROR:
				return LT_ERROR;
			case AT_WARNING:
				return LT_WARNING;
			default:
				return LT_INFORMATION;
		}
	}
	
	public Object[] getContext() {
		if (this.relatedTo == null) {return null;}
		ArrayList l = new ArrayList();
		for (int i=0; i<this.relatedTo.length; i++) {
			//l.add(PluginCoreUtils.convert(this.relatedTo[i], false));
		}
		return l.toArray();
	}
	
	public int getTimeoutSecs() {
		if (this.timeoutSecs != -1) {return this.timeoutSecs;}
		return 1000;//COConfigurationManager.getIntParameter("Message Popup Autoclose in Seconds");
	}
	
}
