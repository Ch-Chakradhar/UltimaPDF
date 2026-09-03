# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/chakri/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# ML Kit and PDF OCR ProGuard Rules
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class androidx.pdf.ocr.** { *; }
