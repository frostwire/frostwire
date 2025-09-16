/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;

import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class ClickAdapter<T> implements View.OnClickListener, View.OnLongClickListener, View.OnKeyListener, DialogInterface.OnClickListener, CompoundButton.OnCheckedChangeListener {

    protected final WeakReference<T> ownerRef;

    public ClickAdapter(T owner) {
        this.ownerRef = Ref.weak(owner);
    }

    @Override
    public final void onClick(View v) {
        if (Ref.alive(ownerRef)) {
            onClick(ownerRef.get(), v);
        }
    }

    @Override
    public final boolean onLongClick(View v) {
        return Ref.alive(ownerRef) && onLongClick(ownerRef.get(), v);
    }

    @Override
    public final boolean onKey(View v, int keyCode, KeyEvent event) {
        return Ref.alive(ownerRef) && onKey(ownerRef.get(), v, keyCode, event);
    }

    @Override
    public final void onClick(DialogInterface dialog, int which) {
        if (Ref.alive(ownerRef)) {
            onClick(ownerRef.get(), dialog, which);
        }
    }

    @Override
    public final void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (Ref.alive(ownerRef)) {
            onCheckedChanged(ownerRef.get(), buttonView, isChecked);
        }
    }

    public void onClick(T owner, View v) {
    }

    @SuppressWarnings("unused")
    public boolean onLongClick(T owner, View v) {
        return false;
    }

    public boolean onKey(T owner, View v, int keyCode, KeyEvent event) {
        return false;
    }

    @SuppressWarnings("unused")
    public void onClick(T owner, DialogInterface dialog, int which) {
    }

    public void onCheckedChanged(T owner, CompoundButton buttonView, boolean isChecked) {
    }
}
