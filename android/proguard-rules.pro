# okhttp3
-dontwarn okhttp3.**
-dontwarn okio.**
#D8: Type `org.conscrypt.Conscrypt` was not found
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn javax.annotation.**
-keep class okhttp3.Headers { *; }
-keep class org.apache.http.client.utils.URLEncodedUtils { *; }
-keep class org.conscrypt.Conscrypt { *; }
-keep class org.conscrypt.org.Conscrypt$ProviderBuilder { *; }
#-keep class androidx.core.view.accessibility.AccessibilityManagerCompat$TouchExplorationStateChangeListenerWrapper { *; }
-keep class android.net.http.AndroidHttpClient { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }
-keep class org.conscrypt.ConscryptHostnameVerifier { *; }
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

#  VUNGLE
-dontwarn com.vungle.**
-dontnote com.vungle.**
-keep class com.vungle.** { *; }
-keep class javax.inject.*
-dontwarn de.greenrobot.event.util.**
-dontwarn rx.internal.util.unsafe.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
-keep class rx.schedulers.Schedulers { public static <methods>; }
-keep class rx.schedulers.ImmediateScheduler { public <methods>; }
-keep class rx.schedulers.TestScheduler { public <methods>; }
-keep class rx.schedulers.Schedulers { public static ** test(); }
# EOVUNGLE

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

# Applovin
-keepattributes Signature,InnerClasses,Exceptions,Annotation
-keep public class com.applovin.sdk.AppLovinSdk{ *; }
-keep public class com.applovin.sdk.AppLovin* { public protected *; }
-keep public class com.applovin.nativeAds.AppLovin* { public protected *; }
-keep public class com.applovin.adview.* { public protected *; }
-keep public class com.applovin.mediation.* { public protected *; }
-keep public class com.applovin.mediation.ads.* { public protected *; }
-keep public class com.applovin.impl.*.AppLovin { public protected *; }
-keep public class com.applovin.impl.**.*Impl { public protected *; }
-keepclassmembers class com.applovin.sdk.AppLovinSdkSettings { private java.util.Map localSettings; }
-keep class com.applovin.mediation.adapters.** { *; }
-keep class com.applovin.mediation.adapter.**{ *; }

# GoogleBillingClient
-keep class com.android.billingclient.api.BillingClientImpl$* { *; }

# jlibtorrent
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni {*;}

-keep class libcore.io.Memory { *; }

# GMS
-keep public class com.google.android.gms.** { public protected *; }

# keep all constructors
-keep class * {
    <init>(...);
}

-keep public class * implements androidx.versionedparcelable.VersionedParcelable {
  <init>();
}

# to keep all the names and avoid code mangling
-keepnames class ** {*;}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontobfuscate
#-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
