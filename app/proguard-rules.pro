# ====================================================================
# 1. СОХРАНЕНИЕ МЕТАДАННЫХ И АННОТАЦИЙ (САМОЕ ВАЖНОЕ)
# ====================================================================
# Это навсегда убивает ошибку "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType"
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, *Annotation*

# ====================================================================
# 2. ЗАЩИТА ВАШЕГО ПАКЕТА (МОДЕЛИ, API, VIEWMODEL, ЭКРАНЫ)
# ====================================================================
# Запрещаем трогать абсолютно всё, что вы написали в папке restify
-keep class com.restify.rest.** { *; }
-keepclassmembers class com.restify.rest.** { *; }
-keep interface com.restify.rest.** { *; }

# ====================================================================
# 3. ЗАЩИТА ИНТЕРФЕЙСОВ RETROFIT
# ====================================================================
# Сохраняем все методы, у которых есть аннотации @GET, @POST и т.д.
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# ====================================================================
# 4. СЕТЕВЫЕ БИБЛИОТЕКИ: RETROFIT & OKHTTP
# ====================================================================
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ====================================================================
# 5. КОРУТИНЫ (ИСПРАВЛЯЕТ ОШИБКУ suspend ФУНКЦИЙ)
# ====================================================================
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.** { *; }

# ====================================================================
# 6. GSON (ДЛЯ ПРАВИЛЬНОГО ПАРСИНГА JSON С СЕРВЕРА)
# ====================================================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class sun.misc.Unsafe { *; }

# ====================================================================
# 7. FIREBASE & OSMDroid (ПУШИ И КАРТЫ)
# ====================================================================
-keep class com.google.firebase.** { *; }
-keep class org.osmdroid.** { *; }
-keep class org.metalev.multitouch.** { *; }