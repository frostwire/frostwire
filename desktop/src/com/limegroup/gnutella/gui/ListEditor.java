package com.limegroup.gnutella.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/** 
 * A list editor is a GUI control for editing lists of strings.
 * It consists of a scrolling pane, a text field, and an add and
 * remove button.<p>
 *
 * You can add ListDataListener's to this.  When you add/remove/modify
 * elements in the list, one of the 
 * intervalAdded/intervalRemoved/contentsChanged
 * methods will be called on each listener, respectively.  The source field
 * of the ListDataEvent passed to these methods will be the underlying
 * model passed to the constructor, an instance of Vector.  The upper
 * and lower index of this event will always be the same, since only one
 * element is modified at a time.
 */ 
public class ListEditor extends JPanel {
    /**
     * 
     */
    private static final long serialVersionUID = 5689653237762528073L;
    /** INVARIANT: model contains exactly the same elements as realModel. */
    protected Vector<String> model;
    protected DefaultListModel<Object> /* of String */ realModel;

    protected Vector<ListDataListener> listeners;

    private static final int DEFAULT_COLUMNS=10;  //size of editor
    protected JTextField editor;
    protected JButton addButton;
    protected JButton removeButton;
    protected JList<Object> list;

    /** True if I should append new items to end of the list; false if I should
     *  add them to the end of the list. */
    private boolean addTail=true;

    
    /** 
     * Creates a new list editor with an empty underlying model.
     * New elements are added to the tail of the list by default.
     * @see setModel
     */
    public ListEditor() {
        this(new Vector<String>());
    }

    /**
     * Creates a new list editor with the given underlying model.
     * New elements are added to the tail of the list by default.
     * @see setModel
     */
    public ListEditor(Vector<String> model) {
        this.listeners=new Vector<ListDataListener>();

        setLayout(new GridBagLayout());

        //Top half of the editor
        editor=new LimeTextField("");
        editor.setColumns(DEFAULT_COLUMNS);
		editor.setPreferredSize(new Dimension(500, 20));
		editor.setMaximumSize(new Dimension(500, 20));
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.weightx = 1;
		
		add(editor, gbc);
		
		Action addAction = new AddAction();
        addButton =  new JButton(addAction);
        GUIUtils.bindKeyToAction(editor, 
        		KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), addAction);
                
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, ButtonRow.BUTTON_SEP, 0, 0);
        
        add(addButton, gbc);
        
        Action removeAction = new RemoveAction();
        removeButton = new JButton(removeAction); 
        removeButton.setEnabled(false);
        
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(removeButton, gbc);
        
        //Bottom half of the editor
        list=new JList<Object>();
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(new ListListener());
        GUIUtils.bindKeyToAction(list,
        		KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), removeAction);
        
        JScrollPane scrollPane=new JScrollPane(list, 
                                  JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        setModel(model);
		scrollPane.setPreferredSize(new Dimension(500, 50));
		scrollPane.setMaximumSize(new Dimension(500, 50));

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(ButtonRow.BUTTON_SEP, 0, 0, 0);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        
        add(scrollPane, gbc);
    }

    /** 
     * @requires model not subsequently modified
     * @effects returns the underlying model of this, as a Vector of Strings.
     *  Changes to this will <i>not</i> be reflected in the GUI.  However,
     *  changes made through the GUI will be reflected in the model. 
     */
    public Vector<String> getModel() {
        return model;
    }

    /**
     * @requires model contains only Strings, model not subsequently modified
     * @modifies this
     * @effects sets the underlying model.  This will be reflected in the
     *  GUI. Modifications to this will <i>not</i> be reflected in the GUI.  
     *  However, changes made through the GUI will be reflected in the model. 
     */
     public synchronized void setModel(Vector<String> model) {
         //Copy model into realModel.
         this.model=model;        
         this.realModel=new DefaultListModel<Object>();
         for (int i=0; i<model.size(); i++)
             realModel.addElement(model.get(i));
         list.setModel(realModel);
     }

    /** 
     * @modifies this
     * @effects if addTail is true, new elements will be added to
     *  the end of the list.  Otherwise, they'll be added to the
     *  beginning.
     */
    public void setAddTail(boolean addTail) {
        this.addTail=addTail;
    }

    /** Returns true if items are added to the tail of the list,
     *  false otherwise. */
    public boolean getAddTail() {
        return addTail;
    }
    
    /**
     * Removes an item from the list.
     */
    public synchronized void removeItem(int i) {
        model.remove(i);
        realModel.remove(i);
        editor.setText("");
        
        ListDataEvent event=new ListDataEvent(model, 
            ListDataEvent.INTERVAL_REMOVED, i, i);
        for (int j=0; j<listeners.size(); j++) {
            ListDataListener listener=listeners.get(j);
            listener.intervalRemoved(event);
        }
    }


    /**
     * @modifies this
     * @effects adds listener to the list of listeners to be notified when
     *  the underlying model changes.
     */
    public synchronized void addListDataListener(ListDataListener listener) {
        listeners.add(listener);
    }

    
    /**
     * Enables the remove button if the selection of the lis is not empty, otherwise disables it.
     * Also sets the text of the currently selected value in the edit field.
     */
    private class ListListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
        	
			if (list.isSelectionEmpty()) {
				removeButton.setEnabled(false);
			}
			else {
				removeButton.setEnabled(true);
			}
			
            //Put it in the editor.
            Object val=list.getSelectedValue();
            if (val==null) 
                return;
            else
                editor.setText((String)val);
        }
    }

    /** Someone tried to add something to the list. */
    private class AddAction extends AbstractAction {
    	
    	/**
         * 
         */
        private static final long serialVersionUID = 8474390737220207662L;

        public AddAction()
    	{
    		putValue(Action.NAME, I18n.tr("Add"));
    	}
    	
        public void actionPerformed(ActionEvent e) {
            String text=editor.getText();
            //If nothing in editor, ignore
            if (text.trim().equals(""))
                return;
            //If something is selected, replace it.  Notify listeners.
            int i=list.getSelectedIndex();
            if (i!=-1) {                
                model.setElementAt(text,i);
                realModel.setElementAt(text,i);                

                ListDataEvent event=new ListDataEvent(model, 
                  ListDataEvent.CONTENTS_CHANGED, i, i);
                for (int j=0; j<listeners.size(); j++) {
                    ListDataListener listener=listeners.get(j);
                    listener.contentsChanged(event);
                }                    
            }
            // Otherwise add text to the end/beginning of the list. 
	           // Notify listeners.
            else {
                int last;
                if (addTail) {
                    //add to tail
                    model.addElement(text);
                    realModel.addElement(text);                    
                    last=model.size()-1;
                } else {
                    //add to head
                    model.add(0, text);
                    realModel.add(0, text);
                    last=0;
                }
                ListDataEvent event=new ListDataEvent(model, 
                  ListDataEvent.INTERVAL_ADDED, last, last);
                for (int j=0; j<listeners.size(); j++) {
                    ListDataListener listener=listeners.get(j);
                    listener.intervalAdded(event);
                }                    
            }
            editor.setText("");
            list.clearSelection();
        }
    }

    private class RemoveAction extends AbstractAction
	{
    	
    	/**
         * 
         */
        private static final long serialVersionUID = 6931596656024307229L;

        public RemoveAction()
    	{
    		putValue(Action.NAME, I18n.tr("Remove"));
    	}
    	
        /** Someone tried to remove something from the list. */
        public void actionPerformed(ActionEvent e) {
            //If something is selected, remove it. Notify listeners.
            int i=list.getSelectedIndex();
            if (i!=-1)
                removeItem(i);
        }
    }

//          Vector model=new Vector();
//          model.addElement("britney");
//          model.addElement("n'sync");
//          model.addElement("money");
//          model.addElement("spam");
//          model.addElement("Republican");
//          model.addElement("communist");
//          ListEditor panel=new ListEditor(model);
//          panel.setAddTail(false);
//          panel.addListDataListener(new TestListener(model));

//          JFrame frame = new JFrame("Spam Config Mock-up");
//          frame.addWindowListener(new WindowAdapter() {
//              public void windowClosing(WindowEvent e) {System.exit(0);}});

//          frame.getContentPane().add(panel, BorderLayout.NORTH);
//          frame.pack();
//          frame.setVisible(true);
//      }

//      static class TestListener implements ListDataListener {
//          private Vector model;
//          public TestListener(Vector model) {
//              this.model=model;
//          }

//          private void verify(ListDataEvent e) {
//              Assert.that(e.getIndex0()==e.getIndex1());
//              Assert.that(e.getSource()==model);
//          }

//          public void contentsChanged(ListDataEvent e) {
//              verify(e);  Assert.that(e.getType()==ListDataEvent.CONTENTS_CHANGED);
//              System.out.println("You just modified element "+e.getIndex0());
//              System.out.println("The new list is "+model.toString());
//          }

//          public void intervalAdded(ListDataEvent e) {
//              verify(e);  Assert.that(e.getType()==ListDataEvent.INTERVAL_ADDED);
//              System.out.println("You just added element "+e.getIndex0());
//              System.out.println("The new list is "+model.toString());
//          }

//          public void intervalRemoved(ListDataEvent e) {
//              verify(e);  Assert.that(e.getType()==ListDataEvent.INTERVAL_REMOVED);
//              System.out.println("You just removed element "+e.getIndex0());
//              System.out.println("The new list is "+model.toString());
//          }              
//      }
}
