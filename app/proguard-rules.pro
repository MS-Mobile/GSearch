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

# The same shape of failure as the Room rule above, from the same WorkManager 2.7.1, and it
# takes the widget with it. WorkManager builds a work request's input by reflectively
# instantiating an InputMerger — `Class.forName(name).newInstance()` — and 2.7.1's consumer
# rules only ask for
#
#   -keep class * extends androidx.work.InputMerger
#
# In full mode that keeps the class and drops its default constructor, so every enqueued
# worker died in WorkerWrapper before it ever started:
#
#   E WM-InputMerger: java.lang.InstantiationException:
#       java.lang.Class<androidx.work.OverwritingInputMerger> has no zero argument constructor
#   E WM-WorkerWrapper: Could not create Input Merger androidx.work.OverwritingInputMerger
#
# Glance renders through a worker (androidx.glance.session.SessionWorker), so in release
# builds the widget never composed at all: it sat on `initialLayout` — the stock
# search/mic/lens pill at full opacity — and no configuration change ever reached the home
# screen, while the settings screen saved everything correctly. Debug builds are not
# minified and so never showed it.
#
# The worker constructor below is the next link in the same chain: the default WorkerFactory
# instantiates a ListenableWorker by its (Context, WorkerParameters) constructor, equally
# reflectively. It has not been observed failing — WorkerWrapper aborts on the merger first
# — but it is kept for the same reason and by the same argument.
#
# WorkManager 2.8+ ships both itself; we carry them until Glance pulls a new enough one.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
