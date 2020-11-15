/*
 * Copyright (C) 2004 Sun Microsystems, Inc. All rights reserved. Use is
 * subject to license terms.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package com.frostwire.util.filetypes;

import java.util.Objects;

/**
 * This class represents an action that could be applied to a particular file type.
 * An action could be added to an <code>Association</code> object as part of an
 * association.
 * <p>
 * An <code>Action</code> object is a triple containing a description string,
 * a verb string and a command string. Common examples of verb are "open", "edit",
 * and "print". The command string consists of the executable file path followed
 * by command line parameters.
 *
 * @see Association
 */
public class Action {
    /**
     * Description of this action.
     * <p>
     * This field is not required to create a valid Action object.
     * It's used only on Windows, and on Gnome for Linux and Solaris, this field
     * is not used.
     */
    private String description;
    /**
     * Name of the verb field.
     */
    private final String verb;
    /**
     * Command field associated with the given verb.
     */
    private String command;
    /**
     * Hash code for this action
     */
    private int hashcode = 0;

    /**
     * Constructor of an <code>Action</code> object.
     * <p>
     * On Microsoft Windows platforms, the verb could be "open", "edit", or any given
     * name; on Gnome/UNIX platforms, it could only be "open", other verbs will
     * be ignored.
     *
     * @param verb    a given verb string.
     * @param command a given command string.
     */
    public Action(String verb, String command) {
        this.verb = verb;
        this.command = command;
    }

    /**
     * Constructor of an <code>Action</code> object.
     *
     * @param verb    a given verb value.
     * @param command a given command value.
     * @param desc    a given description value.
     */
    public Action(String verb, String command, String desc) {
        this.verb = verb;
        this.command = command;
        this.description = desc;
    }

    /**
     * Returns the value of the description field.
     *
     * @return the value of the description field.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the value of the verb field.
     *
     * @return the value of the verb field.
     */
    public String getVerb() {
        return verb;
    }

    /**
     * Returns the value of the command field.
     *
     * @return the value of the command field.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Determines whether or not two actions are equal. Two instances
     * of <code>Action</code> are equal if the values of all the fields
     * are the same.
     *
     * @param otherObj an object to be compared with this <code>Action</code>
     * @return <code>true</code> if the object to be compared is an instance of
     * <code>Action</code> and has the same values;
     * <code>false</code> otherwise.
     */
    public boolean equals(Object otherObj) {
        if (otherObj instanceof Action) {
            Action otherAction = (Action) otherObj;
            String otherDescription = otherAction.getDescription();
            String otherVerb = otherAction.getVerb();
            String otherCommand = otherAction.getCommand();
            return (Objects.equals(description, otherDescription))
                    && (Objects.equals(verb, otherVerb))
                    && (Objects.equals(command, otherCommand));
        }
        return false;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Returns the hashcode for this <code>Action</code>.
     *
     * @return a hash code for this <code>Action<code>.
     */
    public int hashCode() {
        if (hashcode != 0) {
            int result = 17;
            if (this.description != null) {
                result = 37 * result + this.description.hashCode();
            }
            if (this.verb != null) {
                result = 37 * result + this.verb.hashCode();
            }
            if (this.command != null) {
                result = 37 * result + this.command.hashCode();
            }
            hashcode = result;
        }
        return hashcode;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Returns a <code>String</code> that represents the value of this
     * <code>Action</code>.
     *
     * @return a string representation of this <code>Action</code>.
     */
    public String toString() {
        String crlfString = "\r\n";
        String content = "";
        String tabString = "\t";
        content = content.concat(tabString);
        content = content.concat("Description: ");
        if (this.description != null) {
            content = content.concat(description);
        }
        content = content.concat(crlfString);
        content = content.concat(tabString);
        content = content.concat("Verb: ");
        if (this.verb != null) {
            content = content.concat(verb);
        }
        content = content.concat(crlfString);
        content = content.concat(tabString);
        content = content.concat("Command: ");
        if (this.command != null) {
            content = content.concat(command);
        }
        content = content.concat(crlfString);
        return content;
    }
}
