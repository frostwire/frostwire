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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.frostwire.android.R;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.widgets.ColorPickerView.OnColorChangedListener;

import java.util.Locale;

/**
 * Shows the {@link ColorPanelView} in a new {@link AlertDialog}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ColorSchemeDialog extends AlertDialog implements
        ColorPickerView.OnColorChangedListener {

    private final int mCurrentColor;

    private final OnColorChangedListener mListener = this;;

    private LayoutInflater mInflater;

    private ColorPickerView mColorPicker;

    private Button mOldColor;

    private Button mNewColor;

    private View mRootView;

    private EditText mHexValue;

    /**
     * Constructor of <code>ColorSchemeDialog</code>
     * 
     * @param context The {@link Contxt} to use.
     */
    public ColorSchemeDialog(final Context context) {
        super(context);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        mCurrentColor = PreferenceUtils.getInstance(context).getDefaultThemeColor(context);
        setUp(mCurrentColor);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ApolloUtils.removeHardwareAccelerationSupport(mColorPicker);
    }

    /*
     * (non-Javadoc)
     * @see com.andrew.apollo.widgets.ColorPickerView.OnColorChangedListener#
     * onColorChanged(int)
     */
    @Override
    public void onColorChanged(final int color) {
        if (mHexValue != null) {
            mHexValue.setText(padLeft(Integer.toHexString(color).toUpperCase(Locale.getDefault()),
                    '0', 8));
        }
        mNewColor.setBackgroundColor(color);
    }

    private String padLeft(final String string, final char padChar, final int size) {
        if (string.length() >= size) {
            return string;
        }
        final StringBuilder result = new StringBuilder();
        for (int i = string.length(); i < size; i++) {
            result.append(padChar);
        }
        result.append(string);
        return result.toString();
    }

    /**
     * Initialzes the presets and color picker
     * 
     * @param color The color to use.
     */
    private void setUp(final int color) {
        mInflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = mInflater.inflate(R.layout.color_scheme_dialog, null);

        mColorPicker = (ColorPickerView)mRootView.findViewById(R.id.color_picker_view);
        mOldColor = (Button)mRootView.findViewById(R.id.color_scheme_dialog_old_color);
        mOldColor.setOnClickListener(mPresetListener);
        mNewColor = (Button)mRootView.findViewById(R.id.color_scheme_dialog_new_color);
        setUpPresets(R.id.color_scheme_dialog_preset_one);
        setUpPresets(R.id.color_scheme_dialog_preset_two);
        setUpPresets(R.id.color_scheme_dialog_preset_three);
        setUpPresets(R.id.color_scheme_dialog_preset_four);
        setUpPresets(R.id.color_scheme_dialog_preset_five);
        setUpPresets(R.id.color_scheme_dialog_preset_six);
        setUpPresets(R.id.color_scheme_dialog_preset_seven);
        setUpPresets(R.id.color_scheme_dialog_preset_eight);
        mHexValue = (EditText)mRootView.findViewById(R.id.color_scheme_dialog_hex_value);
        mHexValue.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) {
                try {
                    mColorPicker.setColor(Color.parseColor("#"
                            + mHexValue.getText().toString().toUpperCase(Locale.getDefault())));
                    mNewColor.setBackgroundColor(Color.parseColor("#"
                            + mHexValue.getText().toString().toUpperCase(Locale.getDefault())));
                } catch (final Exception ignored) {
                }
            }

            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) {
                /* Nothing to do */
            }

            @Override
            public void afterTextChanged(final Editable s) {
                /* Nothing to do */
            }
        });

        mColorPicker.setOnColorChangedListener(this);
        mOldColor.setBackgroundColor(color);
        mColorPicker.setColor(color, true);

        setTitle(R.string.color_picker_title);
        setView(mRootView);
    }

    /**
     * @param color The color resource.
     * @return A new color from Apollo's resources.
     */
    private int getColor(final int color) {
        return getContext().getResources().getColor(color);
    }

    /**
     * @return {@link ColorPickerView}'s current color
     */
    public int getColor() {
        return mColorPicker.getColor();
    }

    /**
     * @param which The Id of the preset color
     */
    private void setUpPresets(final int which) {
        final Button preset = (Button)mRootView.findViewById(which);
        if (preset != null) {
            preset.setOnClickListener(mPresetListener);
        }
    }

    /**
     * Sets up the preset buttons
     */
    private final View.OnClickListener mPresetListener = new View.OnClickListener() {

        @Override
        public void onClick(final View v) {
            switch (v.getId()) {
                case R.id.color_scheme_dialog_preset_one:
                    mColorPicker.setColor(getColor(R.color.holo_blue_light));
                    break;
                case R.id.color_scheme_dialog_preset_two:
                    mColorPicker.setColor(getColor(R.color.holo_green_light));
                    break;
                case R.id.color_scheme_dialog_preset_three:
                    mColorPicker.setColor(getColor(R.color.holo_orange_dark));
                    break;
                case R.id.color_scheme_dialog_preset_four:
                    mColorPicker.setColor(getColor(R.color.holo_orange_light));
                    break;
                case R.id.color_scheme_dialog_preset_five:
                    mColorPicker.setColor(getColor(R.color.holo_purple));
                    break;
                case R.id.color_scheme_dialog_preset_six:
                    mColorPicker.setColor(getColor(R.color.holo_red_light));
                    break;
                case R.id.color_scheme_dialog_preset_seven:
                    mColorPicker.setColor(getColor(R.color.white));
                    break;
                case R.id.color_scheme_dialog_preset_eight:
                    mColorPicker.setColor(getColor(R.color.black));
                    break;
                case R.id.color_scheme_dialog_old_color:
                    mColorPicker.setColor(mCurrentColor);
                    break;
                default:
                    break;
            }
            if (mListener != null) {
                mListener.onColorChanged(getColor());
            }
        }
    };

}
