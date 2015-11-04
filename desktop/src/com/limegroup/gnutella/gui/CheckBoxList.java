package com.limegroup.gnutella.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import com.limegroup.gnutella.gui.tables.DefaultTableBevelledCellRenderer;


/**
 * Provides a list of checkboxes in the look and feel of a {@link JList}.
 * <p>
 * The seleted items can be retrieved easily using {@link #getSelectedObjects()}
 * or {@link #getSelectedElements(Object[])}.
 */
public class CheckBoxList<E> extends BoxPanel {

    /**
     * 
     */
    private static final long serialVersionUID = 680881365220840255L;

    private String disabledTooltip = null;
    
    private boolean removeable = false;
    
    private boolean selectOff;
    private JScrollPane scrollPane;
    private JTable checkBoxList;
    private CheckBoxListSelectionListener selectionListener;
    private CheckBoxListCheckChangeListener<?> checkListener;
    private Object parent;

    private int highLightedRow = -1;
    
    
    /**
     * The set of elements included on the panel.
     */
    private final Set<E>     elements  = new HashSet<E>();
    
    /**
     * The subsets of unchecked and checked elements on the panel.
     */
    private final Set<E>     unchecked = new HashSet<E>();
    private final Set<E>     checked   = new HashSet<E>();
    
    /**
     * The subset of individual items that have been force disabled.
     */
    private final Set<E>     disabled  = new HashSet<E>();
    
    /**
     * The subset of items that are bolded for notification in the panel.
     */
    private final Set<E>     bolded    = new HashSet<E>();
   
        
    private Object selected;
    
    public static final int SELECT_FIRST_ON  = 0;
    public static final int SELECT_FIRST_OFF = 1;
    private static final int DEFAULT_ROW_HEIGHT = 22;
    
    /**
     * Holds the text provider used for this instance, is guaranteed to be 
     * non-null.
     */
    private TextProvider<E> provider;
    
    /**
     * Holds the provider used to indicate if an element should have a divider
     *  drawn above.  Null if the feature is disabled. 
     */
    private ExtrasProvider<E> extrasProvider;
    
    /**
     * Holds the array of checkboxes that are displayed.
     */
    private List<E> items; 
    /**
     * Holds the visible row count if set.
     */
    private int visibleRowCount = -1;
    /**
     * The key for the text provider property, when the text provider changes
     * for this component a property change event is fired with this key.
     */
    public static final String TEXT_PROVIDER_PROPERTY = "textProvider";

    private CheckBoxCellEditor editor;

   
    /**
     * Constructs a checkbox list for a collection of elements.
     * @param elements the collection of objects to be displayed in the checkbox list
     * @param provider the provider that retrieves the textual display information 
     * from the objects
     * @param selected whether or not the checkboxes should be selected initially
     * @throws NullPointerException if the text provider is <code>null</code>
     */
    public CheckBoxList(Collection<E> elements, Collection<E> notCheckedElements, TextProvider<E> provider, ExtrasProvider<E> separatorProvider, int mode) {
        this.provider = provider;
        this.extrasProvider = separatorProvider;
        
        if (provider == null) {
            throw new NullPointerException("provider must not be null");
        }

        this.parent = this;
        this.selectionListener = null;
        
        this.selectOff = (mode == SELECT_FIRST_OFF);

        this.initialize();
        this.setElements(elements, notCheckedElements);
       
    }
    
    public CheckBoxList(Collection<E> elements, Collection<E> notCheckedElements, TextProvider<E> provider, int mode) {
        this(elements, notCheckedElements, provider, null, mode);
    }
    
    
    public CheckBoxList(Collection<E> elements, TextProvider<E> provider, boolean checkAll, int mode) {
        this(elements, checkAll ? new HashSet<E>() : elements, provider, null, mode);
    }

    
    public CheckBoxList(Collection<E> totalElements, Collection<E> notCheckedElements, int mode) {
        this(totalElements, notCheckedElements, new DefaultTextProvider<E>(), null, mode);
    }    
    
   public CheckBoxList(Collection<E> elements, TextProvider<E> provider, int mode) {
        this(elements, new HashSet<E>(), provider, null, mode);
    }
    
    
    public CheckBoxList(Collection<E> elements) {
        this(elements, new DefaultTextProvider<E>(), SELECT_FIRST_ON);
    }
    
    public CheckBoxList(Collection<E> elements, TextProvider<E> provider) {
        this(elements, provider, SELECT_FIRST_ON);
    }
    
    
    
    /**
     * Convenience wrapper for {@link #setElements(Object[], boolean)
     * setObjects(Object[], true)}.
     */
    public void setElements(Collection<E> elements) {
        setElements(elements, true);
    }
    
    
    
    
    /**
     * Wrapper for setElements.
     */
    public void setElements(Collection<E> elements, boolean checked) {
        if (checked) {
            this.setElements(elements, new HashSet<E>());
        } 
        else {
            this.setElements(elements, elements);
        }
    }
    
    /**
     * Sets the element list.
     */
    public void setElements(Collection<E> elements, Collection<E> notCheckedElements) {
        this.items = new ArrayList<E>(elements);
        
        Object[][] rowData = new Object[elements.size()][1];        
        for ( int i=0 ; i<elements.size() ; i++ )
            rowData[i][0] = items.get(i);
       
        
        this.elements.clear();
        this.checked.clear();
        this.unchecked.clear();
                
        this.elements.addAll(elements);
        this.checked.addAll(elements);
        this.checked.removeAll(notCheckedElements);
        this.unchecked.addAll(notCheckedElements);
        
        if (rowData.length > 0) {
            this.selected = rowData[0];
        }
        
        this.checkBoxList.setModel(new DefaultTableModel(rowData, new String[]{""}));        
    }
    
    
    /**
     * Force updates every item in the list
     */
    public void update() {
        ((DefaultTableModel)this.checkBoxList.getModel()).fireTableStructureChanged();
        //repaint();
    }
     
    /**
     * Deletes an item from the list with key "key"
     */
    public void removeItem(Object key) {
        Set<E> newElements = new HashSet<E>(this.elements);
        Set<E> newUnchecked = new HashSet<E>(this.unchecked);
        newElements.remove(key);
        newUnchecked.remove(key);
        this.setElements(newElements, newUnchecked);
    }
   
    /**
     * Enables or disables the delete buttons on each row
     */
    public void setRemovable(boolean state) {
        this.removeable = state;
        this.update();
    }
    
     /**
     * Sets the enabled state of the List and its members.
     */
    public void setEnabled(boolean enabled) {
        this.checkBoxList.setEnabled(enabled);
    }
    
    /**
     * Sets the custom tooltip for disabled items
     */
    public void setDisabledTooltip(String tooltip) {
        this.disabledTooltip = tooltip;        
    }
    
    /**
     * Gets the custom tooltip for disabled items
     */
    public String getDisabledTooltip() {
        return this.disabledTooltip;        
    }
    
    private static void ensureRowVisible(JTable table, int row) {
        if(row != -1) {
            Rectangle cellRect = table.getCellRect(row, 0, false);
            Rectangle visibleRect = table.getVisibleRect();
            if( !visibleRect.intersects(cellRect) )
                table.scrollRectToVisible(cellRect);
        }
    }
    
    /**
     * Ensures the row with the key is visible.
     */
    public void ensureRowVisible(E key) {
        if (key == null) {
            throw new IllegalArgumentException();
        }
        
        int row = 0;   
               
        for ( ; row<this.checkBoxList.getModel().getRowCount() ; row++ ) {
            Object item = this.checkBoxList.getModel().getValueAt(row, 0);
            if (item == null) {
                return;
            }
            
            if (item.equals(key)) {
                break;
            }
        }
                        
        ensureRowVisible(this.checkBoxList, row);
    }
    
    /**
     * Selects an item in the list.
     */
    public void setItemSelected(E key) {
        if (key == null) {
            throw new IllegalArgumentException();
        }
        
        this.selected = key;
        this.ensureRowVisible(key);
        this.editor.notifyChange();
        this.update();
    }
    
    /**
     *  Checks an item, returns true if the item has changed state
     *  otherwise false.
     */
    public boolean setItemChecked(E key) {
        if (key == null) {
            throw new IllegalArgumentException();
        }
        
        if (!this.checked.contains(key)) {
            this.checked.add(key);
            this.unchecked.remove(key);
        }

        
        this.bolded.add(key);
        this.update();
        this.ensureRowVisible(key);
        
        (new Timer(2000, new BoldRemoveListener(key))).start();

        return true;
    }

    
    private class BoldRemoveListener implements ActionListener {
        private E key;
        
        public BoldRemoveListener(E key) {
            this.key = key;
        }
        
        public void actionPerformed(ActionEvent e) {
            bolded.remove(key);
            update();
        }
    }
    
    
    /** 
     * Disable or enable a set of items
     */
    public void setItemsEnabled(Set<E> keys, boolean state) {
        if (state) {
            Set<E> toUncheck = new HashSet<E>(this.checked);
            toUncheck.retainAll(keys);
            toUncheck.retainAll(this.disabled);
            
            this.checked.removeAll(toUncheck);
            this.unchecked.addAll(toUncheck);
            
            
            this.disabled.removeAll(keys);
        }
        else {
            this.disabled.addAll(keys);
        }
        this.update();
    }
    
    /** 
     * Check or uncheck a set of items
     */
    public void setItemsChecked(Set<E> keys, boolean state) {
        if (state) {
            this.unchecked.removeAll(keys);
            this.checked.addAll(keys);
        }
        else {
            this.unchecked.addAll(keys);
            this.checked.removeAll(keys);
        }
        this.update();
    }
    

    /**
     * Returns the enabled state of the List and its members.
     */
    public boolean isEnabled() {
        if (checkBoxList != null) {
            return this.checkBoxList.isEnabled();
        } else {
            return false;
        }
    }
    
    /**
     * Sets the preferred number of visible rows. 
     * @param rows
     */
    public void setVisibleRowCount(int rows) {
        this.visibleRowCount = rows;
        this.setPreferredSize(this.getPreferredScrollableViewportSize());
    }
    
        
    public Dimension getPreferredScrollableViewportSize() {
        return (visibleRowCount > 0 && items.size()> 0) ?
                new Dimension(getPreferredSize().width, visibleRowCount * this.checkBoxList.getRowHeight())
                : getPreferredSize();
    }
    
    
    public void setSelectionListener(CheckBoxListSelectionListener listener) {
        this.selectionListener = listener;  
    }
    
    public void setCheckChangeListener(CheckBoxListCheckChangeListener<?> listener) {
        this.checkListener = listener;  
    }

    /**
     * Sets a new next provider and fires a property change event.
     * @param provider the new text provider
     * @throws NullPointerException if the new provider is <code>null</code>
     */
    public void setTextProvider(TextProvider<E> provider) {
        if (provider == null) {
            throw new NullPointerException("provider must not be null");
        }
        TextProvider<?> oldProvider = this.provider;
        this.provider = provider;
        firePropertyChange(TEXT_PROVIDER_PROPERTY, oldProvider, this.provider);
    }
    
    /**
     * Returns the typed array of selected objects taking
     *  enable/disable into account
     */
    public List<E> getCheckedElements() {
        if (this.checkBoxList.isEnabled() && this.checked.size() > 0) {
            Set<E> totalChecked = new HashSet<E>(checked);
            totalChecked.removeAll(this.disabled);
            return new LinkedList<E>(totalChecked);
        }
        else {
            return Collections.emptyList();
        }
    }

    
    /**
     * Returns the typed array of unselected objects taking
     *  enable/disable into account
     */
    public List<E> getUncheckedElements() {
        if (!this.checkBoxList.isEnabled()) {
            return new LinkedList<E>(this.elements);
        }
        
        Set<E> toUncheck = new HashSet<E>(this.checked);
        toUncheck.retainAll(this.disabled);
        
        if (this.unchecked.size() > 0) {
            Set<E> totalUnchecked = new HashSet<E>(this.unchecked);
            totalUnchecked.addAll(toUncheck);

            return new LinkedList<E>(totalUnchecked);
        }
        else {
            return new LinkedList<E>(toUncheck);
        }
    }

    
    /**
     * Returns a new list of the total elements contained
     */
    public Set<E> getElements() {
        return new HashSet<E>(this.elements);
    }
    
    /**
     * Returns a new list of the unchecked elements
     *  without regards to the enabled/disabled status
     *  of the component or elements
     */
    public Set<E> getRawUncheckedElementsAsSet() {
        return new HashSet<E>(this.unchecked);
    }
    
    
    /**
     * Returns an array of actions operation on the checkbox list.
     * <p>
     * The first action sets all items to selected, the second deselects all
     * items.
     */
    public Action[] getActions() {
        return new Action[] { new SelectAllAction(), new DeselectAllAction() };
    }
    

    
    
    private void initialize()   {
        this.setBorder(new EmptyBorder(0, 0, 0, 0));
                
        this.editor = new CheckBoxCellEditor();
        
        this.checkBoxList = new CustomJTable();    
        this.checkBoxList.setDefaultRenderer(Object.class, new CheckBoxCellRenderer());
        this.checkBoxList.setDefaultEditor(Object.class, editor);
        this.checkBoxList.setRowHeight(DEFAULT_ROW_HEIGHT);
        
        // NOTE: workaround for windows/linux swing header consistency showing problem
        this.checkBoxList.setTableHeader(new JTableHeader());
        this.checkBoxList.getTableHeader().setVisible(false);
        this.checkBoxList.getTableHeader().setSize(0, 0);

        this.scrollPane = new JScrollPane(checkBoxList);
        this.add(scrollPane);
        
        this.updateUI();
    }
    
    @Override
    public void updateUI() {
        super.updateUI();

        if (this.checkBoxList == null) {
            return;
        }
        
        this.setBackground(UIManager.getColor("List.textBackground"));
        this.checkBoxList.setBackground(UIManager.getColor("List.background"));
        this.checkBoxList.setGridColor(UIManager.getColor("List.background"));
        this.scrollPane.getViewport().setBackground(UIManager.getColor("List.background"));
    }

    

    /**
     * Provides a default implementation of the {@link TextProvider} interface.
     */
    public static class DefaultTextProvider<E> implements TextProvider<E> {

        /**
         * Returns {@link Object#toString() obj.toString()}.
         */
        public String getText(E obj) {
            return obj.toString();
        }

        /**
         * Returns <code>null</code>.
         */
        public String getToolTipText(E obj) {
            return null;
        }

        /**
         * Returns <code>null</code>.
         */
        public Icon getIcon(E obj) {
            return null;
        }
        
    }
    
    
    
    
    
    private class SelectAllAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = -8173830762677196193L;

        public SelectAllAction() {
            putValue(Action.NAME, I18n.tr
                    ("Select All"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr
                    ("Marks all Items as Selected"));
        }
        
        public void actionPerformed(ActionEvent e) {
            checked.addAll(unchecked);
            unchecked.clear();


            scrollPane.setVisible(false);
            scrollPane.setVisible(true);
        }
        
    }
    
    private class DeselectAllAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = -7666969710559350155L;

        public DeselectAllAction() {
            putValue(Action.NAME, I18n.tr
                    ("Deselect All"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr
                    ("Deselects all Items in the List"));
        }
        
        public void actionPerformed(ActionEvent e) {
            unchecked.addAll(checked);
            checked.clear();
            
            scrollPane.setVisible(false);
            scrollPane.setVisible(true);
        }
        
    }


    
    private class CustomJTable extends JTable {
    
        /**
         * 
         */
        private static final long serialVersionUID = 2839028569443961323L;

        private void shift(int row) {
            if (row != highLightedRow) {
                highLightedRow = row;
                CheckBoxList.this.repaint();
            }
        }
        
        protected void processMouseMotionEvent( MouseEvent e) {
            if (removeable) {
                int row = rowAtPoint(e.getPoint());
                
                Rectangle firstRowRect = getCellRect(row, 0, false);
                Rectangle lastRowRect = getCellRect(row, getColumnCount() - 1, false);
                Rectangle dirtyRegion = firstRowRect.union(lastRowRect);
                
                if (e.getX() > dirtyRegion.getWidth() - 16) {
                    shift(row);
                } 
                else {
                    shift(-1);
                }
            }
            
            super.processMouseMotionEvent(e);
        }
        
        protected void processMouseEvent( MouseEvent e) {
            if (removeable) {
                shift(-1);
            }
            
            super.processMouseEvent(e);
        }
    }
    
    

     /**
     * Check box tree cell renderer.
     */
    private class CheckBoxCellRenderer extends DefaultTableBevelledCellRenderer {
        
        private static final long serialVersionUID = -8209871264709364379L;
        
        private IconDataCheckBox checkBox = new IconDataCheckBox();
                
        @Override
        @SuppressWarnings("unchecked")
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, 
                int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // See LWC-1158 for null weirdness
            if(value != null)
                checkBox.setData((E)value);

            checkBox.setRemovable(removeable);
            checkBox.setHighlight(row == highLightedRow);
            
            if (!selectOff && value != null && value.equals(selected)) {
                this.checkBox.setBackground(UIManager.getColor("List.selectionBackground"));
                this.checkBox.setForeground(UIManager.getColor("List.selectionForeground"));
            }
            else {
                this.checkBox.setBackground(UIManager.getColor("List.textBackground"));
                this.checkBox.setForeground(UIManager.getColor("List.textForeground"));
            }
            
            if (bolded.contains(value)) {
                this.checkBox.setBold(true);
            } 
            else {
                this.checkBox.setBold(false);
            }
            
            if (disabled.contains(value)) {
                if (disabledTooltip != null) {
                    checkBox.setToolTipText(disabledTooltip);
                }
                checkBox.setEnabled(false);
                checkBox.setSelected(false);
            }
            else {
                checkBox.setSelected(!unchecked.contains(value));
                checkBox.setEnabled(table.isEnabled());
            }
            
            return checkBox;
        }
    }
    

    /**
     * Checkbox tree cell editor.
     */
    private class CheckBoxCellEditor extends DefaultCellEditor {
                       
        /**
         * 
         */
        private static final long serialVersionUID = 8487646158995389360L;
        private CustomEditorDelegate customDelegate;

        public CheckBoxCellEditor() {
            super(new JCheckBox());
                        
            IconDataCheckBox checkBox = new IconDataCheckBox();
            editorComponent = checkBox;
          
            checkBox.setRequestFocusEnabled(false);
            
            this.customDelegate = new CustomEditorDelegate();
            this.delegate = customDelegate;            
            checkBox.addActionListener(customDelegate);
            checkBox.addTopMouseListener(customDelegate);
        
           
        }
        
        /**
         * Used to pass notifications of selection focus changes down to the
         *  table editor classes so selection changes can be handled 
         *  correctly.
         */
        public void notifyChange() {
            this.customDelegate.notifyChange();
        }
        
        /**
         * Overridden to pass the value to the delegate and not just its
         * string representation as done in the super class.
         */
        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value,
                boolean isSelected, boolean expanded, boolean leaf, int row) {
            
            delegate.setValue(value);
            return editorComponent;
        }
        
        /**
         * The custom table editor model to implement focus selection and all the checkbox
         *  update control 
         */
        private class CustomEditorDelegate extends EditorDelegate implements MouseListener {
            
            private static final long serialVersionUID = -7007164079207676831L;
            private Object lastValue;
            
            public CustomEditorDelegate() {
                this.lastValue = this.value;
            }
            
            
            public void notifyChange() {
                this.lastValue = null;
            }
            
            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public void setValue(Object value) { 

                if (value == null) {
                    return;
                }
                
                IconDataCheckBox box = ((IconDataCheckBox)editorComponent);
                
                box.setData((E)value);   
                box.setRemovable(removeable);
                box.setEnabled(isEnabled());
                
                if (disabled.contains(value)) {
                    if (disabledTooltip != null) {
                        box.setToolTipText(disabledTooltip);
                    }
                    box.setEnabled(false);
                    box.setSelected(false);
                    
                    this.value = value;
                    return;
                }
                
                boolean isSelected;
                
                selected = value;
                if (this.lastValue == value) {
                    isSelected = true;
                }
                else {
                    isSelected = false;
                    repaint();
                    if (selectionListener != null) {   
                        selectionListener.valueChanged(new CheckBoxListSelectionEvent(parent, value));
                    }
                }
                
                
                if (selectOff || isSelected) {

                    boolean decheck = unchecked.contains(value);

                    if (decheck) {
                        unchecked.remove(value);
                        checked.add((E)value);
                    }
                    else {
                        unchecked.add((E)value);
                        checked.remove(value);
                    }

                    if (checkListener != null) {
                        checkListener.valueChanged(new CheckBoxListCheckChangeEvent(parent, value, decheck));
                    }
                }

                box.setSelected(checked.contains(value));
                                    
                this.lastValue = value;
                this.value = value;
            }
            
            @Override
            public Object getCellEditorValue() {
                return this.value;
            }
            
            
            /**
             * Overriden to allow disable selection so the custom
             *  selection model can be used
             */
            @Override
            public boolean shouldSelectCell(EventObject anEvent) {
                return false;
            }

            public void mouseClicked(MouseEvent e) {
            }

            public void mouseEntered(MouseEvent e) {
            }

            public void mouseExited(MouseEvent e) {
            }

            public void mousePressed(MouseEvent e) {
                this.stopCellEditing();
            }

            public void mouseReleased(MouseEvent e) {
            }
        
    }
    

  
        

    }

    
    /**
     * Stores selection change events
     */
    public static class CheckBoxListSelectionEvent extends EventObject{
        /**
         * 
         */
        private static final long serialVersionUID = 6985964072155472329L;
        private Object selected;
       
        public CheckBoxListSelectionEvent(Object source, Object selected) {
            super(source);
            this.selected = selected;
        }
        
        /**
         * Gets the item that has just been selected
         * @return selected item
         */
        public Object getSelected() {
            return this.selected;
        }
    }


    /**
     * Stores check/uncheck events 
     */
    public static class CheckBoxListCheckChangeEvent<E> extends EventObject{
        /**
         * 
         */
        private static final long serialVersionUID = -2715339837857605924L;
        private E selected;
        private boolean checked;
       
        public CheckBoxListCheckChangeEvent(Object source, E selected, boolean checked) {
            super(source);
            this.selected = selected;
            this.checked = checked;
        }
        
        /**
         * Gets the item that was changed
         * @return The item changed
         */
        public E getSelected() {
            return this.selected;
        }
        
        /**
         * Gets the check state of the change
         * @return the check state of the change
         */
        public boolean getChecked() {
            return this.checked;
        }
    }

    /**
     * Defines the listener to notify item a change in list selection
     *  if select first mode is enabled
     */
    public static interface CheckBoxListSelectionListener {
        public void valueChanged(CheckBoxListSelectionEvent e);        
    }
        
    /**
     * Defines the listener to notify item check/uncheck
     *  events in the list
     */
    public static interface CheckBoxListCheckChangeListener<E> {
        public void valueChanged(CheckBoxListCheckChangeEvent<E> e);        
    }
    
    /** 
     * This interface is used to define a provider with a
     *  check if an element requires is required to have a horizontal
     *  separator drawn above it. 
     */
    public interface ExtrasProvider<E> {
        boolean isSeparated(E obj);
        String getComment(E obj);
    }
    
    /**
     * This interface defines the requirements for a kind of renderer class which
     * retrieves the display information for the given object. 
     */
    public interface TextProvider<E> {
        /**
         * Returns the label text displayed next to the checkbox. 
         * @param obj the underlying data object
         * @return
         */
        String getText(E obj);
        /**
         * Returns the tooltip text for the checkbox item.
         * @param obj the underlying data object
         * @return
         */
        String getToolTipText(E obj);
        /**
         * Returns an icon that is displayed to the left of the checkbox.
         */
        Icon getIcon(E obj);
    }
    
    
    /**
     *  Composite class to make a check box with an icon along with the text
     *      
     */
    private class IconDataCheckBox extends BoxPanel {
        
        /**
         * 
         */
        private static final long serialVersionUID = 7370736947464891601L;
        private E obj;
        private JCheckBox checkBox;
        private JLabel label;
        private DeleteButton button;
        private Border blankBorder;
        private SeperatorBorder sepBorder;
        private Font originalFont;
        private Font boldFont;
        
        public IconDataCheckBox() {
            super(BoxPanel.X_AXIS);
            
            sepBorder = new SeperatorBorder();
            blankBorder = BorderFactory.createMatteBorder(4,4,4,4, (Icon)null);

            label = new JLabel();
            label.setBorder(blankBorder);
            checkBox = new JCheckBox("", true);

            button = new DeleteButton();
      
            add(Box.createHorizontalStrut(4));
            add(checkBox);
            add(label);
            add(Box.createHorizontalStrut(1));
            add(Box.createHorizontalGlue());
            add(button);
            
            updateLook();
   
            originalFont = label.getFont();
            boldFont = originalFont.deriveFont(originalFont.getStyle() | Font.BOLD | Font.ITALIC);
        }
        
        public void setRemovable(boolean state) {
            this.button.setVisible(state);
        }
        
        public void setData(E obj) {
            this.obj = obj;
            
            String text = provider.getText(obj);

            label.setIcon(provider.getIcon(obj));
            
            if (extrasProvider != null) {
                label.setText(text+" "+extrasProvider.getComment(obj));
                
                if (extrasProvider.isSeparated(obj)) {
                    label.setBorder(sepBorder);
                }
                else {
                    label.setBorder(blankBorder);
                }
            }
            else {
                label.setText(text);
            }
            
            
            this.setToolTipText(provider.getToolTipText(obj));
        }
        
        public void setHighlight(boolean b) {
            button.setHighlight(b);
            if (b) {
                this.setToolTipText("Remove...");
            } 
            else {
               this.setToolTipText(provider.getToolTipText(obj));
            }
                
        }
        
        public void setSelected(boolean selected) {
            checkBox.setSelected(selected);
        }        

        public void setEnabled(boolean enabled) {
            checkBox.setEnabled(enabled);
            label.setEnabled(enabled);
        }        
        
        public void setForeground(Color c) {
            if (label != null) {
                label.setForeground(c);
            }
        }
        
        public void setBold(boolean b) {
            if (b) {
                label.setFont(boldFont);
            } 
            else {
                label.setFont(originalFont);
            }
        }
        
        private void updateLook() {
            setOpaque(true);
            setForeground(UIManager.getColor("List.foreground"));
            setBackground(UIManager.getColor("List.background"));
            
            checkBox.setBackground(UIManager.getColor("List.background"));
            checkBox.setForeground(UIManager.getColor("List.foreground"));
            checkBox.setFont(UIManager.getFont("Table.font"));
            checkBox.setOpaque(false);
            
            label.setBackground(UIManager.getColor("List.background"));
            label.setForeground(UIManager.getColor("List.foreground"));
            label.setFont(UIManager.getFont("Table.font"));
            label.setOpaque(false);
            
            
        }
        
        /**
         * Adds an <code>ActionListener</code> to the button.
         * @param l the <code>ActionListener</code> to be added
         */
        public void addActionListener(ActionListener l) {
            this.checkBox.addActionListener(l);
        }
        
        
        public void addTopMouseListener(MouseListener l) {
            this.addMouseListener(l);
            this.label.addMouseListener(l);
        }
        
        
        
        private class DeleteButton extends JButton {
            /**
             * 
             */
            private static final long serialVersionUID = 2099563643830495610L;
            private Icon iconReg;
            private Icon iconHi;
            
            public DeleteButton() {
                iconReg = GUIMediator.getThemeImage("delete_small");
                iconHi = GUIMediator.getThemeImage("delete_small_hi");
                
                this.setIcon(iconReg);
                this.setMargin(new Insets(0,0,0,0));         
                this.setBorder(BorderFactory.createEmptyBorder());
                this.setContentAreaFilled(false);
                this.setVisible(false);
                this.addMouseListener(new MouseListener() {
                    public void mouseClicked(MouseEvent e) {
                    }
                    public void mouseEntered(MouseEvent e) {
                    }
                    public void mouseExited(MouseEvent e) {
                    }
                    public void mousePressed(MouseEvent e) {
                        removeItem(obj);                        
                    }
                    public void mouseReleased(MouseEvent e) {
                    }
                });
            }
            
            public void setHighlight(boolean b) {
                if (b) {
                    this.setIcon(iconHi);
                }
                else {
                    this.setIcon(iconReg);
                }
            }
        }
        
    }
    
    

}
