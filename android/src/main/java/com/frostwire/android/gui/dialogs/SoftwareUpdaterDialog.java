/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdaterDialog extends AbstractDialog {

    public SoftwareUpdaterDialog() {
        super(R.layout.dialog_default_update);
    }

    public static SoftwareUpdaterDialog newInstance(
            final String apkDownloadURL,
            final Map<String, String> updateMessages,
            final List<String> changelog) {
        SoftwareUpdaterDialog dlg = new SoftwareUpdaterDialog();

        Bundle args = new Bundle();
        args.putString("apkDownloadURL", apkDownloadURL);
        args.putSerializable("updateMessages", new HashMap<>(updateMessages));
        args.putStringArrayList("changelog", new ArrayList<>(changelog));
        dlg.setArguments(args);

        return dlg;
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        Bundle args = getArguments();
        HashMap<String, String> updateMessages = (HashMap<String, String>) args.getSerializable("updateMessages");
        ArrayList<String> changelog = args.getStringArrayList("changelog");

        String message = StringUtils.getLocaleString(updateMessages, getString(R.string.update_message));

        TextView title = findView(dlg, R.id.dialog_default_update_title);
        title.setText(R.string.update_title);

        TextView text = findView(dlg, R.id.dialog_default_update_text);
        text.setText(message);

        final ListView listview = findView(dlg, R.id.dialog_default_update_list_view);

        if (changelog != null) {
            String[] values = new String[changelog.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = String.valueOf(Html.fromHtml("&#8226; " + changelog.get(i)));
            }
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                    R.layout.dialog_update_bullet,
                    R.id.dialog_update_bullets_checked_text_view,
                    values);
            listview.setAdapter(adapter);
        }

        // Set the save button action
        Button noButton = findView(dlg, R.id.dialog_default_update_button_no);
        noButton.setText(R.string.cancel);

        Button yesButton = findView(dlg, R.id.dialog_default_update_button_yes);
        yesButton.setText(android.R.string.ok);
        yesButton.setOnClickListener(this::onYesClick);
        noButton.setOnClickListener(v -> dismiss());
    }

    private void onYesClick(View v) {
        // Google Play distribution (even if it's a dev. build)
        Bundle arguments = getArguments();
        String apkDownloadURL = arguments.getString("apkDownloadURL");

        if (apkDownloadURL != null) {
            UIUtils.openURL(getActivity(), apkDownloadURL);
            dismiss();
            return;
        }
        UIUtils.openURL(getActivity(),
                Constants.IS_GOOGLE_PLAY_DISTRIBUTION ?
                        Constants.FROSTWIRE_ANDROID_GOOGLE_PLAY_URL :
                        Constants.FROSTWIRE_ANDROID_DOWNLOAD_PAGE_URL);
        dismiss();
    }
}
