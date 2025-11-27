# Add project specific ProGuard rules here.
# You can find more details about ProGuard rules at https://www.guardsquare.com/en/products/proguard/manual/usage


# If you are using Kotlin reflect, you need to add the following rules:
-keep class kotlin.reflect.jvm.internal.** { *; }

# Keep the following rules for Firebase services
-keep class com.google.firebase.** { *; }

# Keep the following rules for Google Play services
-keep class com.google.android.gms.** { *; }

# Keep the following rules for Room
-keep class androidx.room.RoomDatabase { *; }

# Keep the following rules for coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep the following rules for Compose
-keep class androidx.compose.** { *; }
-keep class * implements androidx.compose.runtime.Composer { *; }
