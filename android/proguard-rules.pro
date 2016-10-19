-dontobfuscate

# only remove unused stuff and keep the app small
-keepnames interface ** { *; }
-keepnames class ** { *; }
-keepnames enum ** { *; }

-dontwarn com.google.android.**
-dontwarn com.inmobi.**
-dontwarn com.moat.**
-dontwarn okio.**
-dontwarn com.squareup.okhttp.**
