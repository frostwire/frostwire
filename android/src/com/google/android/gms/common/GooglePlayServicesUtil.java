package com.google.android.gms.common;

import android.content.Context;

/**
 * Hack to avoid dependency of com.google.android.gms:play-services-base
 */
public final class GooglePlayServicesUtil {

    @SuppressWarnings("deprecation")
    public static int isGooglePlayServicesAvailable(Context var0) {
        return GooglePlayServicesUtilLight.isGooglePlayServicesAvailable(var0);
    }
}
