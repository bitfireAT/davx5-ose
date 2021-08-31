
# R8 usage for DAVx⁵:
#    shrinking        yes (only in release builds)
#    optimization     yes (on by R8 defaults)
#    obfuscation      no (open-source)

-dontobfuscate
-printusage build/reports/r8-usage.txt

# ez-vcard: keep all vCard properties/parameters (used via reflection)
-keep class ezvcard.io.scribe.** { *; }
-keep class ezvcard.property.** { *; }
-keep class ezvcard.parameter.** { *; }

# ical4j: keep all iCalendar properties/parameters (used via reflection)
-keep class net.fortuna.ical4j.** { *; }

# XmlPullParser
-keep class org.xmlpull.** { *; }

# DAVx⁵ + libs
-keep class at.bitfire.** { *; }       # all DAVx⁵ code is required

# we use enum classes (https://www.guardsquare.com/en/products/proguard/manual/examples#enumerations)
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
