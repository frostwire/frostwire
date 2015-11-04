
package com.andrew.apollo.widgets;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

import com.frostwire.android.R;

import java.util.LinkedHashMap;
import java.util.Map;

public class SeparatedListAdapter extends BaseAdapter {

    public final Map<String, Adapter> mSections = new LinkedHashMap<String, Adapter>();

    public final ArrayAdapter<String> mHeaders;

    public final static int TYPE_SECTION_HEADER = 0;

    /**
     * Constructor of <code>SeparatedListAdapter</code>
     * 
     * @param context The {@link Context} to use.
     */
    public SeparatedListAdapter(final Context context) {
        mHeaders = new ArrayAdapter<String>(context, R.layout.list_header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getItem(int position) {
        for (final Object section : mSections.keySet()) {
            final Adapter adapter = mSections.get(section);
            final int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0) {
                return section;
            }
            if (position < size) {
                return adapter.getItem(position - 1);
            }

            // otherwise jump into next section
            position -= size;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        // total together all mSections, plus one for each section header
        int total = 0;
        for (final Adapter adapter : mSections.values()) {
            total += adapter.getCount() + 1;
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getViewTypeCount() {
        // assume that mHeaders count as one, then total all mSections
        int total = 1;
        for (final Adapter adapter : mSections.values()) {
            total += adapter.getViewTypeCount();
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getItemViewType(int position) {
        int type = 1;
        for (final Object section : mSections.keySet()) {
            final Adapter adapter = mSections.get(section);
            final int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0) {
                return TYPE_SECTION_HEADER;
            }
            if (position < size) {
                return type + adapter.getItemViewType(position - 1);
            }

            // otherwise jump into next section
            position -= size;
            type += adapter.getViewTypeCount();
        }
        return -1;
    }

    public boolean areAllItemsSelectable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(final int position) {
        return getItemViewType(position) != TYPE_SECTION_HEADER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, final View convertView, final ViewGroup parent) {
        int sectionnum = 0;
        for (final Object section : mSections.keySet()) {
            final Adapter adapter = mSections.get(section);
            final int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0) {
                return mHeaders.getView(sectionnum, convertView, parent);
            }
            if (position < size) {
                return adapter.getView(position - 1, convertView, parent);
            }

            // otherwise jump into next section
            position -= size;
            sectionnum++;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(final int position) {
        return position;
    }

    public void addSection(final String section, final Adapter adapter) {
        mHeaders.add(section);
        mSections.put(section, adapter);
    }

}
