# Preserve source file names and line numbers for Play Console stack trace deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization (bundled signatures, DataStore whitelist/logs, Navigation3 NavKeys)
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.windyflow.smartglassscanner.ble.**$$serializer { *; }
-keep,includedescriptorclasses class com.windyflow.smartglassscanner.data.**$$serializer { *; }
-keep,includedescriptorclasses class com.windyflow.smartglassscanner.**$$serializer { *; }

-keepclassmembers class com.windyflow.smartglassscanner.** {
    *** Companion;
}

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keep @kotlinx.serialization.Serializable class com.windyflow.smartglassscanner.** { *; }
