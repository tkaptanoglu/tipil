# ═══════════════════════════════════════════════════════════
#  Retrofit + OkHttp
# ═══════════════════════════════════════════════════════════
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.tipil.app.data.remote.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# ═══════════════════════════════════════════════════════════
#  Gson (used by Retrofit converter)
# ═══════════════════════════════════════════════════════════
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# ═══════════════════════════════════════════════════════════
#  Room
# ═══════════════════════════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ═══════════════════════════════════════════════════════════
#  Firebase Auth
# ═══════════════════════════════════════════════════════════
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ═══════════════════════════════════════════════════════════
#  Google Identity / Credential Manager
# ═══════════════════════════════════════════════════════════
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ═══════════════════════════════════════════════════════════
#  ML Kit Barcode
# ═══════════════════════════════════════════════════════════
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ═══════════════════════════════════════════════════════════
#  Kotlinx Serialization
# ═══════════════════════════════════════════════════════════
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.tipil.app.**$$serializer { *; }
-keepclassmembers class com.tipil.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.tipil.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ═══════════════════════════════════════════════════════════
#  Coil (image loading)
# ═══════════════════════════════════════════════════════════
-dontwarn coil.**

# ═══════════════════════════════════════════════════════════
#  Coroutines
# ═══════════════════════════════════════════════════════════
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ═══════════════════════════════════════════════════════════
#  General: keep data class fields used in serialization
# ═══════════════════════════════════════════════════════════
-keep class com.tipil.app.data.local.BookEntity { *; }
-keep class com.tipil.app.data.local.StringListConverter { *; }
