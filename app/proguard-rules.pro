# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Glance drags in WorkManager 2.7.1, which drags in Room 2.2.5, whose consumer rules predate
# R8 full mode (the default since AGP 8) and read only:
#
#   -keep class * extends androidx.room.RoomDatabase
#
# In full mode that keeps the class but NOT its default constructor, and Room instantiates
# the generated `<Database>_Impl` reflectively via getDeclaredConstructor().newInstance().
# So the release build stripped WorkDatabase_Impl.<init>() and every launch died in
# androidx.startup's WorkManagerInitializer with "Failed to create an instance of
# androidx.work.impl.WorkDatabase". Room 2.3+ ships this exact rule itself; we carry it until
# WorkManager (and with it Room) is new enough to bring its own.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
