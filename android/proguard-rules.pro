# okhttp3
-keep class okhttp3.Headers { *; }

# MoPub
-keep class com.mopub.mobileads.WebViewCacheService { *; }

# presage seems to have passed by proguard already
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider
-keep class do.** {*;}
-keep class for.** {*;}
-keep class if.** {*;}
-keep class int.** {*;}

# applovin seems to have passed by proguard already
-keep class com.applovin.** {*;}

-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni {*;}

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
