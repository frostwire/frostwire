# okhttp3
-keep class okhttp3.Headers { *; }
-keep class org.apache.http.client.utils.URLEncodedUtils { *; }
-keep class org.conscrypt.Conscrypt { *; }
-keep class android.net.http.AndroidHttpClient { *; }


# MoPub
-keep class com.mopub.mobileads.WebViewCacheService { *; }
-dontwarn com.mopub.common.MoPubReward
-dontwarn com.mopub.nativeads.*
-dontwarn com.mopub.mobileads.MoPubRewardedVideoManager
-dontwarn com.mopub.mobileads.CustomEventRewardedVideo

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider

# jlibtorrent
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni {*;}

-keep class libcore.io.Memory { *; }

# keep all constructors
-keep class * {
    <init>(...);
}

# to keep all the names and avoid code mangling
-keepnames class ** {*;}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontobfuscate
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
