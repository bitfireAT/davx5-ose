
# keep rules
-keep class at.bitfire.** { *; }        # all DAVx5 code is required
-keep class org.xmlpull.** { *; }

# Additional rules which are now required since missing classes can't be ignored in R8 anymore.
# [https://developer.android.com/build/releases/past-releases/agp-7-0-0-release-notes#r8-missing-class-warning]
-dontwarn org.xmlpull.**

# dnsjava
-dontwarn com.sun.jna.**
-dontwarn lombok.**
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.**
-dontwarn sun.net.spi.nameservice.NameService
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider

# okhttp
# https://github.com/bitfireAT/davx5/issues/711 / https://github.com/square/okhttp/issues/8574
-keep class okhttp3.internal.idn.IdnaMappingTable { *; }
-keep class okhttp3.internal.idn.IdnaMappingTableInstanceKt{ *; }
