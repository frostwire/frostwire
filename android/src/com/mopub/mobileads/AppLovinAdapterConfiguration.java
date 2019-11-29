package com.mopub.mobileads;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkSettings;
import com.mopub.common.BaseAdapterConfiguration;
import com.mopub.common.OnNetworkInitializationFinishedListener;
import com.mopub.common.Preconditions;
import com.mopub.common.logging.MoPubLog;
//import com.mopub.mobileads.applovin.BuildConfig;

import java.util.Map;

public class AppLovinAdapterConfiguration extends BaseAdapterConfiguration {
    private static final String MOPUB_NETWORK_NAME = "applovin_sdk";//;BuildConfig.NETWORK_NAME;
    private static final String ADAPTER_VERSION = "9.10.3.0"; //Keep it matching with the applovin SDK version
    // BuildConfig.VERSION_NAME;

    private static final String CONFIG_KEY_APPLOVIN_SDK_KEY = "sdk_key";
    private static final String MANIFEST_KEY_APPLOVIN_SDK_KEY = "applovin.sdk.key";

    private static String sdkKey;
    static final String APPLOVIN_PLUGIN_VERSION = "MoPub-" + ADAPTER_VERSION;

    @Nullable
    private static AppLovinSdk sdk;

    @NonNull
    @Override
    public String getMoPubNetworkName() {
        return MOPUB_NETWORK_NAME;
    }

    @NonNull
    @Override
    public String getNetworkSdkVersion() {
        return AppLovinSdk.VERSION;
    }

    @NonNull
    @Override
    public String getAdapterVersion() {
        return ADAPTER_VERSION;
    }

    @Nullable
    @Override
    public String getBiddingToken(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        return (sdk != null) ? sdk.getAdService().getBidToken() : null;
    }

    @Override
    public void initializeNetwork(@NonNull Context context, @Nullable Map<String, String> configuration, @NonNull OnNetworkInitializationFinishedListener listener) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(listener);

        // Check if an SDK key is provided in the `configuration` map
        sdk = getSdkFromConfiguration(configuration, context);
        if (sdk != null) {
            sdk.setPluginVersion(APPLOVIN_PLUGIN_VERSION);
            sdk.setMediationProvider(AppLovinMediationProvider.MOPUB);

            MoPubLog.LogLevel logLevel = MoPubLog.getLogLevel();
            boolean verboseLoggingEnabled = logLevel == MoPubLog.LogLevel.DEBUG;

            sdk.getSettings().setVerboseLogging(verboseLoggingEnabled);

            listener.onNetworkInitializationFinished(AppLovinAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_INITIALIZATION_SUCCESS);
        } else {
            listener.onNetworkInitializationFinished(AppLovinAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
    }

    @Nullable
    private AppLovinSdk getSdkFromConfiguration(@Nullable Map<String, String> configuration, @NonNull Context context) {
        String key = null;

        if (configuration != null && !configuration.isEmpty()) {
            key = configuration.get(CONFIG_KEY_APPLOVIN_SDK_KEY);
        }

        if (!TextUtils.isEmpty(key)) {
            setSdkKey(key);

            return AppLovinSdk.getInstance(key, new AppLovinSdkSettings(context), context);
        } else {
            final boolean androidManifestContainsValidSdkKey = androidManifestContainsValidSdkKey(context);
            if (androidManifestContainsValidSdkKey) {
                return AppLovinSdk.getInstance(context);
            } else {
                return null;
            }
        }
    }

    private static void setSdkKey(String key) {
        sdkKey = key;
    }

    public static String getSdkKey() {
        return sdkKey;
    }

    static boolean androidManifestContainsValidSdkKey(final Context context) {
        try {
            final PackageManager pm = context.getPackageManager();
            final ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            final Bundle metaData = ai.metaData;
            if (metaData != null) {
                final String sdkKey = metaData.getString(MANIFEST_KEY_APPLOVIN_SDK_KEY);
                return !TextUtils.isEmpty(sdkKey);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // This is unlikely to happen: we are querying for our own package
        }

        return false;
    }
}
