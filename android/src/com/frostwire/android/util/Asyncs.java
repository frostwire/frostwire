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

import androidx.annotation.NonNull;

/**
 * Utility class for asynchronous task in the background.
 *
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("unchecked")
public final class Asyncs {

    private Asyncs() {
    }

    // context + return

    public static <C, R> void async(@NonNull C context,
                                    ContextResultTask<C, R> task,
                                    ContextResultPostTask<C, R> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> task.run(c),
            (c, args, r) -> post.run(c, r));
    }

    public static <C, T1, R> void async(@NonNull C context,
                                        ContextResultTask1<C, T1, R> task,
                                        T1 arg1,
                                        ContextResultPostTask1<C, T1, R> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> task.run(c, (T1)args[0]),
            (c, args, r) -> post.run(c, (T1)args[0], r),
            arg1);
    }

    public static <C, T1, T2, R> void async(@NonNull C context,
                                            ContextResultTask2<C, T1, T2, R> task,
                                            T1 arg1, T2 arg2,
                                            ContextResultPostTask2<C, T1, T2, R> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> task.run(c, (T1)args[0], (T2)args[1]),
            (c, args, r) -> post.run(c, (T1)args[0], (T2)args[1], r),
            arg1, arg2);
    }

    public static <C, T1, T2, T3, R> void async(@NonNull C context,
                                                ContextResultTask3<C, T1, T2, T3, R> task,
                                                T1 arg1, T2 arg2, T3 arg3,
                                                ContextResultPostTask3<C, T1, T2, T3, R> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> task.run(c, (T1)args[0], (T2)args[1], (T3)args[2]),
            (c, args, r) -> post.run(c, (T1)args[0], (T2)args[1], (T3)args[2], r),
            arg1, arg2, arg3);
    }

    public interface ContextResultTask<C, R> {
        R run(C context);
    }

    public interface ContextResultTask1<C, T1, R> {
        R run(C context, T1 arg1);
    }

    public interface ContextResultTask2<C, T1, T2, R> {
        R run(C context, T1 arg1, T2 arg2);
    }

    public interface ContextResultTask3<C, T1, T2, T3, R> {
        R run(C context, T1 arg1, T2 arg2, T3 arg3);
    }

    public interface ContextResultPostTask<C, R> {
        void run(C context, R r);
    }

    public interface ContextResultPostTask1<C, T1, R> {
        void run(C context, T1 arg1, R r);
    }

    public interface ContextResultPostTask2<C, T1, T2, R> {
        void run(C context, T1 arg1, T2 arg2, R r);
    }

    public interface ContextResultPostTask3<C, T1, T2, T3, R> {
        void run(C context, T1 arg1, T2 arg2, T3 arg3, R r);
    }

    // only context

    public static <C> void async(@NonNull C context, ContextTask<C> task) {
        requireContext(context);
        invokeAsyncSupport(context,
            (c, args) -> {task.run(c); return null;},
            null);
    }

    public static <C, T1> void async(C context,
                                     ContextTask1<C, T1> task,
                                     T1 arg1) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args) -> {task.run(c, (T1)args[0]); return null;},
            null,
            arg1);
    }

    public static <C, T1, T2> void async(@NonNull C context,
                                         ContextTask2<C, T1, T2> task,
                                         T1 arg1, T2 arg2) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args) -> {task.run(c, (T1)args[0], (T2)args[1]); return null;},
            null,
            arg1, arg2);
    }

    public static <C, T1, T2, T3> void async(@NonNull C context,
                                             ContextTask3<C, T1, T2, T3> task,
                                             T1 arg1, T2 arg2, T3 arg3) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args) -> {task.run(c, (T1)args[0], (T2)args[1], (T3)args[2]); return null;},
            null,
            arg1, arg2, arg3);
    }

    public static <C, T1, T2, T3, T4> void async(@NonNull C context,
                                             ContextTask4<C, T1, T2, T3, T4> task,
                                             T1 arg1, T2 arg2, T3 arg3, T4 arg4) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args) -> {task.run(c, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3]); return null;},
            null,
            arg1, arg2, arg3, arg4);
    }

    public static <C, T1> void async(@NonNull C context,
                                     ContextTask1<C, T1> task,
                                     T1 arg1,
                                     ContextPostTask1<C, T1> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> {task.run(c, (T1)args[0]); return null;},
            (c, args, r) -> post.run(c, (T1)args[0]),
            arg1);
    }

    public static <C, T1, T2> void async(@NonNull C context,
                                         ContextTask2<C, T1, T2> task,
                                         T1 arg1, T2 arg2,
                                         ContextPostTask2<C, T1, T2> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> {task.run(c, (T1)args[0], (T2)args[1]); return null;},
            (c, args, r) -> post.run(c, (T1)args[0], (T2)args[1]),
            arg1, arg2);
    }

    public static <C, T1, T2, T3> void async(@NonNull C context,
                                             ContextTask3<C, T1, T2, T3> task,
                                             T1 arg1, T2 arg2, T3 arg3,
                                             ContextPostTask3<C, T1, T2, T3> post) {

        requireContext(context);
        invokeAsyncSupport(context,
            (c, args)-> {task.run(c, (T1)args[0], (T2)args[1], (T3)args[2]); return null;},
            (c, args, r) -> post.run(c, (T1)args[0], (T2)args[1], (T3)args[2]),
            arg1, arg2, arg3);
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

    public interface ContextPostTask1<C, T1> {
        void run(C context, T1 arg1);
    }

    public interface ContextPostTask2<C, T1, T2> {
        void run(C context, T1 arg1, T2 arg2);
    }

    public interface ContextPostTask3<C, T1, T2, T3> {
        void run(C context, T1 arg1, T2 arg2, T3 arg3);
    }

    // only result

    public static <R> void async(ResultTask<R> task,
                                 ResultPostTask<R> post) {

        invokeAsyncSupport(null,
            (c, args)-> task.run(),
            (c, args, r) -> post.run(r));
    }

    public static <T1, R> void async(ResultTask1<T1, R> task,
                                     T1 arg1,
                                     ResultPostTask1<T1, R> post) {

        invokeAsyncSupport(null,
            (c, args)-> task.run((T1)args[0]),
            (c, args, r) -> post.run((T1)args[0], r),
            arg1);
    }

    public static <T1, T2, R> void async(ResultTask2<T1, T2, R> task,
                                         T1 arg1, T2 arg2,
                                         ResultPostTask2<T1, T2, R> post) {

        invokeAsyncSupport(null,
            (c, args)-> task.run((T1)args[0], (T2)args[1]),
            (c, args, r) -> post.run((T1)args[0], (T2)args[1], r),
            arg1, arg2);
    }

    public static <T1, T2, T3, R> void async(ResultTask3<T1, T2, T3, R> task,
                                             T1 arg1, T2 arg2, T3 arg3,
                                             ResultPostTask3<T1, T2, T3, R> post) {

        invokeAsyncSupport(null,
            (c, args)-> task.run((T1)args[0], (T2)args[1], (T3)args[2]),
            (c, args, r) -> post.run((T1)args[0], (T2)args[1], (T3)args[2], r),
            arg1, arg2, arg3);
    }

    public interface ResultTask<R> {
        R run();
    }

    public interface ResultTask1<T1, R> {
        R run(T1 arg1);
    }

    public interface ResultTask2<T1, T2, R> {
        R run(T1 arg1, T2 arg2);
    }

    public interface ResultTask3<T1, T2, T3, R> {
        R run(T1 arg1, T2 arg2, T3 arg3);
    }

    public interface ResultPostTask<R> {
        void run(R r);
    }

    public interface ResultPostTask1<T1, R> {
        void run(T1 arg1, R r);
    }

    public interface ResultPostTask2<T1, T2, R> {
        void run(T1 arg1, T2 arg2, R r);
    }

    public interface ResultPostTask3<T1, T2, T3, R> {
        void run(T1 arg1, T2 arg2, T3 arg3, R r);
    }

    // plain

    public static void async(Task task) {
        invokeAsyncSupport(null,
            (c, args)-> {task.run(); return null;},
            null);
    }

    public static <T1> void async(Task1<T1> task,
                                  T1 arg1) {

        invokeAsyncSupport(null,
            (c, args)-> {task.run((T1)args[0]); return null;},
            null,
            arg1);
    }

    public static <T1, T2> void async(Task2<T1, T2> task,
                                      T1 arg1, T2 arg2) {

        invokeAsyncSupport(null,
            (c, args)-> {task.run((T1)args[0], (T2)args[1]); return null;},
            null,
            arg1, arg2);
    }

    public static <T1, T2, T3> void async(Task3<T1, T2, T3> task,
                                          T1 arg1, T2 arg2, T3 arg3) {
        invokeAsyncSupport(null,
            (c, args)-> {task.run((T1)args[0], (T2)args[1], (T3)args[2]); return null;},
            null,
            arg1, arg2, arg3);
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

    // private helper methods

    private static <C, R> void invokeAsyncSupport(C context,
        TaskSupport<C, R> task,
        PostSupport<C, R> post,
        Object... args) {

        WeakReference<C> ctx = context != null ? Ref.weak(context) : null;

        Engine.instance().getThreadPool().execute(() -> {
            if (ctx != null && !Ref.alive(ctx)) {
                return;
            }

            C c = ctx != null ? ctx.get() : null;
            R r = task.run(c, args);

            if (post != null) {
                new Handler(Looper.getMainLooper()).post(() -> post.run(c, args, r));
            }
        });
    }

    private static <C> void requireContext(C context) {
        if (context == null) {
            throw new IllegalArgumentException("Argument 'context' can't be null");
        }
    }

    private interface TaskSupport<C, R> {
        R run(C context, Object[] args);
    }

    private interface PostSupport<C, R> {
        void run(C context, Object[] args, R result);
    }
}
