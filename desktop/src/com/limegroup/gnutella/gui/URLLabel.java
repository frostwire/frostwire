/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.actions.LimeAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeListener;

/**
 * A label that has a clickable text. The text is rendered as an HTML link and
 * the mouse cursor is changed when the mouse hovers over the label.
 */
public class URLLabel extends JLabel {
    private MouseListener urlListener;
    private PropertyChangeListener listener = null;
    private Action currentAction;
    private String url = "";
    private String text;
    private Color linkColor = UIManager.getColor("Label.foreground");

    /**
     * Constructs a new clickable label with <code>url</code> as the
     * text.
     *
     * @param url the URL to open when the label is clicked
     */
    public URLLabel(final String url) {
        this(url, url);
    }

    /**
     * Constructs a new clickable label.
     *
     * @param url  the URL to open when the label is clicked
     * @param text the label's text
     */
    public URLLabel(final String url, final String text) {
        this.url = url;
        setText(text);
        setToolTipText(url);
        installListener(GUIUtils.getURLInputListener(url));
    }

    @Override
    public void setText(String text) {
        this.text = text;
        String htmlString = null;
        if (text != null) {
            htmlString = ("<html><a href=\"" + url + "\"" +
                    (linkColor != null ? "color=\"#" + GUIUtils.colorToHex(linkColor) + "\"" : "") +
                    ">" + text + "</a></html>");
        }
        super.setText(htmlString);
    }

    private void setColor(Color fg) {
        linkColor = fg;
        setText(text);
    }

    private Action getAction() {
        return currentAction;
    }

    public void setAction(Action action) {
        // remove old listener
        Action oldAction = getAction();
        if (oldAction != null) {
            oldAction.removePropertyChangeListener(getListener());
        }
        // add listener
        currentAction = action;
        currentAction.addPropertyChangeListener(getListener());
        installListener(GUIUtils.getURLInputListener(action));
        updateLabel();
    }

    private PropertyChangeListener getListener() {
        if (listener == null) {
            listener = evt -> {
                //update label properties
                updateLabel();
            };
        }
        return listener;
    }

    /*
     * Update label text based on action event
     */
    private void updateLabel() {
        if (currentAction != null) {
            String display = (String) currentAction.getValue(Action.NAME);
            Color color = (Color) currentAction.getValue(LimeAction.COLOR);
            if (color != null)
                setColor(color);
            setIcon((Icon) currentAction.getValue(Action.SMALL_ICON));
            setToolTipText((String) currentAction.getValue(Action.SHORT_DESCRIPTION));
            // display
            setText(display);
        } else {
            setText(text);
            setToolTipText(url);
        }
    }

    private void installListener(MouseListener listener) {
        if (urlListener != null) {
            removeMouseListener(urlListener);
        }
        urlListener = listener;
        addMouseListener(urlListener);
    }
}