/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;

import com.frostwire.util.Ref;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class AbstractDialog extends DialogFragment {

    /**
     * The identifier for the positive button.
     */
    public static final int BUTTON_POSITIVE = Dialog.BUTTON_POSITIVE;

    /**
     * The identifier for the negative button. 
     */
    public static final int BUTTON_NEGATIVE = Dialog.BUTTON_NEGATIVE;

    private final String tag;
    private final int layoutResId;

    private WeakReference<Activity> activityRef;

    public AbstractDialog(String tag, int layoutResId) {
        this.tag = tag;
        this.layoutResId = layoutResId;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        activityRef = Ref.weak(activity);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dlg = super.onCreateDialog(savedInstanceState);

        setContentView(dlg, layoutResId);
        initComponents(dlg, savedInstanceState);

        return dlg;
    }

    public final void show(FragmentManager manager) {
        super.show(manager, tag);
    }

    public final void performDialogClick(int which) {
        performDialogClick(tag, which);
    }

    protected void setContentView(Dialog dlg, int layoutResId) {
        dlg.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dlg.setContentView(layoutResId);
    }

    protected void performDialogClick(String tag, int which) {
        if (Ref.alive(activityRef)) {
            dispatchDialogClick(activityRef.get(), tag, which);
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Dialog d = getDialog();
        if (d != null) {
            d.setCanceledOnTouchOutside(true);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }


    protected abstract void initComponents(Dialog dlg, Bundle savedInstanceState);

    @SuppressWarnings("unchecked")
    protected final <T extends View> T findView(Dialog dlg, int id) {
        return (T) dlg.findViewById(id);
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Serializable> T getArgument(String key) {
        return (T) getArguments().getSerializable(key);
    }

    private void dispatchDialogClick(Activity activity, String tag, int which) {
        dispatchDialogClickSafe(activity, tag, which);

        if (activity instanceof AbstractActivity) {
            List<Fragment> fragments = ((AbstractActivity) activity).getVisibleFragments();

            for (Fragment f : fragments) {
                dispatchDialogClickSafe(f, tag, which);
            }
        }
    }

    private void dispatchDialogClickSafe(Object obj, String tag, int which) {
        if (obj instanceof OnDialogClickListener) {
            ((OnDialogClickListener) obj).onDialogClick(tag, which);
        }
    }

    public interface OnDialogClickListener {
        void onDialogClick(String tag, int which);
    }
}
