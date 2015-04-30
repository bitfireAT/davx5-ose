This project represents an effort to provide an implementation of Apache HttpClient, which can be 
deployed on Google Android in parallel to the outdated version shipped with platform  while 
remaining partially API compatible with Apache HttpClient 4.3.  

Background
----------

Google Android 1.0 was released with a pre-BETA snapshot of Apache HttpClient 4.0. To coincide with 
the first Android release Apache HttpClient 4.0 APIs had to be frozen prematurely, while many of 
interfaces and internal structures were still not fully worked out. As Apache HttpClient 4.0 was 
maturing the project was expecting Google to incorporate the latest code improvements into their 
code tree. Unfortunately it did not happen. Version of Apache HttpClient shipped with Android has 
effectively became a fork. Eventually Google decided to discontinue further development of their 
fork while refusing to upgrade to the stock version of Apache HttpClient citing compatibility 
concerns as a reason for such decision. As a result those Android developers who would like to 
continue using Apache HttpClient APIs on Android cannot take advantage of newer features, performance 
improvements and bug fixes. 

Apache HttpClient 4.3 port for Android is intended to remedy the situation by providing
official releases compatible with Google Android.  


Differences with the stock version of Apache HttpClient 
----------

(1) Compiled against HttpClient 4.0 APIs.

(2) Commons Logging replaced with Android Logging.

(3) Base64 implementation from Commons Codec replaced with Android Base64.

(4) Android default SSLSocketFactory used by for SSL/TLS connections.


Compatibility notes 
----------

(1) HttpClient port for Android is compiled against the official Android SDK and is expected
to be fully compatible with any code consuming HttpClient services through its interface 
(HttpClient) rather than the default implementation (DefaultHttpClient).

(2) Code compiled against stock versions of Apache HttpClient 4.3 is not fully compatible with
the HttpClient port for Android. Some of the implementation classes had to be copied (or shaded)
with different names in order to avoid conflicts with the older versions of the same classes
included in the Android runtime. One can increase compatibility with the stock version of
HttpClient by avoiding 'org.apache.http.**.*HC4' classes.
