# Keep osmdroid's reflection-accessed classes.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OkHttp / Okio.
-dontwarn okhttp3.**
-dontwarn okio.**
