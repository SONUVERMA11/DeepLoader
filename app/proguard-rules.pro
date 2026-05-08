# DeepLoader ProGuard Rules
# Developer: Sonu Verma

# ─── Keep Hilt generated code ───
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─── Keep Room entities ───
-keep class com.sonuverma.deeploader.data.db.** { *; }

# ─── Keep Gson models ───
-keep class com.sonuverma.deeploader.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ─── Keep NewPipe Extractor classes ───
-keep class org.schabi.newpipe.extractor.** { *; }

# ─── Keep OkHttp ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── Keep Jsoup ───
-keep class org.jsoup.** { *; }

# ─── Keep libtorrent4j (native JNI bindings) ───
-keep class org.libtorrent4j.** { *; }
-keep class com.frostwire.jlibtorrent.** { *; }
-dontwarn org.libtorrent4j.**
-dontwarn com.frostwire.jlibtorrent.**

# ─── Keep Kotlin coroutines ───
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ─── Keep Compose ───
-keep class androidx.compose.** { *; }

# ─── Keep Media3/ExoPlayer ───
-keep class androidx.media3.** { *; }

# ─── General Android rules ───
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepclassmembers class * extends android.app.Activity { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }

# ─── Suppress warnings for missing classes ───
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
