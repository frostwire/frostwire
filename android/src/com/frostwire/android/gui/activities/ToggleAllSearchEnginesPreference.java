package com.frostwire.android.gui.activities;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import com.frostwire.android.R;

import java.util.List;

/**
 * Created by gubatron on 9/20/16.
 */
public class ToggleAllSearchEnginesPreference extends CheckBoxPreference {

    private CheckBox checkbox;
    private List<CheckBoxPreference> searchEnginePreferences;
    private boolean clickListenerEnabled;

    public ToggleAllSearchEnginesPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutResource(R.layout.view_checkbox_preference);
    }

    public ToggleAllSearchEnginesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.view_checkbox_preference);
    }

    public CheckBox getCheckbox() {
        return checkbox;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final TextView titleView = (TextView) view.findViewById(R.id.checkbox_preference_title);
        titleView.setText(getTitle());
        view.findViewById(R.id.checkbox_preference_summary).setVisibility(View.INVISIBLE);

        checkbox = (CheckBox) view.findViewById(R.id.checkbox_preference_checkbox);
        checkbox.setVisibility(View.VISIBLE);
        checkbox.setClickable(true);
        checkbox.setChecked(areAllEnginesChecked());

        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListenerEnabled) {
                    checkAllEngines(checkbox.isChecked());
                }
            }
        });

        clickListenerEnabled = true;

        return view;
    }

    public void setClickListenerEnabled(boolean enabled) {
        clickListenerEnabled = enabled;
    }

    private void checkAllEngines(boolean checked) {
        if (searchEnginePreferences == null) {
            return;
        }

        for (CheckBoxPreference preference : searchEnginePreferences) {
            if (preference != null) { //it could already have been removed due to remote config value.
                final OnPreferenceClickListener onPreferenceClickListener = preference.getOnPreferenceClickListener();
                preference.setOnPreferenceClickListener(null);
                preference.setChecked(checked);
                preference.setOnPreferenceClickListener(onPreferenceClickListener);
            }
        }
    }

    private boolean areAllEnginesChecked() {
        for (CheckBoxPreference preference : searchEnginePreferences) {
            if (preference != null) {
                if (!preference.isChecked()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setChecked(boolean checked) {
        if (checkbox != null) {
            checkbox.setChecked(checked);
        }
        super.setChecked(checked);
    }

    public void setSearchEnginePreferences(List<CheckBoxPreference> searchEnginePreferences) {
        this.searchEnginePreferences = searchEnginePreferences;
    }
}
