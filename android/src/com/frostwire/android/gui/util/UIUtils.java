/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UIUtils {

    private static final Logger LOG = Logger.getLogger(UIUtils.class);

    /**
     * Localizable Number Format constant for the current default locale.
     */
    private static final NumberFormat NUMBER_FORMAT0; // localized "#,##0"

    private static final String[] BYTE_UNITS = new String[]{"b", "KB", "Mb", "Gb", "Tb"};

    private static final String GENERAL_UNIT_KBPSEC = "KB/s";

    // put "support" pitches at the beginning and play with the offset
    private static final int[] PITCHES = {
            R.string.support_frostwire,
            R.string.support_free_software,
            R.string.save_bandwidth,
            R.string.cheaper_than_drinks,
            R.string.cheaper_than_lattes,
            R.string.cheaper_than_parking,
            R.string.cheaper_than_beer,
            R.string.cheaper_than_cigarettes,
            R.string.cheaper_than_gas,
            R.string.keep_the_project_alive
    };

    static {
        NUMBER_FORMAT0 = NumberFormat.getNumberInstance(Locale.getDefault());
        NUMBER_FORMAT0.setMaximumFractionDigits(0);
        NUMBER_FORMAT0.setMinimumFractionDigits(0);
        NUMBER_FORMAT0.setGroupingUsed(true);
    }

    private static void showToastMessage(Context context, String message, int duration, int gravity, int xOffset, int yOffset) {
        if (context != null && message != null) {
            Toast toast = Toast.makeText(context, message, duration);
            if (gravity != (Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM)) {
                toast.setGravity(gravity, xOffset, yOffset);
            }
            toast.show();
        }
    }

    public static void showShortMessage(View view, int resourceId) {
        Snackbar.make(view, resourceId, Snackbar.LENGTH_SHORT).show();
    }

    public static void showLongMessage(View view, int resourceId) {
        Snackbar.make(view, resourceId, Snackbar.LENGTH_LONG).show();
    }

    public static void showDismissableMessage(View view, int resourceId) {
        final Snackbar snackbar = Snackbar.make(view, resourceId, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.dismiss, v -> snackbar.dismiss()).show();
    }

    public static void sendShutdownIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), true);
        ctx.startActivity(i);
    }

    public static void sendGoHomeIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("gohome-" + ConfigurationManager.instance().getUUIDString(), true);
        ctx.startActivity(i);
    }

    public static void showToastMessage(Context context, String message, int duration) {
        showToastMessage(context, message, duration, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
    }

    public static void showShortMessage(Context context, String message) {
        showToastMessage(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLongMessage(Context context, String message) {
        showToastMessage(context, message, Toast.LENGTH_LONG);
    }

    public static void showShortMessage(Context context, int resId) {
        showShortMessage(context, context.getString(resId));
    }

    public static void showLongMessage(Context context, int resId) {
        showLongMessage(context, context.getString(resId));
    }

    public static void showShortMessage(Context context, int resId, Object... formatArgs) {
        showShortMessage(context, context.getString(resId, formatArgs));
    }

    public static void showYesNoDialog(Context context, int iconId, String message, int titleId, OnClickListener positiveListener) {
        showYesNoDialog(context, iconId, message, titleId, positiveListener, (dialog, which) -> dialog.dismiss());
    }

    public static void showYesNoDialog(Context context, int iconId, String message, int titleId, OnClickListener positiveListener, OnClickListener negativeListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setIcon(iconId).setMessage(message).setTitle(titleId).setCancelable(false).setPositiveButton(android.R.string.yes, positiveListener).setNegativeButton(android.R.string.no, negativeListener);
        builder.create().show();
    }

    public interface TextViewInputDialogCallback {
        void onDialogSubmitted(String value, boolean cancelled);
    }

    public static void showEditTextDialog(Context context,
                                          int iconId,
                                          int messageStringId,
                                          int titleStringId,
                                          int positiveButtonStringId,
                                          boolean multilineInput,
                                          String optionalEditTextValue,
                                          final TextViewInputDialogCallback callback) {
        LinearLayout customView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.view_alertdialog_edittext, null);
        final EditText inputEditText = customView.findViewById(R.id.view_alertdialog_edittext_edittext);
        inputEditText.setHint(context.getString(messageStringId));
        inputEditText.setMaxLines(!multilineInput ? 1 : 5);

        if (optionalEditTextValue != null && optionalEditTextValue.length() > 0) {
            inputEditText.setText(optionalEditTextValue);
        }

        final android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);
        builder.setIcon(iconId).setTitle(titleStringId).setCancelable(true);
        builder.setView(customView);
        builder.setPositiveButton(positiveButtonStringId, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (inputEditText != null && callback != null) {
                    try {
                        callback.onDialogSubmitted(inputEditText.getText().toString(), false);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                dialog.dismiss();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.onDialogSubmitted(null, true);
                }
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public static String getBytesInHuman(long size) {
        int i;
        float sizeFloat = (float) size;
        for (i = 0; sizeFloat > 1024; i++) {
            sizeFloat /= 1024f;
        }
        return String.format(Locale.US, "%.2f %s", sizeFloat, BYTE_UNITS[i]);
    }

    /**
     * Converts an rate into a human readable and localized KB/s speed.
     */
    public static String rate2speed(double rate) {
        return NUMBER_FORMAT0.format(rate) + " " + GENERAL_UNIT_KBPSEC;
    }

    public static String getFileTypeAsString(Resources resources, byte fileType) {
        switch (fileType) {
            case Constants.FILE_TYPE_APPLICATIONS:
                return resources.getString(R.string.applications);
            case Constants.FILE_TYPE_AUDIO:
                return resources.getString(R.string.audio);
            case Constants.FILE_TYPE_DOCUMENTS:
                return resources.getString(R.string.documents);
            case Constants.FILE_TYPE_PICTURES:
                return resources.getString(R.string.pictures);
            case Constants.FILE_TYPE_RINGTONES:
                return resources.getString(R.string.ringtones);
            case Constants.FILE_TYPE_VIDEOS:
                return resources.getString(R.string.video);
            case Constants.FILE_TYPE_TORRENTS:
                return resources.getString(R.string.media_type_torrents);
            default:
                return resources.getString(R.string.unknown);
        }
    }

    /**
     * Opens the given file with the default Android activity for that File and
     * mime type.
     */
    public static void openFile(Context context, String filePath, String mime, boolean useFileProvider) {
        try {
            if (filePath != null && !openAudioInternal(context, filePath)) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(getFileUri(context, filePath, useFileProvider), Intent.normalizeMimeType(mime));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (mime != null && mime.contains("video")) {
                    if (MusicUtils.isPlaying()) {
                        MusicUtils.playOrPause();
                    }
                    UXStats.instance().log(UXAction.LIBRARY_VIDEO_PLAY);
                }
                context.startActivity(i);
            }
        } catch (Throwable e) {
            UIUtils.showShortMessage(context, R.string.cant_open_file);
            LOG.error("Failed to open file: " + filePath, e);
        }
    }

    public static Uri getFileUri(Context context, String filePath, boolean useFileProvider) {
        return useFileProvider ?
                FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", new File(filePath)) :
                Uri.fromFile(new File(filePath));
    }

    public static void openFile(Context context, File file) {
        openFile(context, file.getAbsolutePath(), getMimeType(file.getAbsolutePath()), true);
    }

    public static void openFile(Context context, File file, boolean useFileProvider) {
        openFile(context, file.getAbsolutePath(), getMimeType(file.getAbsolutePath()), useFileProvider);
    }

    public static void openURL(Context context, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            // ignore
            // yes, it happens
        }
    }

    public static String getMimeType(String filePath) {
        try {
            return MimeDetector.getMimeType(FilenameUtils.getExtension(filePath));
        } catch (Throwable e) {
            LOG.error("Failed to read mime type for: " + filePath);
            return MimeDetector.UNKNOWN;
        }
    }

    /**
     * Create an ephemeral playlist with the files of the same type that live on the folder of the given file descriptor and play it.
     */
    public static void playEphemeralPlaylist(final Context context, FileDescriptor fd) {
        Engine.instance().getMediaPlayer().play(Librarian.instance().createEphemeralPlaylist(context, fd));
    }

    private static boolean openAudioInternal(final Context context, String filePath) {
        try {
            List<FileDescriptor> fds = Librarian.instance().getFiles(context, filePath, true);
            if (fds.size() == 1 && fds.get(0).fileType == Constants.FILE_TYPE_AUDIO) {
                playEphemeralPlaylist(context, fds.get(0));
                UXStats.instance().log(UXAction.LIBRARY_PLAY_AUDIO_FROM_FILE);
                return true;
            } else {
                return false;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks setting to show or not the transfers window right after a download has started.
     * This should probably be moved elsewhere (similar to GUIMediator on the desktop)
     */
    public static void showTransfersOnDownloadStart(Context context) {
        if (ConfigurationManager.instance().showTransfersOnDownloadStart() && context != null) {
            Intent i = new Intent(context, MainActivity.class);
            i.setAction(Constants.ACTION_SHOW_TRANSFERS);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            context.startActivity(i);
        }
    }

    public static void showKeyboard(Context context, View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static void hideKeyboardFromActivity(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void goToFrostWireMainActivity(Activity activity) {
        final Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        activity.finish();
        activity.overridePendingTransition(0, 0);
    }

    /**
     * @param context                         -  containing Context.
     * @param showInstallationCompleteSection - true if you want to display "Your installation is now complete. Thank You" section
     * @param dismissListener                 - what happens when the dialog is dismissed.
     * @param referrerContextSuffix           - string appended at the end of social pages click urls's ?ref=_android_ parameter.
     */
    public static void showSocialLinksDialog(final Context context,
                                             boolean showInstallationCompleteSection,
                                             DialogInterface.OnDismissListener dismissListener,
                                             String referrerContextSuffix) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View customView = View.inflate(context, R.layout.view_social_buttons, null);
        builder.setView(customView);
        builder.setPositiveButton(context.getString(android.R.string.ok), (dialog, which) -> dialog.dismiss());
        final AlertDialog socialLinksDialog = builder.create();
        socialLinksDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        socialLinksDialog.setOnDismissListener(dismissListener);
        ImageButton fbButton = customView.findViewById(R.id.view_social_buttons_facebook_button);
        ImageButton twitterButton = customView.findViewById(R.id.view_social_buttons_twitter_button);
        ImageButton redditButton = customView.findViewById(R.id.view_social_buttons_reddit_button);
        final String referrerParam = "?ref=android_" + ((referrerContextSuffix != null) ? referrerContextSuffix.trim() : "");
        fbButton.setOnClickListener(v -> UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_FACEBOOK_PAGE + referrerParam));
        twitterButton.setOnClickListener(v -> UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_TWITTER_PAGE + referrerParam));
        redditButton.setOnClickListener(v -> UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_REDDIT_PAGE + referrerParam));
        if (showInstallationCompleteSection) {
            LinearLayout installationCompleteLayout =
                    customView.findViewById(R.id.view_social_buttons_installation_complete_layout);
            installationCompleteLayout.setVisibility(View.VISIBLE);
            ImageButton dismissCheckButton = customView.findViewById(R.id.view_social_buttons_dismiss_check);
            dismissCheckButton.setOnClickListener(v -> socialLinksDialog.dismiss());
        }
        socialLinksDialog.show();
    }

    // tried playing around with <T> but at the moment I only need ByteExtra's, no need to over enginner.
    public static class IntentByteExtra {
        public final String name;
        public final byte value;

        public IntentByteExtra(String name, byte value) {
            this.name = name;
            this.value = value;
        }
    }

    public static void broadcastAction(Context ctx, String actionCode, IntentByteExtra... extras) {
        if (ctx == null || actionCode == null) {
            return;
        }
        final Intent intent = new Intent(actionCode);
        if (extras != null && extras.length > 0) {
            for (IntentByteExtra extra : extras) {
                intent.putExtra(extra.name, extra.value);
            }
        }
        ctx.sendBroadcast(intent);
    }

    public static int randomPitchResId(boolean avoidSupportPitches) {
        int offset1 = 0;
        int offset2 = 2;
        int offset = !avoidSupportPitches ? offset1 : offset2;
        return PITCHES[offset + new Random().nextInt(PITCHES.length - offset)];
    }

    public static double getScreenInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        double x_sq = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y_sq = Math.pow(dm.heightPixels / dm.ydpi, 2);
        // pitagoras
        return Math.sqrt(x_sq + y_sq);
    }

    public static boolean isTablet(Resources res) {
        return res.getBoolean(R.bool.isTablet);
    }

    public static boolean isPortrait(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels > dm.widthPixels;
    }

    public static boolean isMain() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    /**
     * @param thresholdPreferenceKey - preference key for an int threshold
     * @return true if the threshold is >= 100, otherwise true if the dice roll is below the threshold
     */
    public static boolean diceRollPassesThreshold(ConfigurationManager cm, String thresholdPreferenceKey) {
        int thresholdValue = cm.getInt(thresholdPreferenceKey);
        int diceRoll = new Random().nextInt(100)+1; //1-100
        if (thresholdValue <= 0) {
            LOG.info("diceRollPassesThreshold(" + thresholdPreferenceKey + "=" + thresholdValue + ") -> false");
            return false;
        }
        if (thresholdValue >= 100) {
            LOG.info("diceRollPassesThreshold(" + thresholdPreferenceKey + "=" + thresholdValue + ") -> true (always)");
            return true;
        }
        LOG.info("diceRollPassesThreshold(" + thresholdPreferenceKey + "=" + thresholdValue + ", roll=" + diceRoll + ") -> " + (diceRoll <= thresholdValue));
        return diceRoll <= thresholdValue;
    }
}
