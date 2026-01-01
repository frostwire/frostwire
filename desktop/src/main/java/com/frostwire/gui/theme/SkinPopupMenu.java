package com.frostwire.gui.theme;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class SkinPopupMenu extends JPopupMenu {
    /**
     *
     */

    @Override
    public void addSeparator() {
        add(new SkinPopupMenu.Separator());
    }

    @Override
    public JMenuItem add(Action a) {
        SkinMenuItem mi = createActionComponent(a);
        mi.setAction(a);
        add(mi);
        return mi;
    }

    @Override
    protected SkinMenuItem createActionComponent(Action a) {
        SkinMenuItem mi = new SkinMenuItem() {
            /**
             *
             */

            protected PropertyChangeListener createActionPropertyChangeListener(Action a) {
                PropertyChangeListener pcl = createActionChangeListener(this);
                if (pcl == null) {
                    pcl = super.createActionPropertyChangeListener(a);
                }
                return pcl;
            }
        };
        mi.setHorizontalTextPosition(JButton.TRAILING);
        mi.setVerticalTextPosition(JButton.CENTER);
        return mi;
    }

    /**
     * A popup menu-specific separator.
     */
    static public class Separator extends JSeparator {
        /**
         *
         */

        public Separator() {
            super(JSeparator.HORIZONTAL);
        }

        /**
         * Returns the name of the L&amp;F class that renders this component.
         *
         * @return the string "PopupMenuSeparatorUI"
         * @see JComponent#getUIClassID
         * @see UIDefaults#getUI
         */
        public String getUIClassID() {
            return "PopupMenuSeparatorUI";
        }
    }
}
