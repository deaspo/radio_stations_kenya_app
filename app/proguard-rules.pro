# R8 rules for the release and staging build types.
#
# Written for R8 full mode, which is the default from AGP 8: `-keep class A`
# there does NOT imply keeping A's constructors, so members are named explicitly.
#
# Media3, Coil, Material, Cast and Firebase all ship consumer rules of their own.
# Only the things R8 genuinely cannot see are listed here.

# --- Google Cast --------------------------------------------------------------
# CastOptionsProvider is named only as a *string* in a manifest <meta-data>
# value, so R8 has no reference to it and will strip it. The Cast framework then
# fails to initialise and CastContext.getSharedInstance throws on first launch.
-keep class com.example.kenyanradiostations.CastOptionsProvider {
    <init>();
}

# NotificationOptions.setTargetActivityClassName is given MainActivity's name at
# runtime. MainActivity survives because it is in the manifest, but be explicit:
# this is a by-name reference, not a code reference.
-keep class com.example.kenyanradiostations.MainActivity {
    <init>();
}

# --- Crashlytics --------------------------------------------------------------
# Without these, release stack traces have no line numbers and are close to
# useless. The uploaded mapping file is what makes them readable again, so keep
# the attributes and let the mapping do the de-obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin -------------------------------------------------------------------
# @Parcelize CREATOR fields are already covered by the Parcelable rule in
# proguard-android-optimize.txt. Coroutine and metadata rules ship with the
# Kotlin stdlib artifacts.

# --- Notes --------------------------------------------------------------------
# Deliberately NOT here:
#   * Model keep rules. Nothing in this app is deserialised by field name any
#     more - parsing is org.json by explicit key and Jsoup by selector, both of
#     which read string literals rather than member names.
#   * Enum name rules. Settings.Theme persists an explicit `key` string rather
#     than Enum.name, so obfuscating the constants cannot corrupt a saved
#     preference.
