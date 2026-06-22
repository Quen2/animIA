# Conserve les classes annotées Serializable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.animia.mvp.**$$serializer { *; }
-keepclassmembers class com.animia.mvp.** {
    *** Companion;
}
-keepclasseswithmembers class com.animia.mvp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
