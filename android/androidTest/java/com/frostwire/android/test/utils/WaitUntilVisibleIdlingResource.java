package com.frostwire.android.test.utils;

import android.support.test.espresso.IdlingResource;
import android.view.View;

/**
 * Created by ralf on 5/1/16.
 */
public class WaitUntilVisibleIdlingResource implements IdlingResource {
    ResourceCallback mResourceCallback;

    View[] mViewsToCheck;

    public WaitUntilVisibleIdlingResource(View... viewsToCheck) {
        mViewsToCheck = viewsToCheck;
    }

    @Override
    public String getName() {
        return "WaitUntilVisible";
    }

    @Override
    public boolean isIdleNow() {
        for (View view : mViewsToCheck) {
            if (View.VISIBLE == view.getVisibility()) {
                mResourceCallback.onTransitionToIdle();
                return true;
            }
        }

        return false;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        mResourceCallback = callback;
    }
}