/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.adapters.menu.CancelMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.adapters.menu.PauseDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.ResumeDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendBitcoinTipAction;
import com.frostwire.android.gui.adapters.menu.SendFiatTipAction;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.TransferState;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class TransferDetailFragment extends AbstractFragment {

    private UIBittorrentDownload uiBittorrentDownload;
    private MenuItem pauseResumeMenuItem;

    public TransferDetailFragment() {
        super(R.layout.fragment_transfer_detail);
        setHasOptionsMenu(true);
    }

    public void setUiBittorrentDownload(UIBittorrentDownload uiBittorrentDownload) {
        this.uiBittorrentDownload = uiBittorrentDownload;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_transfer_detail_menu, menu);
        pauseResumeMenuItem = menu.findItem(R.id.fragment_transfer_detail_menu_pause_resume_seed);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private boolean isPausable() {
        return uiBittorrentDownload != null && uiBittorrentDownload.getState() != TransferState.FINISHED && (!uiBittorrentDownload.isPaused() || uiBittorrentDownload.getState() == TransferState.SEEDING);
    }

    private boolean isResumable() {
        if (isPausable()) {
            return false;
        }
        boolean wifiIsUp = NetworkManager.instance().isDataWIFIUp();
        boolean bittorrentOnMobileData = !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
        return wifiIsUp || bittorrentOnMobileData;
    }

    private boolean isSeedable() {
        return uiBittorrentDownload != null && uiBittorrentDownload.getState() == TransferState.FINISHED;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (uiBittorrentDownload == null) {
            return;
        }
        updatePauseResumeSeedMenuAction();
        MenuItem fiatMenuItem = menu.findItem(R.id.fragment_transfer_detail_menu_donate_fiat);
        MenuItem bitcoinMenuItem = menu.findItem(R.id.fragment_transfer_detail_menu_donate_bitcoin);
        if (!uiBittorrentDownload.hasPaymentOptions()) {
            fiatMenuItem.setVisible(false);
            bitcoinMenuItem.setVisible(false);
        } else {
            PaymentOptions po = uiBittorrentDownload.getPaymentOptions();
            fiatMenuItem.setVisible(po.paypalUrl != null);
            bitcoinMenuItem.setVisible(po.bitcoin != null);
        }
        super.onPrepareOptionsMenu(menu);
    }

    public void updatePauseResumeSeedMenuAction() {
        if (pauseResumeMenuItem == null) {
            return;
        }
        if (isPausable()) {
            pauseResumeMenuItem.setIcon(R.drawable.action_bar_pause);
        }
        if (isResumable()) {
            pauseResumeMenuItem.setIcon(R.drawable.action_bar_resume);
            if (isSeedable()) {
                pauseResumeMenuItem.setIcon(R.drawable.action_bar_seed);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Activity activity = getActivity();
        int itemId = item.getItemId();
        PaymentOptions paymentOptions = uiBittorrentDownload.getPaymentOptions();
        switch (itemId) {
            // TODO: Add a force re-announce action
            case R.id.fragment_transfer_detail_menu_delete:
                // TODO: add an action listener and pass to dialog
                new CancelMenuAction(activity, uiBittorrentDownload, true, true).onClick(activity);
                break;
            case R.id.fragment_transfer_detail_menu_pause_resume_seed:
                if (isPausable()) {
                    new PauseDownloadMenuAction(activity, uiBittorrentDownload).onClick(activity);
                } else if (isSeedable()) {
                    new SeedAction(activity, uiBittorrentDownload).onClick(activity);
                } else if (isResumable()) {
                    new ResumeDownloadMenuAction(activity, uiBittorrentDownload, R.string.resume_torrent_menu_action).onClick(activity);
                }
                updatePauseResumeSeedMenuAction();
                break;
            case R.id.fragment_transfer_detail_menu_clear:
                new CancelMenuAction(activity, uiBittorrentDownload, false, false).onClick(activity);
                break;
            case R.id.fragment_transfer_detail_menu_copy_magnet:
                new CopyToClipboardMenuAction(activity,
                        R.drawable.contextmenu_icon_magnet,
                        R.string.transfers_context_menu_copy_magnet,
                        R.string.transfers_context_menu_copy_magnet_copied,
                        uiBittorrentDownload.magnetUri() + BTEngine.getInstance().magnetPeers()
                ).onClick(activity);
                break;
            case R.id.fragment_transfer_detail_menu_copy_infohash:
                new CopyToClipboardMenuAction(activity,
                        R.drawable.contextmenu_icon_copy,
                        R.string.transfers_context_menu_copy_infohash,
                        R.string.transfers_context_menu_copy_infohash_copied,
                        uiBittorrentDownload.getInfoHash()
                ).onClick(activity);
                break;
            case R.id.fragment_transfer_detail_menu_donate_fiat:
                new SendFiatTipAction(activity, paymentOptions.paypalUrl).onClick(activity);
                break;
            case R.id.fragment_transfer_detail_menu_donate_bitcoin:
                new SendBitcoinTipAction(activity, paymentOptions.bitcoin).onClick(activity);
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
