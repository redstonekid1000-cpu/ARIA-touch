# Add project specific ProGuard rules here.
-keep class com.aria.assistant.MainActivity$AndroidBridge { *; }
-keepclassmembers class com.aria.assistant.MainActivity$AndroidBridge {
    public *;
}
