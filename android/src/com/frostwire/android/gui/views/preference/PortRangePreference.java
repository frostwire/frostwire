/*
 *     Created by Angel Leon (@gubatron)
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
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;

import java.util.Locale;
import java.util.Random;

/**
 * Preference for setting incoming connection port range
 */
public final class PortRangePreference extends DialogPreference {

    private int startPort = 1024; // Default values
    private int endPort = 57000;

    public PortRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_preference_port_range);
        loadCurrentValuesAsync();
        updateSummary(); // Show defaults initially
    }

    private void loadCurrentValuesAsync() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
            ConfigurationManager cm = ConfigurationManager.instance();
            int start = cm.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START);
            int end = cm.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END);
            
            // Post back to UI thread to update the values
            SystemUtils.postToUIThread(() -> onConfigurationManagerPortRange(start, end));
        });
    }
    
    private void onConfigurationManagerPortRange(int start, int end) {
        startPort = start;
        endPort = end;
        updateSummary();
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
            showRestartEngineDialog();
        }
    }
    
    private void showRestartEngineDialog() {
        // Show dialog using the preference fragment context
        try {
            Context context = getContext();
            if (context instanceof androidx.fragment.app.FragmentActivity) {
                androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
                
                // Create a simple confirmation dialog
                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(R.string.restart_torrent_engine_title)
                    .setMessage(R.string.restart_torrent_engine_message)
                    .setPositiveButton(R.string.restart_now, (dialogInterface, which) -> {
                        restartTorrentEngine();
                    })
                    .setNegativeButton(R.string.restart_later, (dialogInterface, which) -> {
                        UIUtils.showLongMessage(context, R.string.port_settings_saved_restart_later);
                    })
                    .create();
                    
                dialog.show();
            }
        } catch (Exception e) {
            // If we can't show the dialog, just show a toast
            UIUtils.showLongMessage(getContext(), R.string.port_settings_saved_restart_later);
        }
    }

    private void restartTorrentEngine() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
            try {
                restartTorrentEngineInternal();
            } catch (Exception e) {
                SystemUtils.postToUIThread(() -> 
                    UIUtils.showLongMessage(getContext(), getContext().getString(R.string.error_restarting_torrent_engine, e.getMessage())));
            }
        });
    }

    private void restartTorrentEngineInternal() {
        SystemUtils.postToUIThread(() -> 
            UIUtils.showLongMessage(getContext(), getContext().getString(R.string.restarting_torrent_engine)));
        
        try {
            // Step 1: Save resume data for all transfers
            TransferManager.instance().pauseTorrents();
            
            // Step 2: Stop the BTEngine
            BTEngine.getInstance().pause();
            Thread.sleep(1000); // Give it a moment to pause properly
            
            // Step 3: Update BTContext with new port settings
            updateBTContextWithNewPorts();
            
            // Step 4: Resume the BTEngine
            BTEngine.getInstance().resume();
            
            SystemUtils.postToUIThread(() -> 
                UIUtils.showLongMessage(getContext(), getContext().getString(R.string.torrent_engine_restarted)));
            
        } catch (Exception e) {
            SystemUtils.postToUIThread(() -> 
                UIUtils.showLongMessage(getContext(), getContext().getString(R.string.error_restarting_torrent_engine, e.getMessage())));
        }
    }

    private void updateBTContextWithNewPorts() {
        BTContext ctx = BTEngine.ctx;
        if (ctx != null) {
            // Get the new port configuration using the same logic as MainApplication
            int configuredStartPort = startPort;
            int configuredEndPort = endPort;
            
            int port0, port1;
            if (configuredStartPort == 1024 && configuredEndPort == 57000) {
                // Use default port range [37000, 57000] when user hasn't configured specific ports
                port0 = 37000 + new Random().nextInt(20000);
                port1 = port0 + 10; // 10 retries
            } else {
                // Use user-configured port range
                if (configuredStartPort == configuredEndPort) {
                    // Single port specified
                    port0 = configuredStartPort;
                    port1 = port0 + 1; // Just try the single port
                } else {
                    // Port range specified
                    port0 = configuredStartPort;
                    port1 = configuredEndPort;
                }
            }
            
            String iface = "0.0.0.0:%1$d,[::]:%1$d";
            ctx.interfaces = String.format(Locale.US, iface, port0);
            ctx.retries = port1 - port0;
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
                    // Reset to default values [1024, 57000]
                    startPortEditText.setText("1024");
                    endPortEditText.setText("57000");
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
                    
                    if (startPort < 1024 || startPort > 65535 || endPort < 1024 || endPort > 65535) {
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