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

import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * Wraps a {@link CheckBoxList} into a scroll pane and shows its 
 * {@link CheckBoxList#getActions() actions} in a right aligned button
 * row underneath.
 */
public class CheckBoxListPanel<E> extends BoxPanel {
	
	/**
     * 
     */
    private static final long serialVersionUID = 1989311176988471587L;
    private CheckBoxList<E> list;

	/**
	 * Constructs a checkbox list panel for an array of objects.
	 * <p>
	 * See {@link CheckBoxList#CheckBoxList(Collection<E>, TextProvider, boolean)}.
	 */
	public CheckBoxListPanel(Collection<E> elements, CheckBoxList.TextProvider<E> provider,
			boolean selected) {
	    list = new CheckBoxList<E>(elements, provider, selected, CheckBoxList.SELECT_FIRST_OFF);
		initialize();
	}
	
	/**
	 * Constructs a checkbox list panel for an array of objects.
	 * <p>
	 * See {@link CheckBoxList#CheckBoxList(Collection<E>)}.
	 */
	public CheckBoxListPanel(Collection<E> elements) {
		list = new CheckBoxList<E>(elements);
		initialize();
	}
	
	/**
	 * Constructs an empty checkbox list panel.
	 * <p>
	 * See {@link CheckBoxList#CheckBoxList()}.
	 */
	@SuppressWarnings("unchecked")
    public CheckBoxListPanel() {
		list = new CheckBoxList<E>((Collection<E>)Collections.emptyList());
		initialize();
	}
	
	private void initialize() {
		InternalJScrollPane scrollPane = new InternalJScrollPane(list);
		scrollPane.getViewport().setBackground(UIManager.getColor("List.background"));
		add(scrollPane);
		add(Box.createVerticalStrut(ButtonRow.BUTTON_SEP));
		add(new ButtonRow(list.getActions(), ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE));
	}
	
	/**
	 * Returns a typed array of the selected objects.
	 * See {@link CheckBoxList#getSelectedElements(Object[])}.
	 */
	public List<E> getSelectedElements() {
		return list.getCheckedElements();
	}
	
	/**
	 * Returns the checkbox list used internally.
	 * @return
	 */
	public CheckBoxList<E> getList() {
		return list;
	}
	
	/**
	 * Inherit from JScrollPane just to override updateUI.
	 */
	private class InternalJScrollPane extends JScrollPane {
		
		/**
         * 
         */
        private static final long serialVersionUID = 5346177338334373472L;

        public InternalJScrollPane(Component comp) {
			super(comp);
			getViewport().setBackground(UIManager.getColor("List.background"));
		}
		
		public void updateUI() {
			super.updateUI();
			getViewport().setBackground(UIManager.getColor("List.background"));
		}
	}
}
