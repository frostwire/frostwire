/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.IdRes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import com.frostwire.android.R;

import java.util.List;

/**
 * IMPORTANT:
 * - All subclasses must be public, otherwise the dialogs can't be instantiated by android on rotation
 * - All subclasses must only have an empty constructor. If you feel the need to use a custom constructor
 * implement a "newInstance(...)" method that uses the default empty constructor and then set your
 * attributes on the object to return.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public abstract class AbstractDialog extends DialogFragment {

    protected static String getSuggestedTAG(Class clazz) {
        StringBuilder sb = new StringBuilder();
        char[] className = clazz.getSimpleName().toCharArray();
        for (int i = 0; i < className.length; i++) {
            char c = className[i];
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append('_');
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * The identifier for the positive button.
     */
    private final String tag;
    private final int layoutResId;

    private OnDialogClickListener onDialogClickListener;

    public AbstractDialog(int layoutResId) {
        this.tag = getSuggestedTAG(getClass());
        this.layoutResId = layoutResId;
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DefaultDialogTheme);
    }

    public AbstractDialog(String tag, int layoutResId) {
        this.tag = tag;
        this.layoutResId = layoutResId;
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DefaultDialogTheme);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dlg = super.onCreateDialog(savedInstanceState);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setContentView(dlg, layoutResId);
        initComponents(dlg, savedInstanceState);
        return dlg;
    }

    public void show(FragmentManager manager) {
        super.show(manager, tag);
    }

    public final void performDialogClick(int which) {
        performDialogClick(tag, which);
    }

    private void setContentView(Dialog dlg, int layoutResId) {
        dlg.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dlg.setContentView(layoutResId);
    }

    protected void performDialogClick(String tag, int which) {
        Activity activity = getActivity();
        if (activity != null) {
            dispatchDialogClick(activity, tag, which);
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

    protected final <T extends View> T findView(Dialog dlg, @IdRes int id) {
        return dlg.findViewById(id);
    }

    private void dispatchDialogClick(Activity activity, String tag, int which) {
        if (onDialogClickListener != null) {
            dispatchDialogClickSafe(onDialogClickListener, tag, which);
            return;
        }
        dispatchDialogClickSafe(activity, tag, which);
        if (activity instanceof AbstractActivity) {
            List<Fragment> fragments = ((AbstractActivity) activity).getFragments();
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

    public void setOnDialogClickListener(OnDialogClickListener onDialogClickListener) {
        this.onDialogClickListener = onDialogClickListener;
    }

    public OnDialogClickListener getOnDialogClickListener() {
        return this.onDialogClickListener;
    }

    public interface OnDialogClickListener {
        void onDialogClick(String tag, int which);
    }
}
