
# R8 usage for DAVx⁵:
#    shrinking        yes (only in release builds)
#    optimization     yes (on by R8 defaults)
#      full-mode      no (see gradle.properties)
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

# AGP 8.2 and 8.3 seem to remove this class, but ezvcard.io uses it. See https://github.com/bitfireAT/davx5/issues/499
-keep class javax.xml.namespace.QName { *; }

# we use enum classes (https://www.guardsquare.com/en/products/proguard/manual/examples#enumerations)
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Additional rules which are now required since missing classes can't be ignored in R8 anymore.
# [https://developer.android.com/build/releases/past-releases/agp-7-0-0-release-notes#r8-missing-class-warning]
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn com.github.erosb.jsonsKema.**  # ical4j
-dontwarn com.sun.jna.**                 # dnsjava
-dontwarn groovy.**
-dontwarn java.beans.Transient
-dontwarn javax.cache.**                 # ical4j
-dontwarn javax.naming.NamingException   # dnsjava
-dontwarn javax.naming.directory.**      # dnsjava
-dontwarn junit.textui.TestRunner
-dontwarn lombok.**                      # dnsjava
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.joda.**
-dontwarn org.jparsec.**                 # ical4j
-dontwarn org.json.*
-dontwarn org.jsoup.**
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.xmlpull.**
-dontwarn sun.net.spi.nameservice.NameService
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
