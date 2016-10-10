package com.frostwire.android.test.utils;

import android.support.test.espresso.IdlingResource;
import android.widget.ListView;


public class WaitUntilListViewNotEmptyIdlingResource implements IdlingResource {
    ResourceCallback mResourceCallback;

    ListView mListViewToCheck;

    public WaitUntilListViewNotEmptyIdlingResource(ListView listView) {
        mListViewToCheck = listView;
    }

    @Override
    public String getName() {
        return "WaitUntilListViewNotEmptyIdlingResource";
    }

    @Override
    public boolean isIdleNow() {
        boolean isIdle = mListViewToCheck.getAdapter().getCount() > 0;

        if(isIdle) {
            mResourceCallback.onTransitionToIdle();
        }
        return isIdle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        mResourceCallback = callback;
    }
}