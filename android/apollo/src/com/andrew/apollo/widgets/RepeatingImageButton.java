/*
 * Copyright (C) 2008 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.widgets;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import com.frostwire.android.R;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.ThemeUtils;
import com.andrew.apollo.widgets.theme.HoloSelector;

/**
 * A {@link ImageButton} that will repeatedly call a 'listener' method as long
 * as the button is pressed, otherwise functions like a typecal
 * {@link ImageButton}
 */
public class RepeatingImageButton extends ImageButton implements OnClickListener {

    /**
     * Next button theme resource
     */
    private static final String NEXT = "btn_playback_next";

    /**
     * Previous button theme resource
     */
    private static final String PREVIOUS = "btn_playback_previous";

    private static final long sInterval = 400;

    /**
     * The resources to use.
     */
    private final ThemeUtils mResources;

    private long mStartTime;

    private int mRepeatCount;

    private RepeatListener mListener;

    private int previousDrawable;
    private int nextDrawable;

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    @SuppressWarnings("deprecation")
    public RepeatingImageButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        mResources = new ThemeUtils(context);
        // Theme the selector
        setBackgroundDrawable(new HoloSelector(context));
        setFocusable(true);
        setLongClickable(true);
        setOnClickListener(this);
        updateState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.action_button_previous:
                MusicUtils.previous(getContext());
                break;
            case R.id.action_button_next:
                MusicUtils.next();
            default:
                break;
        }
    }

    /**
     * Sets the listener to be called while the button is pressed and the
     * interval in milliseconds with which it will be called.
     * 
     * @param l The listener that will be called
     * @param interval The interval in milliseconds for calls
     */
    public void setRepeatListener(final RepeatListener l) {
        mListener = l;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performLongClick() {
        if (mListener == null) {
            ApolloUtils.showCheatSheet(this);
        }
        mStartTime = SystemClock.elapsedRealtime();
        mRepeatCount = 0;
        post(mRepeater);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            /* Remove the repeater, but call the hook one more time */
            removeCallbacks(mRepeater);
            if (mStartTime != 0) {
                doRepeat(true);
                mStartTime = 0;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                /*
                 * Need to call super to make long press work, but return true
                 * so that the application doesn't get the down event
                 */
                super.onKeyDown(keyCode, event);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                /* Remove the repeater, but call the hook one more time */
                removeCallbacks(mRepeater);
                if (mStartTime != 0) {
                    doRepeat(true);
                    mStartTime = 0;
                }
        }
        return super.onKeyUp(keyCode, event);
    }

    private final Runnable mRepeater = new Runnable() {
        @Override
        public void run() {
            doRepeat(false);
            if (isPressed()) {
                postDelayed(this, sInterval);
            }
        }
    };

    /**
     * @param shouldRepeat If True the repeat count stops at -1, false if to add
     *            incrementally add the repeat count
     */
    private void doRepeat(final boolean shouldRepeat) {
        final long now = SystemClock.elapsedRealtime();
        if (mListener != null) {
            mListener.onRepeat(this, now - mStartTime, shouldRepeat ? -1 : mRepeatCount++);
        }
    }

    /**
     * Sets the correct drawable for playback.
     */
    public void updateState() {
        switch (getId()) {
            case R.id.action_button_next:
                if (nextDrawable == 0) {
                    setImageDrawable(mResources.getDrawable(NEXT));
                } else {
                    setImageResource(nextDrawable);
                }
                break;
            case R.id.action_button_previous:
                if (previousDrawable == 0) {
                    setImageDrawable(mResources.getDrawable(PREVIOUS));
                } else {
                    setImageResource(previousDrawable);
                }
                break;
            default:
                break;
        }
    }

    public void setPreviousDrawable(int resId) {
        this.previousDrawable = resId;
        updateState();
    }

    public void setNextDrawable(int resId) {
        this.nextDrawable = resId;
        updateState();
    }

    public interface RepeatListener {

        /**
         * @param v View to be set
         * @param duration Duration of the long press
         * @param repeatcount The number of repeat counts
         */
        void onRepeat(View v, long duration, int repeatcount);
    }

}
