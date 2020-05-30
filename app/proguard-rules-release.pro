
# R8 usage for DAVx⁵:
#    shrinking        yes (only in release builds)
#    optimization     yes (on by R8 defaults)
#    obfuscation      no (open-source)

-dontobfuscate

# okhttp
-keepclassmembers class okhttp3.internal.Util.** { *; }

# ez-vcard
-keep class ezvcard.property.** { *; }  # keep all vCard properties (created at runtime)

# ical4j
-keep class net.fortuna.ical4j.** { *; }  # keep all model classes (properties/factories, created at runtime)

# DAVx⁵ + libs
-keep class at.bitfire.** { *; }       # all DAVx⁵ code is required

# we use enum classes (https://www.guardsquare.com/en/products/proguard/manual/examples#enumerations)
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
