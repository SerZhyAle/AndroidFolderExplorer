# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.afex.explorer.**$$serializer { *; }
-keepclassmembers class com.afex.explorer.** {
    *** Companion;
}
-keepclasseswithmembers class com.afex.explorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
