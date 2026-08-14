# GIZTV R8 rules.
#
# Compose, Media3, Coil, WorkManager and AndroidX all ship their own consumer rules, and anything
# named in AndroidManifest.xml is kept without being asked. What follows is the rest: the places
# where a class is reached by a name written down somewhere R8 cannot read.

# A stack trace that has been through R8 names no file and no line, which would leave the crash
# reports the app now collects saying almost nothing. Keeping these costs a little size and is the
# difference between a report worth reading and one that is not.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Exception names travel into those reports too, and a renamed one cannot be looked up.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Cast reaches its options provider through a class name held in a manifest meta-data *value*.
# R8 sees a string there, not a reference, so without this the class is removed and Chromecast
# fails at runtime in the release build alone.
-keep class androidx.media3.cast.DefaultCastOptionsProvider { *; }

# The phone remote sends an enum constant's own name across the wire and matches it by name at the
# other end. Both ends ship in one APK so a consistent renaming would still line up, but a phone
# and a television on different releases would not, and the failure would be a silent no-op in a
# settings list. The names are worth more than the bytes.
-keepclassmembers enum com.giztv.tv.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
