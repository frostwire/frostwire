-keepnames class * { *; }

-keep class com.frostwire.jlibtorrent.** { *; }
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni { *; }

# ---- for general android's frameworks
-keepattributes *Annotation*
-keep public class com.google.vending.licensing.ILicensingService
-keep public class com.android.vending.licensing.ILicensingService

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# ----

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontoptimize
-dontpreverify
-dontobfuscate
-verbose

-dontwarn android.**
-dontwarn org.apache.**
-dontwarn okio.**
-dontwarn rx.**
-dontwarn com.frostwire.**
