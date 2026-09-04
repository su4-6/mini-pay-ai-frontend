# Dependency libraries provide their own consumer rules. Preserve Kotlin serialization metadata
# for API payload models and native SDK entry points that are discovered by name at runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep,includedescriptorclasses class com.minipay.mobile.**$$serializer { *; }
-keepclassmembers class com.minipay.mobile.** {
    *** Companion;
}
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
