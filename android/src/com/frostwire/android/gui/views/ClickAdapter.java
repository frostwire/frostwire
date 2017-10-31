/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
