
# ProGuard usage for DAVdroid:
# 	shrinking		yes - main reason for using ProGuard
# 	optimization		no - too risky
# 	obfuscation		no - DAVdroid is open-source
# 	preverification		no (Android default)

-dontobfuscate

# ez-vcard
-dontwarn com.fasterxml.jackson.**		# Jackson JSON Processor (for jCards) not used
-dontwarn freemarker.**				# freemarker templating library (for creating hCards) not used
-dontwarn org.jsoup.**				# jsoup library (for hCard parsing) not used
-dontwarn sun.misc.Perf
-keep class ezvcard.property.** { *; }		# keep all VCard properties (created at runtime)

# ical4j: ignore unused dynamic libraries
-dontwarn aQute.**
-dontwarn groovy.**				# Groovy-based ContentBuilder not used
-dontwarn org.codehaus.groovy.**
-dontwarn org.apache.commons.logging.**		# Commons logging is not available
-dontwarn net.fortuna.ical4j.model.**		# ignore warnings from Groovy dependency
-keep class net.fortuna.ical4j.model.** { *; }	# keep all model classes (properties/factories, created at runtime)

# okhttp
-dontwarn java.nio.file.**		# not available on Android
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# MemorizingTrustManager
-dontwarn de.duenndns.ssl.MemorizingTrustManager

# dnsjava
-dontwarn sun.net.spi.nameservice.**		# not available on Android

# DAVdroid + libs
-keep class at.bitfire.** { *; }	# all DAVdroid code is required
