/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
 *
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

package com.frostwire.android.gui.dialogs;

import java.util.List;

import android.app.Dialog;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractListAdapter;

/**
 * This dialog should evolve to allow us for reuse on a number of situations in which you
 * need a dialog that needs to display a list view control.
 * 
 * This would be the simplest version, in the future it will have a text editor to filter
 * the contents of the list, and it will also support different modes of selection.
 * 
 * For now it just uses an adapter to display the contents of the model data.
 * 
 * It's up to the user to implement the adapter (hmm, perhaps that's where the selection mode logic should be)
 * 
 * @author aldenml
 * @author gubatron
 *
 * @param <Adapter>
 * @param <I>
 */
public abstract class ConfirmListDialog<Adapter extends AbstractListAdapter<?>,T> extends AbstractDialog {
    
    /**
     * TODOS: 1. Add an optional text filter control that will be connected to the adapter.
     *        2. Add list selection-mode flags: SINGLE_SELECTION, MULTIPLE_SELECTION, NO_SELECTION (current)
     */

    private final static String TAG = "confirm_list_dialog";
    private String title;
    private String dialogText;
    private OnCancelListener onCancelListener;
    private OnClickListener onYesListener;
    
    public ConfirmListDialog() {
        super(TAG, R.layout.dialog_confirm_list);
    }
    
    public OnClickListener getOnYesListener() {
        return onYesListener;
    }
    
    abstract protected OnClickListener createOnYesListener(List<T> listData);
     
    /** rebuilds list of objects from json and does listView.setAdapter(YourAdapter(theObjectList)) */
    abstract protected List<T> initListAdapter(ListView listView, String listDataInJSON);
    
    protected void prepareArguments(String dialogTitle, String dialogText, String listDataInJSON) {
        Bundle bundle = new Bundle();
        bundle.putString("title", dialogTitle);
        bundle.putString("dialogText", dialogText);
        bundle.putString("listData", listDataInJSON);
        setArguments(bundle);
    }
    
    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
    	//TODO: Add checkbox on list item and make sure the adapter
    	//will know what items have been selected and that we can
    	//reinit the right checkboxes selected here when orientation change occurs.
    	
        Bundle bundle = getArguments();
        title = bundle.getString("title");
        dlg.setTitle(title);
        
        dialogText = bundle.getString("dialogText");
        TextView textView = findView(dlg, R.id.dialog_confirm_list_text);
        textView.setText(dialogText);
        
        ListView listView = findView(dlg, R.id.dialog_confirm_list_list);
        String listDataString = bundle.getString("listData");
        List<T> listData = initListAdapter(listView, listDataString);
        
        final Dialog dialog = dlg;
        Button noButton = findView(dialog, R.id.dialog_confirm_list_button_no);
        noButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onCancelListener != null) {
                    onCancelListener.onCancel(dialog);
                }
                dialog.dismiss();
            }
        });
        
        if (onCancelListener != null){
            dialog.setOnCancelListener(onCancelListener);
        }
        
        onYesListener = createOnYesListener(listData);

        if (onYesListener != null) {
        	Button yesButton = findView(dialog, R.id.dialog_confirm_list_button_yes);
        	yesButton.setOnClickListener(onYesListener);
        }
    }
    
    protected void setOnYesListener(OnClickListener listener) {
        onYesListener = listener;
    }

}