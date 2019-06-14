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

package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.text.JTextComponent;

/**
 * {@link PromptTextUI} implementation for rendering prompts on
 * {@link JTextField}s and uses a {@link JTextField} as a prompt component.
 *
 * @author Peter Weishapl <petw@gmx.net>
 */
public class PromptTextFieldUI extends PromptTextUI {
    /**
     * Shared prompt renderer.
     */
    private final static LabelField txt = new LabelField();

    /**
     * Creates a new {@link PromptTextFieldUI}.
     *
     * @param delegate
     */
    PromptTextFieldUI(TextUI delegate) {
        super(delegate);
    }

    /**
     * Overrides {@link #getPromptComponent(JTextComponent)} to additionally
     * update {@link JTextField} specific properties.
     */
    public JTextComponent getPromptComponent(JTextComponent txt) {
        LabelField lbl = (LabelField) super.getPromptComponent(txt);
        JTextField txtField = (JTextField) txt;
        lbl.setHorizontalAlignment(txtField.getHorizontalAlignment());
        lbl.setColumns(txtField.getColumns());
        // Make search field in Leopard paint focused border.
        lbl.hasFocus = txtField.hasFocus() && NativeSearchFieldSupport.isNativeSearchField(txtField);
        // leopard client properties. see
        // http://developer.apple.com/technotes/tn2007/tn2196.html#JTEXTFIELD_VARIANT
        NativeSearchFieldSupport.setSearchField(lbl, NativeSearchFieldSupport.isSearchField(txtField));
        NativeSearchFieldSupport.setFindPopupMenu(lbl, NativeSearchFieldSupport.getFindPopupMenu(txtField));
        //here we need to copy the border again for Mac OS X, because the above calls may have replaced it.
        lbl.setBorder(txtField.getBorder());
        return lbl;
    }

    /**
     * Returns a shared {@link JTextField}.
     */
    protected JTextComponent createPromptComponent() {
        txt.updateUI();
        return txt;
    }

    private static final class LabelField extends JTextField {
        private static final long serialVersionUID = -4888828236256139176L;
        boolean hasFocus;

        @Override
        public boolean hasFocus() {
            return hasFocus;
        }
    }
}
