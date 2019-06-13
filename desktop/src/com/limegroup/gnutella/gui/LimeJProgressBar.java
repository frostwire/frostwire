package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;

/**
 * A JProgressBar that doesn't NPE when retrieving the preferredSize.
 * See: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6337517
 * (If the user has a custom XP Skin, it'll throw an NPE.)
 * <p>
 * Note: Since Java 1.6 the above bug is reported as fixed.
 */
public class LimeJProgressBar extends JProgressBar {
    /**
     *
     */
    private static final long serialVersionUID = -391739746034247225L;
    private final static Dimension PREFERRED_HORIZONTAL_SIZE = new Dimension(146, 17);

    LimeJProgressBar() {
        super();
    }

    @Override
    public Dimension getMaximumSize() {
        try {
            return super.getMaximumSize();
        } catch (NullPointerException e) {
            Dimension d;
            if (getOrientation() == JProgressBar.HORIZONTAL) {
                d = new Dimension(Short.MAX_VALUE, PREFERRED_HORIZONTAL_SIZE.height);
            } else {
                d = new Dimension(PREFERRED_HORIZONTAL_SIZE.width, Short.MAX_VALUE);
            }
            return d;
        }
    }

    @Override
    public Dimension getMinimumSize() {
        try {
            return super.getMinimumSize();
        } catch (NullPointerException npe) {
            Dimension d;
            if (getOrientation() == JProgressBar.HORIZONTAL) {
                d = new Dimension(10, PREFERRED_HORIZONTAL_SIZE.height);
            } else {
                d = new Dimension(PREFERRED_HORIZONTAL_SIZE.width, 10);
            }
            return d;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        try {
            return super.getPreferredSize();
        } catch (NullPointerException npe) {
            Dimension d;
            if (getOrientation() == JProgressBar.HORIZONTAL) {
                d = PREFERRED_HORIZONTAL_SIZE;
            } else {
                //noinspection SuspiciousNameCombination
                d = new Dimension(PREFERRED_HORIZONTAL_SIZE.height, PREFERRED_HORIZONTAL_SIZE.width);
            }
            return d;
        }
    }
}
