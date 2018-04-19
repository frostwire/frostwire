/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.widgets;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.AnimationUtils;
import android.widget.HorizontalScrollView;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.frostwire.android.R;

/**
 * A custom {@link HorizontalScrollView} that displays up to two "tabs" in the
 * {@link ProfileActivity}. If the second tab is visible, a fraction of it will
 * overflow slightly onto the screen.
 */
public class ProfileTabCarousel extends HorizontalScrollView implements OnTouchListener {

    /**
     * Number of tabs
     */
    private static final int TAB_COUNT = 2;

    /**
     * First tab index
     */
    private static final int TAB_INDEX_FIRST = 0;

    /**
     * Second tab index
     */
    private static final int TAB_INDEX_SECOND = 1;

    /**
     * Alpha layer to be set on the lable view
     */
    private static final float MAX_ALPHA = 0.6f;

    /**
     * Y coordinate of the tab at the given index was selected
     */
    private static final float[] mYCoordinateArray = new float[TAB_COUNT];

    /**
     * Tab width as defined as a fraction of the screen width
     */
    private final float tabWidthScreenWidthFraction;

    /**
     * Tab height as defined as a fraction of the screen width
     */
    private final float tabHeightScreenWidthFraction;

    /**
     * Height of the tab label
     */
    private final int mTabDisplayLabelHeight;

    /**
     * First tab click listener
     */
    private final TabClickListener mTabOneTouchInterceptListener = new TabClickListener(
            TAB_INDEX_FIRST);

    /**
     * Second tab click listener
     */
    private final TabClickListener mTabTwoTouchInterceptListener = new TabClickListener(
            TAB_INDEX_SECOND);

    /**
     * The last scrolled position
     */
    private int mLastScrollPosition = Integer.MIN_VALUE;

    /**
     * Allowed horizontal scroll length
     */
    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;

    /**
     * Allowed vertical scroll length
     */
    private int mAllowedVerticalScrollLength = Integer.MIN_VALUE;

    /**
     * Current tab index
     */
    private int mCurrentTab = TAB_INDEX_FIRST;

    /**
     * Factor to scale scroll-amount sent to listeners
     */
    private float mScrollScaleFactor = 1.0f;

    private boolean mScrollToCurrentTab = false;

    private boolean mTabCarouselIsAnimating;

    private boolean mEnableSwipe;

    private CarouselTab mFirstTab;

    private CarouselTab mSecondTab;

    private Listener mListener;

    /**
     * @param context The {@link Context} to use
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public ProfileTabCarousel(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
        final Resources mResources = context.getResources();
        mTabDisplayLabelHeight = mResources.getDimensionPixelSize(R.dimen.profile_carousel_label_height);
        tabWidthScreenWidthFraction = mResources.getFraction(
                R.fraction.tab_width_screen_percentage, 1, 1);
        tabHeightScreenWidthFraction = mResources.getFraction(
                R.fraction.tab_height_screen_percentage, 1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mFirstTab = findViewById(R.id.profile_tab_carousel_tab_one);
        mFirstTab.setOverlayOnClickListener(mTabOneTouchInterceptListener);
        mSecondTab = findViewById(R.id.profile_tab_carousel_tab_two);
        mSecondTab.setOverlayOnClickListener(mTabTwoTouchInterceptListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int tabWidth = Math.round(tabWidthScreenWidthFraction * screenWidth);

        mAllowedHorizontalScrollLength = tabWidth * TAB_COUNT - screenWidth;
        if (mAllowedHorizontalScrollLength == 0) {
            mScrollScaleFactor = 1.0f;
        } else {
            mScrollScaleFactor = screenWidth / mAllowedHorizontalScrollLength;
        }

        final int tabHeight = Math.round(screenWidth * tabHeightScreenWidthFraction)
                + mTabDisplayLabelHeight;
        if (getChildCount() > 0) {
            final View child = getChildAt(0);

            // Add 1 dip of separation between the tabs
            final int separatorPixels = (int) (TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()) + 0.5f);

            if (mEnableSwipe) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(TAB_COUNT * tabWidth + (TAB_COUNT - 1)
                                * separatorPixels, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
            } else {
                child.measure(MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
            }
        }
        mAllowedVerticalScrollLength = tabHeight - mTabDisplayLabelHeight;
        setMeasuredDimension(resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(tabHeight, heightMeasureSpec));
        updateAlphaLayers();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r,
            final int b) {
        super.onLayout(changed, l, t, r, b);
        if (!mScrollToCurrentTab) {
            return;
        }
        mScrollToCurrentTab = false;
        ApolloUtils.doAfterLayout(this, new Runnable() {
            @Override
            public void run() {
                scrollTo(mCurrentTab == TAB_INDEX_FIRST ? 0 : mAllowedHorizontalScrollLength, 0);
                updateAlphaLayers();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onScrollChanged(final int x, final int y, final int oldX, final int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (mLastScrollPosition == x) {
            return;
        }
        final int scaledL = (int)(x * mScrollScaleFactor);
        final int oldScaledL = (int)(oldX * mScrollScaleFactor);
        mListener.onScrollChanged(scaledL, y, oldScaledL, oldY);
        mLastScrollPosition = x;
        updateAlphaLayers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mListener.onTouchDown();
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onTouchUp();
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        final boolean mInterceptTouch = super.onInterceptTouchEvent(ev);
        if (mInterceptTouch) {
            mListener.onTouchDown();
        }
        return mInterceptTouch;
    }

    /**
     * Reset the carousel to the start position (i.e. because new data will be
     * loaded in for a different contact).
     */
    public void reset() {
        scrollTo(0, 0);
        setCurrentTab(TAB_INDEX_FIRST);
        moveToYCoordinate(TAB_INDEX_FIRST, 0);
    }

    /**
     * Set the current tab that should be restored when the view is first laid
     * out.
     */
    public void restoreCurrentTab(final int position) {
        setCurrentTab(position);
        mScrollToCurrentTab = true;
    }

    /**
     * Restore the Y position of this view to the last manually requested value.
     * This can be done after the parent has been re-laid out again, where this
     * view's position could have been lost if the view laid outside its
     * parent's bounds.
     */
    public void restoreYCoordinate(final int duration, final int tabIndex) {
        final float storedYCoordinate = getStoredYCoordinateForTab(tabIndex);

        final ObjectAnimator tabCarouselAnimator = ObjectAnimator.ofFloat(this, "y",
                storedYCoordinate);
        tabCarouselAnimator.addListener(mTabCarouselAnimatorListener);
        tabCarouselAnimator.setInterpolator(AnimationUtils.loadInterpolator(getContext(),
                android.R.anim.accelerate_decelerate_interpolator));
        tabCarouselAnimator.setDuration(duration);
        tabCarouselAnimator.start();
    }

    /**
     * Request that the view move to the given Y coordinate. Also store the Y
     * coordinate as the last requested Y coordinate for the given tabIndex.
     */
    public void moveToYCoordinate(final int tabIndex, final float y) {
        storeYCoordinate(tabIndex, y);
        restoreYCoordinate(0, tabIndex);
    }

    /**
     * Store this information as the last requested Y coordinate for the given
     * tabIndex.
     */
    public void storeYCoordinate(final int tabIndex, final float y) {
        mYCoordinateArray[tabIndex] = y;
    }

    /**
     * Returns the stored Y coordinate of this view the last time the user was
     * on the selected tab given by tabIndex.
     */
    public float getStoredYCoordinateForTab(final int tabIndex) {
        return mYCoordinateArray[tabIndex];
    }

    /**
     * Returns the number of pixels that this view can be scrolled horizontally.
     */
    public int getAllowedHorizontalScrollLength() {
        return mAllowedHorizontalScrollLength;
    }

    /**
     * Returns the number of pixels that this view can be scrolled vertically
     * while still allowing the tab labels to still show.
     */
    public int getAllowedVerticalScrollLength() {
        return mAllowedVerticalScrollLength;
    }

    /**
     * Sets the correct alpha layers over the tabs.
     */
    private void updateAlphaLayers() {
        float alpha = mLastScrollPosition * MAX_ALPHA / mAllowedHorizontalScrollLength;
        alpha = AlphaTouchInterceptorOverlay.clamp(alpha, 0.0f, 1.0f);
        mFirstTab.setAlphaLayerValue(alpha);
        mSecondTab.setAlphaLayerValue(MAX_ALPHA - alpha);
    }

    /**
     * This listener keeps track of whether the tab carousel animation is
     * currently going on or not, in order to prevent other simultaneous changes
     * to the Y position of the tab carousel which can cause flicker.
     */
    private final AnimatorListener mTabCarouselAnimatorListener = new AnimatorListener() {

        @Override
        public void onAnimationCancel(final Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        @Override
        public void onAnimationEnd(final Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        @Override
        public void onAnimationRepeat(final Animator animation) {
            mTabCarouselIsAnimating = true;
        }

        @Override
        public void onAnimationStart(final Animator animation) {
            mTabCarouselIsAnimating = true;
        }
    };

    /**
     * @return True if the carousel is currently animating, false otherwise
     */
    public boolean isTabCarouselIsAnimating() {
        return mTabCarouselIsAnimating;
    }

    /**
     * Updates the tab selection.
     */
    public void setCurrentTab(final int position) {
        final CarouselTab selected, deselected;

        switch (position) {
            case TAB_INDEX_FIRST:
                selected = mFirstTab;
                deselected = mSecondTab;
                break;
            case TAB_INDEX_SECOND:
                selected = mSecondTab;
                deselected = mFirstTab;
                break;
            default:
                throw new IllegalStateException("Invalid tab position " + position);
        }
        selected.setSelected(true);
        selected.showSelectedState();
        selected.setOverlayClickable(false);
        deselected.setSelected(false);
        deselected.showDeselectedState();
        deselected.setOverlayClickable(true);
        mCurrentTab = position;
    }

    /**
     * Set the given {@link Listener} to handle carousel events.
     */
    public void setListener(final Listener listener) {
        mListener = listener;
    }

    /**
     * Sets the artist image header
     * 
     * @param context The {@link Activity} to use
     * @param artistName The artist name used to find the cached artist image
     *            and used to find the last album played by the artist
     */
    public void setArtistProfileHeader(final Activity context,
            final String artistName) {
        mFirstTab.setLabel(getResources().getString(R.string.page_songs));
        mSecondTab.setLabel(getResources().getString(R.string.page_albums));
        mFirstTab.setArtistPhoto(context, artistName);
        mSecondTab.setArtistAlbumPhoto(context, artistName);
        mEnableSwipe = true;
    }

    /**
     * Sets the album image header
     * 
     * @param context The {@link Activity} to use
     * @param albumName The key used to find the cached album art
     * @param artistName The artist name used to find the cached artist image
     */
    public void setAlbumProfileHeader(final Activity context,
            final String albumName, final String artistName) {
        mFirstTab.setLabel(getResources().getString(R.string.page_songs));
        mFirstTab.setAlbumPhoto(context, albumName, artistName);
        mFirstTab.blurPhoto(context, artistName, albumName);
        mSecondTab.setVisibility(View.GONE);
        mEnableSwipe = false;
    }

    /**
     * Sets the playlist or genre image header
     * 
     * @param context The {@link Activity} to use
     * @param profileName The key used to find the cached image for a playlist
     *            or genre
     */
    public void setPlaylistOrGenreProfileHeader(final Activity context,
            final String profileName) {
        mFirstTab.setDefault(context);
        mFirstTab.setLabel(getResources().getString(R.string.page_songs));
        mFirstTab.setPlaylistOrGenrePhoto(context, profileName);
        mSecondTab.setVisibility(View.GONE);
        mEnableSwipe = false;
    }

    /** When clicked, selects the corresponding tab. */
    private final class TabClickListener implements OnClickListener {
        private final int mTab;

        public TabClickListener(final int tab) {
            super();
            mTab = tab;
        }

        @Override
        public void onClick(final View v) {
            mListener.onTabSelected(mTab);
        }
    }

    /**
     * Interface for callbacks invoked when the user interacts with the
     * carousel.
     */
    public interface Listener {
        void onTouchDown();

        void onTouchUp();

        void onScrollChanged(int l, int t, int oldl, int oldt);

        void onTabSelected(int position);
    }

}
