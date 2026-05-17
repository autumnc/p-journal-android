# Keep Gson serialization classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.pjournal.app.network.** { *; }
-keep class com.pjournal.app.data.db.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
