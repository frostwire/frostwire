/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragment;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;

/**
 * Preference for setting incoming connection port range
 */
public final class PortRangePreference extends DialogPreference {

    private int startPort;
    private int endPort;

    public PortRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_preference_port_range);
        loadCurrentValues();
        updateSummary();
    }

    private void loadCurrentValues() {
        ConfigurationManager cm = ConfigurationManager.instance();
        startPort = cm.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START);
        endPort = cm.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END);
    }

    private void updateSummary() {
        setSummary(getContext().getString(R.string.port_range_current_range, startPort, endPort));
    }

    public int getStartPort() {
        return startPort;
    }

    public int getEndPort() {
        return endPort;
    }

    public void setPortRange(int start, int end) {
        if (start != startPort || end != endPort) {
            startPort = start;
            endPort = end;
            persistPortRange();
            updateSummary();
        }
    }

    private void persistPortRange() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
            ConfigurationManager cm = ConfigurationManager.instance();
            cm.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, startPort);
            cm.setInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END, endPort);
        });
    }

    public static final class PortRangePreferenceDialog extends PreferenceDialogFragment {

        private EditText startPortEditText;
        private EditText endPortEditText;

        public PortRangePreferenceDialog() {
        }

        public static PortRangePreferenceDialog newInstance(String key) {
            PortRangePreferenceDialog fragment = new PortRangePreferenceDialog();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        protected void onBindDialogView(@NonNull View view) {
            super.onBindDialogView(view);
            
            PortRangePreference preference = (PortRangePreference) getPreference();
            
            startPortEditText = view.findViewById(R.id.start_port_edit_text);
            endPortEditText = view.findViewById(R.id.end_port_edit_text);
            
            if (startPortEditText != null && endPortEditText != null) {
                startPortEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                endPortEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                
                startPortEditText.setText(String.valueOf(preference.getStartPort()));
                endPortEditText.setText(String.valueOf(preference.getEndPort()));
            }
        }

        @Override
        protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            
            builder.setNeutralButton(R.string.port_range_reset_default, null);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog dialog = (AlertDialog) super.onCreateDialog(savedInstanceState);
            
            dialog.setOnShowListener(dialogInterface -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    // Reset to default values [0, 65535]
                    startPortEditText.setText("0");
                    endPortEditText.setText("65535");
                });
            });
            
            return dialog;
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                try {
                    int startPort = Integer.parseInt(startPortEditText.getText().toString().trim());
                    int endPort = Integer.parseInt(endPortEditText.getText().toString().trim());
                    
                    if (startPort < 0 || startPort > 65535 || endPort < 0 || endPort > 65535) {
                        Toast.makeText(getContext(), R.string.port_range_invalid_port, Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    if (startPort > endPort) {
                        Toast.makeText(getContext(), R.string.port_range_invalid_range, Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    PortRangePreference preference = (PortRangePreference) getPreference();
                    preference.setPortRange(startPort, endPort);
                    
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), R.string.port_range_invalid_port, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}