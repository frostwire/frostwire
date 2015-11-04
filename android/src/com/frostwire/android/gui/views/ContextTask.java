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

import java.lang.ref.WeakReference;

import android.content.Context;

import com.frostwire.util.Ref;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class ContextTask<Result> extends AbstractTask<Result> {

    private final WeakReference<Context> ctxRef;

    public ContextTask(Context ctx) {
        this.ctxRef = Ref.weak(ctx);
    }

    @Override
    protected final void onPostExecute(Result result) {
        if (Ref.alive(ctxRef)) {
            onPostExecute(ctxRef.get(), result);
        }
    }
    
    protected abstract void onPostExecute(Context ctx, Result result);
}
