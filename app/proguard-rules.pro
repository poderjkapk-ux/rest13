# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Защита моделей данных (очень важно для GSON) ---
# Сохраняем имена классов и полей во всех дата-классах, чтобы Gson мог их парсить
-keep class com.restify.rest.NominatimResult { *; }
-keep class com.restify.rest.NominatimAddress { *; }
-keep class com.restify.rest.PartnerOrder { *; }
-keep class com.restify.rest.CourierInfo { *; }
-keep class com.restify.rest.OrderCreateRequest { *; }
-keep class com.restify.rest.ChatMessage { *; }
-keep class com.restify.rest.TrackCourierResponse { *; }
-keep class com.restify.rest.PartnerProfile { *; }
-keep class com.restify.rest.VerifInitResp { *; }
-keep class com.restify.rest.VerifCheckResp { *; }

# --- Retrofit & OkHttp ---
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# --- Gson ---
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes EnclosingMethod

# --- OSMDroid (Карты OpenStreetMap) ---
-keep class org.osmdroid.** { *; }
-keep class org.metalev.multitouch.** { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile