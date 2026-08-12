# R8 rules for the release build.
#
# Compose, Kotlin and AndroidX all ship their own consumer rules, so almost
# nothing is needed here. What follows covers the two places this app steps
# outside what R8 can see statically.

# CalculatorStateSaver and HistoryListSaver rebuild these from a Bundle by name
# after process death. They are plain data classes with no reflection, but
# keeping their members makes the restored state independent of R8 renaming.
-keep class com.omai.neocalc.calculator.CalculatorState { *; }
-keep class com.omai.neocalc.history.HistoryEntry { *; }

# Enum valueOf(String) is called on these when restoring saved state, and R8
# cannot see those call sites through the string.
-keepclassmembers enum com.omai.neocalc.calculator.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# UnitCategory and AlertDirection are written into stored JSON by name and read
# back with valueOf, so their constant names must survive obfuscation. Getting
# this wrong loses a user's custom units and rate alerts on the first release
# build, silently, which is exactly the kind of bug that never shows in debug.
-keepclassmembers enum com.omai.neocalc.convert.UnitCategory {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.omai.neocalc.alerts.AlertDirection {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WorkManager stores its state in a Room database, and Room finds the generated
# implementation of that database by *name* at runtime:
#
#     Class.forName(databaseClass.canonicalName + "_Impl")
#
# R8 cannot see a call like that, so with minification on it removes the
# generated class and WorkManager's startup provider throws before a single
# frame is drawn. The app then crashes on launch in release builds only, which
# is exactly what happened in 1.3.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
-keep class androidx.room.RoomDatabase { *; }

# Workers are the same problem one level up: WorkManager persists the worker's
# class name in that database and instantiates it reflectively when the job
# runs. An obfuscated name would still start the app but would silently break
# every rate alert, which is worse - it would look like the feature simply
# never fires.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.omai.neocalc.alerts.RateAlertWorker { *; }

# androidx.startup finds initializers by name from the manifest's provider.
-keep class * extends androidx.startup.Initializer { <init>(); }

# org.json is part of the platform; keep the warning quiet if R8 sees it.
-dontwarn org.json.**

# Line numbers make release crash reports readable while still obfuscating.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
