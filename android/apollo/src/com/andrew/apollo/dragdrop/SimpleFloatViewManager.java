
package com.andrew.apollo.dragdrop;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * Simple implementation of the FloatViewManager class. Uses list items as they
 * appear in the ListView to create the floating View.
 */
public class SimpleFloatViewManager implements DragSortListView.FloatViewManager {

    private final ListView mListView;

    private Bitmap mFloatBitmap;

    private int mFloatBGColor = Color.BLACK;

    public SimpleFloatViewManager(ListView lv) {
        mListView = lv;
    }

    public void setBackgroundColor(int color) {
        mFloatBGColor = color;
    }

    /**
     * This simple implementation creates a Bitmap copy of the list item
     * currently shown at ListView <code>position</code>.
     */
    @Override
    public View onCreateFloatView(int position) {
        View v = mListView.getChildAt(position + mListView.getHeaderViewsCount()
                - mListView.getFirstVisiblePosition());

        if (v == null) {
            return null;
        }

        v.setPressed(false);

        v.setDrawingCacheEnabled(true);
        final Bitmap drawingCache = v.getDrawingCache();

        if (drawingCache != null) {
            mFloatBitmap = Bitmap.createBitmap(drawingCache);
            v.setDrawingCacheEnabled(false);

            ImageView iv = new ImageView(mListView.getContext());
            iv.setBackgroundColor(mFloatBGColor);
            iv.setPadding(0, 0, 0, 0);
            iv.setImageBitmap(mFloatBitmap);

            return iv;
        } else {
            v.setDrawingCacheEnabled(false);
            return null;
        }
    }

    /**
     * Removes the Bitmap from the ImageView created in onCreateFloatView() and
     * tells the system to recycle it.
     */
    @Override
    public void onDestroyFloatView(View floatView) {
        ((ImageView)floatView).setImageDrawable(null);

        mFloatBitmap.recycle();
        mFloatBitmap = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDragFloatView(View floatView, Point position, Point touch) {
        /* Nothing to do */
    }
}
