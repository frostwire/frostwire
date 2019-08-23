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

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailDetailsFragment extends AbstractTransferDetailFragment {
    private TextView storagePath;
    private CheckBox sequentialDownloadCheckBox;
    private TextView totalSize;
    private TextView numberOfFiles;
    private TextView downloadSpeedLimit;
    private ImageView downloadSpeedLimitArrow;
    private TextView uploadSpeedLimit;
    private ImageView uploadSpeedLimitArrow;
    private TextView hash;
    private ImageButton hashCopyButton;
    private TextView magnet;
    private ImageButton magnetCopyButton;
    private TextView createdOn;
    private TextView comment;
    private CompoundButton.OnCheckedChangeListener onSequentialDownloadCheckboxCheckedListener;
    private View.OnClickListener onCopyToClipboardListener;
    private View.OnClickListener onRateLimitClickListener;
    private static final int DOWNLOAD_UNLIMITED_VALUE = 0;
    private static final int UPLOAD_UNLIMITED_VALUE = 0;

    public TransferDetailDetailsFragment() {
        super(R.layout.fragment_transfer_detail_details);
    }

    @Override
    protected void initComponents(View rv, Bundle savedInstanceState) {
        super.initComponents(rv, savedInstanceState);
        storagePath.setText("");
        sequentialDownloadCheckBox.setChecked(false);
        totalSize.setText("");
        numberOfFiles.setText("");
        downloadSpeedLimit.setText("");
        uploadSpeedLimit.setText("");
        hash.setText("");
        magnet.setText("");
        createdOn.setText("");
        comment.setText("");
        onSequentialDownloadCheckboxCheckedListener = null;
        onCopyToClipboardListener = null;
    }

    @Override
    protected void updateComponents() {
        if (uiBittorrentDownload != null) {
            //ensureComponentsReferenced();
            final BTDownload btDL = uiBittorrentDownload.getDl();
            if (onCopyToClipboardListener == null) {
                onCopyToClipboardListener = new CopyToClipboardOnClickListener(uiBittorrentDownload);
            }
            // static data for this download is done only once
            if ("".equals(storagePath.getText())) {
                storagePath.setText(uiBittorrentDownload.getSavePath().getAbsolutePath());
            }
            if ("".equals(totalSize.getText())) {
                totalSize.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
            }
            if ("".equals(numberOfFiles.getText())) {
                List<TransferItem> items = uiBittorrentDownload.getItems();
                int fileCount = items == null ? 0 : items.size();
                numberOfFiles.setText(fileCount + "");
            }
            if ("".equals(hash.getText())) {
                hash.setText(uiBittorrentDownload.getInfoHash());
                hash.setOnClickListener(onCopyToClipboardListener);
                hashCopyButton.setOnClickListener(onCopyToClipboardListener);
            }
            if ("".equals(magnet.getText())) {
                magnet.setText(uiBittorrentDownload.magnetUri());
                magnet.setOnClickListener(onCopyToClipboardListener);
                magnetCopyButton.setOnClickListener(onCopyToClipboardListener);
            }
            if ("".equals(createdOn.getText())) {
                createdOn.setText(DateUtils.formatDateTime(getActivity(), uiBittorrentDownload.getCreated().getTime(), DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            if ("".equals(comment.getText())) {
                TorrentInfo torrentInfo = uiBittorrentDownload.getDl().getTorrentHandle().torrentFile();
                String torrentComment = torrentInfo.comment();
                if (torrentComment != null && !"".equals(torrentComment)) {
                    comment.setText(torrentComment);
                } else {
                    comment.setText(" ");
                    View view = findView(comment.getRootView(), R.id.fragment_transfer_detail_details_comment_container);
                    view.setVisibility(View.GONE);
                }
            }
            if (onSequentialDownloadCheckboxCheckedListener == null) {
                onSequentialDownloadCheckboxCheckedListener = new SequentialDownloadCheckboxCheckedListener(uiBittorrentDownload);
            }
            sequentialDownloadCheckBox.setOnCheckedChangeListener(null);
            sequentialDownloadCheckBox.setChecked(btDL.isSequentialDownload());
            sequentialDownloadCheckBox.setOnCheckedChangeListener(onSequentialDownloadCheckboxCheckedListener);

            if (onRateLimitClickListener == null) {
                onRateLimitClickListener = new OnSpeedLimitClickListener(uiBittorrentDownload, this, getFragmentManager());
                downloadSpeedLimit.setOnClickListener(onRateLimitClickListener);
                downloadSpeedLimitArrow.setOnClickListener(onRateLimitClickListener);
                uploadSpeedLimit.setOnClickListener(onRateLimitClickListener);
                uploadSpeedLimitArrow.setOnClickListener(onRateLimitClickListener);
            }
            int downloadRateLimit = btDL.getDownloadRateLimit();
            int uploadRateLimit = btDL.getUploadRateLimit();
            if (downloadRateLimit > 0) {
                downloadSpeedLimit.setText(UIUtils.getBytesInHuman(downloadRateLimit) + "/s");
            } else if (downloadRateLimit == DOWNLOAD_UNLIMITED_VALUE || downloadRateLimit == -1) {
                downloadSpeedLimit.setText(R.string.unlimited);
            }
            if (uploadRateLimit > 0) {
                uploadSpeedLimit.setText(UIUtils.getBytesInHuman(uploadRateLimit) + "/s");
            } else if (uploadRateLimit == UPLOAD_UNLIMITED_VALUE || uploadRateLimit == -1) {
                uploadSpeedLimit.setText(R.string.unlimited);
            }
        }
    }

    @Override
    protected int getTabTitleStringId() {
        return R.string.details;
    }

    @Override
    public void ensureComponentsReferenced(View rootView) {
        storagePath = findView(rootView, R.id.fragment_transfer_detail_details_storage_path);
        sequentialDownloadCheckBox = findView(rootView, R.id.fragment_transfer_detail_details_sequential_download_checkBox);
        totalSize = findView(rootView, R.id.fragment_transfer_detail_details_total_size);
        numberOfFiles = findView(rootView, R.id.fragment_transfer_detail_details_files_number);
        downloadSpeedLimit = findView(rootView, R.id.fragment_transfer_detail_details_speed_limit_download);
        downloadSpeedLimitArrow = findView(rootView, R.id.fragment_transfer_detail_details_speed_limit_download_arrow);
        uploadSpeedLimit = findView(rootView, R.id.fragment_transfer_detail_details_speed_limit_upload);
        uploadSpeedLimitArrow = findView(rootView, R.id.fragment_transfer_detail_details_speed_limit_upload_arrow);
        hash = findView(rootView, R.id.fragment_transfer_detail_details_hash);
        hashCopyButton = findView(rootView, R.id.fragment_transfer_detail_details_hash_copy_button);
        magnet = findView(rootView, R.id.fragment_transfer_detail_details_magnet);
        magnetCopyButton = findView(rootView, R.id.fragment_transfer_detail_details_magnet_copy_button);
        createdOn = findView(rootView, R.id.fragment_transfer_detail_details_created_on);
        comment = findView(rootView, R.id.fragment_transfer_detail_details_comment);
    }

    private static final class SequentialDownloadCheckboxCheckedListener implements CompoundButton.OnCheckedChangeListener {
        private final UIBittorrentDownload uiBittorrentDownload;

        SequentialDownloadCheckboxCheckedListener(UIBittorrentDownload uiBittorrentDownload) {
            this.uiBittorrentDownload = uiBittorrentDownload;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (uiBittorrentDownload != null) {
                System.out.println("onCheckedChanged(isChecked=" + isChecked + ");");
                uiBittorrentDownload.getDl().setSequentialDownload(isChecked);
            }
        }
    }

    private static final class CopyToClipboardOnClickListener implements View.OnClickListener {
        private final UIBittorrentDownload uiBittorrentDownload;

        CopyToClipboardOnClickListener(UIBittorrentDownload uiBittorrentDownload) {
            this.uiBittorrentDownload = uiBittorrentDownload;
        }

        @Override
        public void onClick(View v) {
            String data = null;
            int drawableId = 0, actionNameId = 0, messageId = 0;
            if (v.getId() == R.id.fragment_transfer_detail_details_hash_copy_button ||
                    v.getId() == R.id.fragment_transfer_detail_details_hash) {
                drawableId = R.drawable.contextmenu_icon_copy;
                actionNameId = R.string.transfers_context_menu_copy_infohash;
                messageId = R.string.transfers_context_menu_copy_infohash_copied;
                data = uiBittorrentDownload.getInfoHash();
            } else if (v.getId() == R.id.fragment_transfer_detail_details_magnet_copy_button ||
                    v.getId() == R.id.fragment_transfer_detail_details_magnet) {
                drawableId = R.drawable.contextmenu_icon_magnet;
                actionNameId = R.string.transfers_context_menu_copy_magnet;
                messageId = R.string.transfers_context_menu_copy_magnet_copied;
                data = uiBittorrentDownload.magnetUri();
            }
            CopyToClipboardMenuAction action =
                    new CopyToClipboardMenuAction(v.getContext(),
                            drawableId,
                            actionNameId,
                            messageId,
                            data);
            action.onClick();
        }
    }

    public static final class SpeedLimitDialog extends DialogFragment {
        private UIBittorrentDownload uiBittorrentDownload;
        private Direction direction;
        private WeakReference<TransferDetailDetailsFragment> fragmentRef;

        enum Direction {
            Download,
            Upload
        }

        private static final String START_RANGE = "startRange";
        private static final String END_RANGE = "endRange";
        private static final String DEFAULT_VALUE = "defaultValue";
        private static final String IS_BYTE_RATE = "isByteRate";
        private static final String SUPPORTS_UNLIMITED = "supportsUnlimited";
        private static final String UNLIMITED_VALUE = "unlimitedValue";
        private static final String UNLIMITED_CHECKED = "unlimitedChecked";
        private static final String CURRENT_VALUE = "currentValue";

        private int mStartRange;
        private int mEndRange;
        private int mDefault;
        private boolean mIsByteRate;
        private boolean mSupportsUnlimited;
        private int mUnlimitedValue;
        private boolean mSkipListeners;
        private SeekBar mSeekbar;
        private CheckBox mUnlimitedCheckbox;
        private TextView mCurrentValueTextView;
        private TextView mDialogTitle;

        public static SpeedLimitDialog newInstance(UIBittorrentDownload uiBittorrentDownload, Direction direction, WeakReference<TransferDetailDetailsFragment> fragmentRef) {
            SpeedLimitDialog dialog = new SpeedLimitDialog().init(uiBittorrentDownload, direction, fragmentRef);
            dialog.setCancelable(true);
            Bundle bundle = new Bundle();
            bundle.putInt(START_RANGE, 1024);
            bundle.putInt(END_RANGE, 5242880);
            bundle.putInt(DEFAULT_VALUE, 0);
            bundle.putBoolean(IS_BYTE_RATE, true);
            bundle.putBoolean(SUPPORTS_UNLIMITED, true);
            bundle.putInt(UNLIMITED_VALUE, direction == Direction.Download ? DOWNLOAD_UNLIMITED_VALUE : UPLOAD_UNLIMITED_VALUE);
            BTDownload dl = uiBittorrentDownload.getDl();
            int rateLimit = direction == Direction.Download ? dl.getDownloadRateLimit() : dl.getUploadRateLimit();
            bundle.putInt(CURRENT_VALUE, rateLimit);
            dialog.setArguments(bundle);
            return dialog;
        }

        public SpeedLimitDialog() {
        }

        public SpeedLimitDialog init(UIBittorrentDownload uiBittorrentDownload, Direction direction, WeakReference<TransferDetailDetailsFragment> fragmentRef) {
            this.uiBittorrentDownload = uiBittorrentDownload;
            this.direction = direction;
            this.fragmentRef = fragmentRef;
            return this;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle args = getArguments();
            mStartRange = args.getInt(START_RANGE);
            mEndRange = args.getInt(END_RANGE);
            mDefault = args.getInt(DEFAULT_VALUE);
            mIsByteRate = args.getBoolean(IS_BYTE_RATE);
            mSupportsUnlimited = args.getBoolean(SUPPORTS_UNLIMITED);
            mUnlimitedValue = args.getInt(UNLIMITED_VALUE);
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(START_RANGE, mStartRange);
            outState.putInt(END_RANGE, mEndRange);
            outState.putInt(DEFAULT_VALUE, mDefault);
            outState.putBoolean(IS_BYTE_RATE, mIsByteRate);
            outState.putBoolean(SUPPORTS_UNLIMITED, mSupportsUnlimited);
            outState.putInt(UNLIMITED_VALUE, mUnlimitedValue);
            outState.putBoolean(UNLIMITED_CHECKED, mUnlimitedCheckbox.isChecked());
            outState.putInt(CURRENT_VALUE, mSeekbar.getProgress());
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View view = layoutInflater.inflate(R.layout.dialog_preference_seekbar_with_checkbox, null);
            builder.setView(view);

            mDialogTitle = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_title);
            mDialogTitle.setText(direction == Direction.Download ? R.string.torrent_max_download_speed : R.string.torrent_max_upload_speed);

            mSeekbar = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
            mSeekbar.setMax(mEndRange);
            int previousValue = 0;
            if (getArguments() != null) {
                previousValue = getArguments().getInt(CURRENT_VALUE);
            }
            mSeekbar.setProgress(previousValue);
            mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    onSeekBarChanged(seekBar, i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            mCurrentValueTextView = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
            mUnlimitedCheckbox = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
            Bundle arguments = getArguments();
            mUnlimitedCheckbox.setChecked((arguments != null && arguments.getBoolean(UNLIMITED_CHECKED)));
            mUnlimitedCheckbox.setOnClickListener(view1 -> onUnlimitedCheckboxClicked());
            updateComponents(previousValue);
            updateCurrentValueTextView(previousValue);
            builder.setCancelable(true);
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> onDialogClosed(false));
            builder.setPositiveButton(R.string.accept, (dialog, which) -> onDialogClosed(true));
            return  builder.create();
        }

        private void updateComponents(int currentValue) {
            mSkipListeners = true;
            if (!mSupportsUnlimited) {
                mUnlimitedCheckbox.setVisibility(View.GONE);
                mSeekbar.setEnabled(true);
                mSeekbar.setProgress(currentValue);
            } else {
                mUnlimitedCheckbox.setVisibility(View.VISIBLE);
                mSeekbar.setEnabled(true);
                if (mUnlimitedCheckbox.isChecked()) {
                    mSeekbar.setProgress(mSeekbar.getMax());
                    mSeekbar.setEnabled(false);
                } else {
                    boolean isUnlimited = currentValue == mUnlimitedValue || currentValue == -1;
                    if (isUnlimited) {
                        mSeekbar.setProgress(mSeekbar.getMax());
                        mUnlimitedCheckbox.setChecked(true);
                        mSeekbar.setEnabled(false);
                    }
                }
            }
            mSkipListeners = false;
        }

        private void onSeekBarChanged(SeekBar seekBar, int value) {
            if (mSkipListeners) {
                return;
            }
            value = seekbarMinValueCheck(seekBar, value);
            Bundle arguments = getArguments();
            arguments.putInt(CURRENT_VALUE, value);
            updateCurrentValueTextView(value);
        }

        // SeekBar does not support a minimum value .setMinimum(int), have to override behaviour
        private int seekbarMinValueCheck(SeekBar seekBar, int value) {
            if (value < mStartRange) {
                mSkipListeners = true;
                value = mStartRange;
                seekBar.setProgress(value);
                mSkipListeners = false;
            }
            return value;
        }

        private void onUnlimitedCheckboxClicked() {
            Bundle arguments = getArguments();
            arguments.putBoolean(UNLIMITED_CHECKED, mUnlimitedCheckbox.isChecked());
            if (mSkipListeners) {
                return;
            }
            mSkipListeners = true;
            int seekbarValue = mSeekbar.getProgress();
            int currentValue = mUnlimitedCheckbox.isChecked() ? mUnlimitedValue : seekbarValue;
            updateComponents(currentValue); // this turns off mSkipListeners when done
            updateCurrentValueTextView(currentValue);
            mSkipListeners = false;
        }

        private void updateCurrentValueTextView(int value) {
            if (mSupportsUnlimited && (value == mUnlimitedValue) || mUnlimitedCheckbox.isChecked()) {
                mCurrentValueTextView.setText(getResources().getText(R.string.unlimited));
            } else if (mIsByteRate) {
                mCurrentValueTextView.setText(String.format("%s/s", UIUtils.getBytesInHuman(value)));
            }
        }

        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                seekbarMinValueCheck(mSeekbar, mSeekbar.getProgress());
                int value = (mSupportsUnlimited && mUnlimitedCheckbox.isChecked()) ?
                        mUnlimitedValue :
                        mSeekbar.getProgress();
                if (uiBittorrentDownload != null) {
                    BTDownload dl = uiBittorrentDownload.getDl();
                    if (direction == Direction.Download) {
                        dl.setDownloadRateLimit(value);
                    } else if (direction == Direction.Upload) {
                        dl.setUploadRateLimit(value);
                    }
                }
            }
            if (Ref.alive(fragmentRef)) {
                fragmentRef.get().updateComponents();
            }
        }
    }

    public static final class OnSpeedLimitClickListener implements View.OnClickListener {
        private final UIBittorrentDownload uiBittorrentDownload;
        private final WeakReference<FragmentManager> fragmentManagerRef;
        private final WeakReference<TransferDetailDetailsFragment> fragmentRef;

        OnSpeedLimitClickListener(UIBittorrentDownload uiBittorrentDownload, TransferDetailDetailsFragment fragment, FragmentManager fragmentManager) {
            this.uiBittorrentDownload = uiBittorrentDownload;
            fragmentManagerRef = Ref.weak(fragmentManager);
            fragmentRef = Ref.weak(fragment);
        }

        @Override
        public void onClick(View v) {
            SpeedLimitDialog.Direction direction = SpeedLimitDialog.Direction.Upload;
            switch (v.getId()) {
                case R.id.fragment_transfer_detail_details_speed_limit_download:
                case R.id.fragment_transfer_detail_details_speed_limit_download_arrow:
                    direction = SpeedLimitDialog.Direction.Download;
                    break;
                case R.id.fragment_transfer_detail_details_speed_limit_upload:
                case R.id.fragment_transfer_detail_details_speed_limit_upload_arrow:
                    direction = SpeedLimitDialog.Direction.Upload;
                    break;
            }

            SpeedLimitDialog dialog = SpeedLimitDialog.newInstance(uiBittorrentDownload, direction, fragmentRef);
            dialog.show(fragmentManagerRef.get(), "SpeedLimitDialog");
        }
    }
}
