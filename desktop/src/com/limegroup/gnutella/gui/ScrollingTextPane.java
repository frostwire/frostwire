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

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Extend <tt>JScrollPane</tt> so that a scrolled html file is shown
 */
final class ScrollingTextPane extends JScrollPane {
    /**
     *
     */
    private static final long serialVersionUID = 1706678462639869854L;
    /**
     * <tt>JEditorPane</tt> to show the text
     */
    private final JEditorPane EDITOR_PANE;
    /**
     * Timer to control scrolling
     */
    private Timer _timer;

    /**
     * Constructs the elements of the about window.
     *
     * @param html The text of the HTML to load into the scrolling pane.
     */
    ScrollingTextPane(String html) {
        if (html == null)
            throw new NullPointerException("null html");
        EDITOR_PANE = new JEditorPane("text/html", html);
        EDITOR_PANE.setMargin(new Insets(5, 5, 5, 5));
        // don't allow edit of editor pane - use it just as a viewer
        EDITOR_PANE.setEditable(false);
        // add it to the scrollpane
        getViewport().add(EDITOR_PANE);
        // enable double buffering for smooth scroll effect
        this.setDoubleBuffered(true);
        // create timer
        Action scrollText = new AbstractAction() {
            /**
             *
             */
            private static final long serialVersionUID = 1486050927440198480L;

            public void actionPerformed(ActionEvent e) {
                scroll();
            }
        };
        _timer = new Timer(50, scrollText);
        // set view to beginning of pane
        EDITOR_PANE.setCaretPosition(0);
    }

    /**
     * Stop scrolling.
     */
    void stopScroll() {
        _timer.stop();
    }

    /**
     * Scroll the content of the JEditorPane
     */
    private void scroll() {
        // calculate visible rectangle
        Rectangle rect = EDITOR_PANE.getVisibleRect();
        // get x / y values
        int x = rect.x;
        int y = this.getVerticalScrollBar().getValue();
        if ((y + rect.height) >= EDITOR_PANE.getHeight()) {
            return;
        } else {
            y += 1;
        }
        Rectangle rectNew =
                new Rectangle(x, y, (x + rect.width),
                        (y + rect.height));
        // scroll to current position
        EDITOR_PANE.scrollRectToVisible(rectNew);
    }

    /**
     * Adds a <tt>HyperlinkListener</tt> instance to the underlying
     * <tt>JEditorPane</tt> instance.
     *
     * @param listener the listener for hyperlinks
     */
    void addHyperlinkListener(HyperlinkListener listener) {
        EDITOR_PANE.addHyperlinkListener(listener);
    }
}
