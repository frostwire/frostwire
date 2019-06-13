/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui;

import java.awt.*;

/**
 * @author jum, gubatron
 * <p>
 * Implement a generic error handler that catches all errors thrown
 * by ActionListeners in the AWT event dispatcher thread.
 */
class DefaultErrorCatcher {
    static void install() {
        System.setProperty("sun.awt.exception.handler",
                DefaultErrorCatcher.class.getName());
    }

    /**
     * Determines if the message can be ignored.
     */
    private boolean isIgnorable(Throwable bug, String msg) {
        // ignore all overflows & out of memory errors,
        // since they'll give us absolutely no debugging information
        if (bug instanceof StackOverflowError)
            return true;
        if (bug instanceof OutOfMemoryError)
            return true;
        // no bug?  kinda impossible, but shouldn't report.
        if (msg == null)
            return true;
        // frickin' repaint manager stinks.
        if (msg.contains("javax.swing.RepaintManager"))
            return true;
        if (msg.contains("sun.awt.RepaintArea.paint"))
            return true;
        // display manager on OSX goes out of whack
        if (bug instanceof ArrayIndexOutOfBoundsException) {
            if (msg.contains("apple.awt.CWindow.displayChanged"))
                return true;
            if (msg.contains("javax.swing.plaf.basic.BasicTabbedPaneUI.getTabBounds"))
                return true;
        }
        // system clipboard can be held, preventing us from getting.
        // throws a RuntimeException through stuff we don't control...
        if (bug instanceof IllegalStateException) {
            if (msg.contains("cannot open system clipboard"))
                return true;
        }
        // odd component exception
        if (bug instanceof IllegalComponentStateException) {
            if (msg.contains("component must be showing on the screen to determine its location"))
                return true;
        }
        // various NPEs we can ignore:
        if (bug instanceof NullPointerException) {
            if (msg.contains("MetalFileChooserUI"))
                return true;
            if (msg.contains("WindowsFileChooserUI"))
                return true;
            if (msg.contains("AquaDirectoryModel"))
                return true;
            if (msg.contains("SizeRequirements.calculateAlignedPositions"))
                return true;
            if (msg.contains("BasicTextUI.damageRange"))
                return true;
            if (msg.contains("null pData"))
                return true;
            if (msg.contains("disposed component"))
                return true;
            if (msg.contains("javax.swing.JComponent.repaint")
                    && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getSaveAsFile")) {
                return true;
            }
            if (msg.contains("javax.swing.JComponent.repaint")
                    && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getInput")) {
                return true;
            }
        }
        if (bug instanceof IndexOutOfBoundsException) {
            if (msg.contains("Invalid index")
                    && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getSaveAsFile")) {
                return true;
            }
            if (msg.contains("Invalid index")
                    && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getInput")) {
                return true;
            }
        }
        // various InternalErrors we can ignore.
        if (bug instanceof InternalError) {
            if (msg.contains("getGraphics not implemented for this component"))
                return true;
        }
        // if we're not somewhere in the bug, ignore it.
        // no need for us to debug sun's internal errors.
        if (!msg.contains("com.limegroup.gnutella") && !msg.contains("org.limewire"))
            return true;
        // we intercept calls in various places -- check if the only
        // com.limegroup.gnutella is from an intercepted call.
        if (intercepts(msg, "com.limegroup.gnutella.tables.MouseEventConsumptionChecker"))
            return true;
        return intercepts(msg, "com.limegroup.gnutella.gui.tables.LimeJTable.processMouseEvent");
    }

    /**
     * Determines if the given string is the only place where 'com.limegroup.gnutella' exists.
     */
    private boolean intercepts(String msg, String inter) {
        int i = msg.indexOf(inter);
        // not intercepted at all?
        if (i == -1)
            return false;
        // something before it?
        if (msg.lastIndexOf("com.limegroup.gnutella", i) != -1 && msg.lastIndexOf("org.limewire", i) != -1)
            return false;
        i += inter.length();
        if (i >= msg.length())
            return false;
        // something after it?
        return msg.indexOf("com.limegroup.gnutella", i) == -1 || msg.lastIndexOf("org.limewire", i) == -1;
        // yup, it's the only com.limegroup.gnutella in there.
    }
}
