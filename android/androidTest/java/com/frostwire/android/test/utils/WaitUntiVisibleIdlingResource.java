package com.frostwire.android.test.utils;

import android.support.test.espresso.IdlingResource;
import android.view.View;


public class WaitUntiVisibleIdlingResource implements IdlingResource {
    ResourceCallback mResourceCallback;

    View[] mViewsToCheck;

    public WaitUntiVisibleIdlingResource(View... viewsToCheck) {
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
