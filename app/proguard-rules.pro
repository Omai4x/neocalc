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

# org.json is part of the platform; keep the warning quiet if R8 sees it.
-dontwarn org.json.**

# Line numbers make release crash reports readable while still obfuscating.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
