/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.EditTextDialog;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;
import com.frostwire.util.Ref;
import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 * @author votaguz
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
            R.string.support_frostwire, //0
            R.string.support_free_software, //1
            R.string.support_frostwire, //2
            R.string.support_free_software, //3
            R.string.save_bandwidth, //4
            R.string.cheaper_than_drinks, //5
            R.string.cheaper_than_lattes, //6
            R.string.cheaper_than_parking, //7
            R.string.cheaper_than_beer, //8
            R.string.cheaper_than_cigarettes, //9
            R.string.cheaper_than_gas, //10
            R.string.try_it_free_for_a_half_hour,
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

    public static void showLongMessage(Context context, int resourceId, Object... formatArgs) {
        showLongMessage(context, context.getResources().getString(resourceId, formatArgs));
    }

    public static void showDismissableMessage(View view, int resourceId) {
        final Snackbar snackbar = Snackbar.make(view, resourceId, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.dismiss, v -> snackbar.dismiss()).show();
    }

    public static void sendShutdownIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("shutdown-frostwire", true);
        ctx.startActivity(i);
    }

    public static void sendGoHomeIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("gohome-frostwire", true);
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

    public static void showLongMessage(Context context, @StringRes int resId) {
        showLongMessage(context, context.getString(resId));
    }

    public static void showShortMessage(Context context, int resId, Object... formatArgs) {
        showShortMessage(context, context.getString(resId, formatArgs));
    }

    public static void showYesNoDialog(FragmentManager fragmentManager, String message, int titleId, OnClickListener positiveListener) {
        showYesNoDialog(fragmentManager, message, titleId, positiveListener, (dialog, which) -> dialog.dismiss());
    }

    public static void showYesNoDialog(FragmentManager fragmentManager, String message, int titleId, OnClickListener positiveListener, OnClickListener negativeListener) {
        YesNoDialog yesNoDialog = YesNoDialog.newInstance(message, titleId, message, (byte) 0);
        yesNoDialog.setOnDialogClickListener((tag, which) -> {
                    if (which == Dialog.BUTTON_POSITIVE && positiveListener != null) {
                        positiveListener.onClick(yesNoDialog.getDialog(), which);
                    } else if (which == Dialog.BUTTON_NEGATIVE && negativeListener != null) {
                        negativeListener.onClick(yesNoDialog.getDialog(), which);
                    }
                    yesNoDialog.dismiss();
                }
        );
        yesNoDialog.show(fragmentManager);
    }

    public static void showEditTextDialog(FragmentManager fragmentManager,
                                          int messageStringId,
                                          int titleStringId,
                                          int positiveButtonStringId,
                                          boolean cancelable,
                                          boolean multilineInput,
                                          String optionalEditTextValue,
                                          final EditTextDialog.TextViewInputDialogCallback callback) {
        new EditTextDialog().
                init(titleStringId,
                        messageStringId,
                        positiveButtonStringId,
                        cancelable,
                        multilineInput,
                        optionalEditTextValue,
                        callback).show(fragmentManager);
    }

    public static String getBytesInHuman(double size) {
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

    public static boolean openFile(Context context, String filePath, String mime) {
        return openFile(context, filePath, mime, SystemUtils.hasNougatOrNewer());
    }

    /**
     * Opens the given file with the default Android activity for that File and
     * mime type.
     */
    public static boolean openFile(Context context, String filePath, String mime, boolean useFileProvider) {
        try {
            if (filePath != null && !openAudioInternal(context, filePath)) {
                Intent i = new Intent(Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE.equals(mime) ?
                        Intent.ACTION_INSTALL_PACKAGE : Intent.ACTION_VIEW);

                // The mime type makes it match AudioPlayerActivity see AndroidManifest.xml
                LOG.info("openFile(filePath=" + filePath + ", mime="+mime+")", true);
                Uri fileUri = getFileUri(context, filePath, useFileProvider);
                LOG.info("openFile(...) -> fileUri="+fileUri.toString(), true);
                
                i.setDataAndType(fileUri, Intent.normalizeMimeType(mime));

                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (mime != null && mime.contains("video")) {
                    if (MusicUtils.isPlaying()) {
                        MusicUtils.playPauseOrResume();
                    }
                }
                context.startActivity(i);
            }
            return true;
        } catch (Throwable e) {
            UIUtils.showShortMessage(context, R.string.cant_open_file);
            LOG.error("Failed to open file: " + filePath, e);
            return false;
        }
    }

    /**
     * Takes a screenshot of the given view
     *
     * @return File with jpeg of the screenshot taken. null if there was a problem.
     */
    public static File takeScreenshot(View view) {
        view.setDrawingCacheEnabled(true);
        try {
            Thread.sleep(300);
        } catch (Throwable ignore) {
        }
        Bitmap drawingCache = null;
        try {
            drawingCache = view.getDrawingCache();
        } catch (Throwable ignored) {
        }
        Bitmap screenshotBitmap = null;
        if (drawingCache != null) {
            try {
                screenshotBitmap = Bitmap.createBitmap(drawingCache);
            } catch (Throwable ignored) {
            }
        }
        view.setDrawingCacheEnabled(false);
        if (screenshotBitmap == null) {
            return null;
        }
        File screenshotFile = new File(Environment.getExternalStorageDirectory().toString(), "fwPlayerScreenshot.tmp.jpg");
        if (screenshotFile.exists()) {
            screenshotFile.delete();
            try {
                screenshotFile.createNewFile();
            } catch (IOException ignore) {
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(screenshotFile);
            screenshotBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Throwable t) {
            screenshotFile.delete();
            screenshotFile = null;
        }
        return screenshotFile;
    }

    public static Uri getFileUri(Context context, String filePath) {
        return getFileUri(context, new File(filePath));
    }

    public static Uri getFileUri(Context context, File file) {
        return getFileUri(context, file, SystemUtils.hasNougatOrNewer());
    }

    public static Uri getFileUri(Context context, File file, boolean useFileProvider) {
        return useFileProvider ?
                FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file) :
                Uri.fromFile(file);
    }

    public static Uri getFileUri(Context context, String filePath, boolean useFileProvider) {
        return getFileUri(context, new File(filePath), useFileProvider);
    }

    public static boolean openFile(Context context, File file) {
        return openFile(context, file.getAbsolutePath(), getMimeType(file.getAbsolutePath()), SystemUtils.hasNougatOrNewer());
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

    public static void setupClickUrl(View v, final String url) {
        v.setOnClickListener(view -> UIUtils.openURL(view.getContext(), url));
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
    public static void playEphemeralPlaylist(final Context context, final FWFileDescriptor fd) {
        async(context, UIUtils::playEphemeralPlaylistTask, fd);
    }

    private static boolean openAudioInternal(final Context context, String filePath) {
        try {
            List<FWFileDescriptor> fds = Librarian.instance().getFiles(context, filePath, true);
            if (fds.size() == 1 && fds.get(0).fileType == Constants.FILE_TYPE_AUDIO) {
                playEphemeralPlaylist(context, fds.get(0));
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

    // tried playing around with <T> but at the moment I only need ByteExtra's, no need to over engineer.
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
        if ((!Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) && PITCHES[5] != R.string.try_it_free_for_a_half_hour) {
            PITCHES[5] = R.string.try_it_free_for_a_half_hour;
            PITCHES[6] = R.string.try_it_free_for_a_half_hour;
            PITCHES[7] = R.string.try_it_free_for_a_half_hour;
            PITCHES[8] = R.string.try_it_free_for_a_half_hour;
            PITCHES[9] = R.string.try_it_free_for_a_half_hour;
            PITCHES[10] = R.string.try_it_free_for_a_half_hour;
        }
        int offsetRemoveAds = 4;
        int offset = !avoidSupportPitches ? 0 : offsetRemoveAds;
        return PITCHES[offset + new Random().nextInt(PITCHES.length - offset)];
    }

    public static double getScreenInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        double x_sq = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y_sq = Math.pow(dm.heightPixels / dm.ydpi, 2);
        // Thank you Pitagoras
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

    /**
     * @param thresholdPreferenceKey - preference key for an int threshold
     * @return true if the threshold is >= 100, otherwise true if the dice roll is below the threshold
     */
    public static boolean diceRollPassesThreshold(ConfigurationManager cm, String thresholdPreferenceKey) {
        int thresholdValue = cm.getInt(thresholdPreferenceKey);
        int diceRoll = new Random().nextInt(100) + 1; //1-100
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

    private static void playEphemeralPlaylistTask(Context context, final FWFileDescriptor fd) {
        LOG.info("playEphemeralPlaylistTask() is MusicPlaybackService running? " + MusicUtils.isMusicPlaybackServiceRunning());
        final CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        final WeakReference<Context> contextRef = Ref.weak(context);
        if (!MusicUtils.isMusicPlaybackServiceRunning()) {
            Runnable playEphemeralPlaylistOfOneCallback = () -> {
                try {
                    LOG.info("playEphemeralPlaylistTask::playEphemeralPlaylistOfOneCallback for " + fd.filePath, true);
                    if (mediaPlayer != null && Ref.alive(contextRef)) {
                        EphemeralPlaylist ephemeralPlaylist = Librarian.instance().createEphemeralPlaylist(contextRef.get(), fd);
                        LOG.info("playEphemeralPlaylistTask::playEphemeralPlaylistOfOneCallback created ephemeral playlist " + fd.filePath, true);
                        mediaPlayer.play(ephemeralPlaylist);
                    }
                } catch (Throwable ignored) {
                    // possible Runtime error thrown by Librarian.instance()
                } finally {
                    Ref.free(contextRef);
                }
            };

            if (MusicUtils.getMusicPlaybackService() == null) {
                //LOG.info("playEphemeralPlaylistTask() service is not there, and it's null");
                MusicUtils.startMusicPlaybackService(context,
                        MusicUtils.buildStartMusicPlaybackServiceIntent(context),
                        playEphemeralPlaylistOfOneCallback);
            } else {
                //LOG.info("playEphemeralPlaylistTask() calling playEphemeralPlaylistOfOneCallback directly, had music service already");
                playEphemeralPlaylistOfOneCallback.run();
            }

        } else {
            try {
                if (mediaPlayer != null) {
                    EphemeralPlaylist ephemeralPlaylist = Librarian.instance().createEphemeralPlaylist(context, fd);
                    mediaPlayer.play(ephemeralPlaylist);
                }
            } catch (Throwable ignored) {
                // possible Runtime error thrown by Librarian.instance()
            }
        }
    }

    public static boolean isScreenLocked(final Context context) {
        if (context == null) {
            return true;
        }
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null) {
            return true;
        }
        return km.isKeyguardLocked();
    }
}
