/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.frostwire.android.R;

import java.util.List;

/**
 * AbstractDialog updated for AndroidX compatibility.
 */
public abstract class AbstractDialog extends DialogFragment {

    protected final String tag;
    private final int layoutResId;

    private OnDialogClickListener onDialogClickListener;

    public AbstractDialog(@LayoutRes int layoutResId) {
        this.tag = getSuggestedTAG(getClass());
        this.layoutResId = layoutResId;
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DefaultDialogTheme);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnDialogClickListener && onDialogClickListener == null) {
            onDialogClickListener = (OnDialogClickListener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(DialogFragment.STYLE_NORMAL);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setContentView(layoutResId);
        initComponents(dialog, savedInstanceState);
        return dialog;
    }

    public void show(FragmentManager manager) {
        super.show(manager, tag);
    }

    protected abstract void initComponents(Dialog dialog, Bundle savedInstanceState);

    protected final <T extends View> T findView(Dialog dialog, @IdRes int id) {
        return dialog.findViewById(id);
    }

    protected void performDialogClick(String tag, int which) {
        if (onDialogClickListener != null) {
            onDialogClickListener.onDialogClick(tag, which);
        }
    }

    public interface OnDialogClickListener {
        void onDialogClick(String tag, int which);
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


    public static String getSuggestedTAG(Class<?> clazz) {
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
}
