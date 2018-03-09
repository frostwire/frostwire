/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.util;

import android.os.Handler;
import android.os.Looper;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * Utility class for asynchronous task in the background.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Asyncs {

    private Asyncs() {
    }

    // context + return

    public static <C, R> void invokeAsync(C context,
        ContextResultTask<C, R> task,
        ContextPostTask<C, R> post) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            R r = task.run(c);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, r));
            }
        });
    }

    public static <C, R, T1> void invokeAsync(C context,
        ContextResultTask1<C, R, T1> task,
        T1 arg1,
        ContextPostTask<C, R> post) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            R r = task.run(c, arg1);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, r));
            }
        });
    }

    public static <C, R, T1, T2> void invokeAsync(C context,
        ContextResultTask2<C, R, T1, T2> task,
        T1 arg1, T2 arg2,
        ContextPostTask<C, R> post) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            R r = task.run(c, arg1, arg2);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, r));
            }
        });
    }

    public static <C, R, T1, T2, T3> void invokeAsync(C context,
        ContextResultTask3<C, R, T1, T2, T3> task,
        T1 arg1, T2 arg2, T3 arg3,
        ContextPostTask<C, R> post) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            R r = task.run(c, arg1, arg2, arg3);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, r));
            }
        });
    }

    public static <C, R, T1, T2, T3, T4> void invokeAsync(C context,
        ContextResultTask4<C, R, T1, T2, T3, T4> task,
        T1 arg1, T2 arg2, T3 arg3, T4 arg4,
        ContextPostTask<C, R> post) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            R r = task.run(c, arg1, arg2, arg3, arg4);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, r));
            }
        });
    }

    public interface ContextResultTask<C, R> {
        R run(C context);
    }

    public interface ContextResultTask1<C, R, T1> {
        R run(C context, T1 arg1);
    }

    public interface ContextResultTask2<C, R, T1, T2> {
        R run(C context, T1 arg1, T2 arg2);
    }

    public interface ContextResultTask3<C, R, T1, T2, T3> {
        R run(C context, T1 arg1, T2 arg2, T3 arg3);
    }

    public interface ContextResultTask4<C, R, T1, T2, T3, T4> {
        R run(C context, T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    public interface ContextPostTask<C, R> {
        void run(C context, R r);
    }

    // only context

    public static <C> void invokeAsync(C context, ContextTask<C> task) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            task.run(c);
        });
    }

    public static <C, T1> void invokeAsync(C context,
        ContextTask1<C, T1> task,
        T1 arg1) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            task.run(c, arg1);
        });
    }

    public static <C, T1, T2> void invokeAsync(C context,
        ContextTask2<C, T1, T2> task,
        T1 arg1, T2 arg2) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            task.run(c, arg1, arg2);
        });
    }

    public static <C, T1, T2, T3> void invokeAsync(C context,
        ContextTask3<C, T1, T2, T3> task,
        T1 arg1, T2 arg2, T3 arg3) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            task.run(c, arg1, arg2, arg3);
        });
    }

    public static <C, T1, T2, T3, T4> void invokeAsync(C context,
        ContextTask4<C, T1, T2, T3, T4> task,
        T1 arg1, T2 arg2, T3 arg3, T4 arg4) {

        WeakReference<C> ctxRef = Ref.weak(context);

        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(ctxRef)) {
                return;
            }

            C c = ctxRef.get();
            task.run(c, arg1, arg2, arg3, arg4);
        });
    }

    public interface ContextTask<C> {
        void run(C context);
    }

    public interface ContextTask1<C, T1> {
        void run(C context, T1 arg1);
    }

    public interface ContextTask2<C, T1, T2> {
        void run(C context, T1 arg1, T2 arg2);
    }

    public interface ContextTask3<C, T1, T2, T3> {
        void run(C context, T1 arg1, T2 arg2, T3 arg3);
    }

    public interface ContextTask4<C, T1, T2, T3, T4> {
        void run(C context, T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    // only result

    public static <R> void invokeAsync(ResultTask<R> task,
        PostTask<R> post) {

        Engine.instance().getThreadPool().execute(() -> {
            R r = task.run();

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(r));
            }
        });
    }

    public static <R, T1> void invokeAsync(ResultTask1<R, T1> task,
        T1 arg1,
        PostTask<R> post) {

        Engine.instance().getThreadPool().execute(() -> {
            R r = task.run(arg1);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(r));
            }
        });
    }

    public static <R, T1, T2> void invokeAsync(ResultTask2<R, T1, T2> task,
        T1 arg1, T2 arg2,
        PostTask<R> post) {

        Engine.instance().getThreadPool().execute(() -> {
            R r = task.run(arg1, arg2);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(r));
            }
        });
    }

    public static <R, T1, T2, T3> void invokeAsync(ResultTask3<R, T1, T2, T3> task,
        T1 arg1, T2 arg2, T3 arg3,
        PostTask<R> post) {

        Engine.instance().getThreadPool().execute(() -> {
            R r = task.run(arg1, arg2, arg3);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(r));
            }
        });
    }

    public static <R, T1, T2, T3, T4> void invokeAsync(ResultTask4<R, T1, T2, T3, T4> task,
        T1 arg1, T2 arg2, T3 arg3, T4 arg4,
        PostTask<R> post) {

        Engine.instance().getThreadPool().execute(() -> {
            R r = task.run(arg1, arg2, arg3, arg4);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(r));
            }
        });
    }

    public interface ResultTask<R> {
        R run();
    }

    public interface ResultTask1<R, T1> {
        R run(T1 arg1);
    }

    public interface ResultTask2<R, T1, T2> {
        R run(T1 arg1, T2 arg2);
    }

    public interface ResultTask3<R, T1, T2, T3> {
        R run(T1 arg1, T2 arg2, T3 arg3);
    }

    public interface ResultTask4<R, T1, T2, T3, T4> {
        R run(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }

    public interface PostTask<R> {
        void run(R r);
    }

    // plain

    public static void invokeAsync(Task task) {
        Engine.instance().getThreadPool().execute(task::run);
    }

    public static <T1> void invokeAsync(Task1<T1> task, T1 arg1) {
        Engine.instance().getThreadPool().execute(() -> task.run(arg1));
    }

    public static <T1, T2> void invokeAsync(Task2<T1, T2> task, T1 arg1, T2 arg2) {
        Engine.instance().getThreadPool().execute(() -> task.run(arg1, arg2));
    }

    public static <T1, T2, T3> void invokeAsync(Task3<T1, T2, T3> task, T1 arg1, T2 arg2, T3 arg3) {
        Engine.instance().getThreadPool().execute(() -> task.run(arg1, arg2, arg3));
    }

    public static <T1, T2, T3, T4> void invokeAsync(Task4<T1, T2, T3, T4> task,
        T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        Engine.instance().getThreadPool().execute(() -> task.run(arg1, arg2, arg3, arg4));
    }

    public interface Task {
        void run();
    }

    public interface Task1<T1> {
        void run(T1 arg1);
    }

    public interface Task2<T1, T2> {
        void run(T1 arg1, T2 arg2);
    }

    public interface Task3<T1, T2, T3> {
        void run(T1 arg1, T2 arg2, T3 arg3);
    }

    public interface Task4<T1, T2, T3, T4> {
        void run(T1 arg1, T2 arg2, T3 arg3, T4 arg4);
    }
}
