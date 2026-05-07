
# keep all iCalendar properties/parameters (referenced over ServiceLoader)
-keep class net.fortuna.ical4j.** { *; }

# ical4j: don't warn when these are missing
-dontwarn com.github.benmanes.caffeine.**
-dontwarn com.github.erosb.jsonsKema.**
-dontwarn groovy.**
-dontwarn java.beans.Transient
-dontwarn java.time.zone.ZoneRulesProvider
-dontwarn javax.cache.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.joda.convert.ToString
-dontwarn org.jparsec.**

# keep all vCard properties/parameters (used via reflection)
-keep class ezvcard.io.scribe.** { *; }
-keep class ezvcard.property.** { *; }
-keep class ezvcard.parameter.** { *; }

# AGP seems to remove this class, but ezvcard.io uses it. See https://github.com/bitfireAT/davx5/issues/499
-keep class javax.xml.namespace.QName { *; }

# ez-vcard: don't warn about jsoup
-dontwarn org.jsoup.**

# synctools provides test rules which are only needed for testing
-dontwarn androidx.test.**
-dontwarn org.junit.**