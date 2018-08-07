# okhttp3
-keep class okhttp3.Headers { *; }

# MoPub
-keep class com.mopub.mobileads.WebViewCacheService { *; }

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider

# jlibtorrent
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni {*;}

# keep all constructors
-keep class com.mopub.** {
    <init>(...);
}
-keep class android.support.** {
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
