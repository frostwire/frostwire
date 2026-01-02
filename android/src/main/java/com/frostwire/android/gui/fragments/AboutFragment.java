/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.LibTorrent;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.frostwire.android.gui.util.UIUtils.setupClickUrl;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class AboutFragment extends AbstractFragment {

    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {

        //Title, build number and changelog setup
        TextView title = findView(rootView, R.id.fragment_about_title);
        String basicOrPlus = (String) getText(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
        title.setText("FrostWire " + basicOrPlus + " v" + Constants.FROSTWIRE_VERSION_STRING);

        TextView buildNumber = findView(rootView, R.id.fragment_about_build_number);
        buildNumber.setText(getText(R.string.build) + " " + BuildConfig.VERSION_CODE + ", sdk level " + Build.VERSION.SDK_INT);

        TextView jlibtorrentVersion = findView(rootView, R.id.fragment_about_jlibtorrent_version);
        jlibtorrentVersion.setText(jlibtorrentVersion());

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
                () -> TellurideCourier.ytDlpVersion(
                        (version) -> SystemUtils.postToUIThread(() -> {
                            TextView ytDlpVersion = findView(rootView, R.id.fragment_about_yt_dlp_version);
                            ytDlpVersion.setText(String.format("yt_dlp %s", version));
                        })));

        TextView changelog = findView(rootView, R.id.fragment_about_changelog);
        setupClickUrl(changelog, Constants.CHANGELOG_URL);

        //Love FrostWire button and social media icons
        Button loveFrostWireButton = findView(rootView, R.id.fragment_about_love_frostwire);
        setupClickUrl(loveFrostWireButton, Constants.FROSTWIRE_GIVE_URL + "plus-about");

        ImageButton facebookButton = findView(rootView, R.id.fragment_about_facebook_button);
        ImageButton twitterButton = findView(rootView, R.id.fragment_about_twitter_button);
        ImageButton redditButton = findView(rootView, R.id.fragment_about_reddit_button);
        ImageButton githubButton = findView(rootView, R.id.fragment_about_github_button);
        ImageButton slackButton = findView(rootView, R.id.fragment_about_slack_button);

        String referrerParam = "?ref=android_about";
        setupClickUrl(facebookButton, Constants.SOCIAL_URL_FACEBOOK_PAGE + referrerParam);
        setupClickUrl(twitterButton, Constants.SOCIAL_URL_TWITTER_PAGE + referrerParam);
        setupClickUrl(redditButton, Constants.SOCIAL_URL_REDDIT_PAGE + referrerParam);
        setupClickUrl(githubButton, Constants.SOCIAL_URL_GITHUB_PAGE + referrerParam);
        setupClickUrl(slackButton, Constants.SOCIAL_URL_SLACK_PAGE + referrerParam);

        //Remaining elements including text content
        TextView supportFrostWire = findView(rootView, R.id.fragment_about_support_frostwire);
        ImageView supportFrostWireDivider = findView(rootView, R.id.fragment_about_support_frostwire_divider);
        TextView translateHelp = findView(rootView, R.id.fragment_about_translate);
        TextView contactUs = findView(rootView, R.id.fragment_about_contact_us);

        setupClickUrl(supportFrostWire, Constants.STICKERS_SHOP_URL);
        setupClickUrl(translateHelp, Constants.TRANSLATE_HELP_URL);
        setupClickUrl(contactUs, Constants.CONTACT_US_URL);

        TextView content = findView(rootView, R.id.fragment_about_content);
        content.setText(Html.fromHtml(getAboutText()));
        content.setMovementMethod(LinkMovementMethod.getInstance());

        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            loveFrostWireButton.setVisibility(View.GONE);
            supportFrostWire.setVisibility(View.GONE);
            supportFrostWireDivider.setVisibility(View.GONE);
        }
    }

    private String getAboutText() {
        try {
            InputStream raw = getResources().openRawResource(R.raw.about);
            return IOUtils.toString(raw, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }

    private static String jlibtorrentVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append("jlibtorrent v").append(LibTorrent.jlibtorrentVersion());

        if (LibTorrent.hasArmNeonSupport()) {
            sb.append("(arm neon)");
        }

        return sb.toString();
    }
}
