
DAVdroid is free and open-source software, licensed under the [GPLv3 License](COPYING).
If you like our project, please contribute to it.

# How to contribute

## Reporting issues

An issue might be a bug, an enhancement request or something in between. If you think you
have found a bug or if you want to request some enhancement, please:

1. Read the [Configuration](http://davdroid.bitfire.at/configuration) and [FAQ](http://davdroid.bitfire.at/faq/)
   pages carefully. The most common issues/usage challenges are explained there.
2. Search the Web for the problem, maybe ask competent friends or in forums.
3. Browse through the [open issues](https://github.com/rfc2822/davdroid/issues). You can
   also search the issues in the search field on top of the page. Please have a look
   into the closed issues, too, because many requests have already been handled (and can't/won't
   be fixed, for instance).
4. **[Fetch verbose logs](https://github.com/rfc2822/davdroid/wiki/How-to-view-the-logs) and prepare
   them. Remove `Authorization: Basic xxxxxx` headers and other private data.** Extracting the
   logs may be cumbersome work in the first time, but it's absolutely necessary in order to
   handle your issue.
5. [Create a new issue](https://github.com/rfc2822/davdroid/issues/new), containing
   * a useful summary of the problem ("Crash when syncing contacts with large photos" instead of "CRASH PLEASE HELP"),
   * your DAVdroid version and source ("DAVdroid 0.5.10 from F-Droid"),
   * your Android version and device model ("Samsung Galaxy S2 running Android 4.4.2 (CyanogenMod 11-20140504-SNAPSHOT-M6-i9100)"),
   * your CalDAV/CardDAV server software, version and hosting information ("OwnCloud 6, hosted on virtual server"),
   * a problem description, including **instructions on how to reproduce the problem** (we need to
     reproduce the problem before we can fix it!),
   * **verbose logs including the network traffic** (see step before). Enquote the logs with three backticks ```
     before and after, or post them onto http://gist.github.com and provide a link.


## Pull requests

We're very happy about pull requests for

* source code,
* documentation,
* translation (strings).

However, if you want to contribute source code, please talk with us in the
corresponding issue before because will only merge pull requests that

* match our product goals,
* have the necessary code quality,
* don't interfere with other near-term future development.

However, feel free to fork the repository and do your changes anyway
(that's why it's open-source). Just don't expect your strategic changes to be
merged if there's no consensus in the issue before.


## Donations

If you want to support this project, please also consider [donating to DAVdroid](http://davdroid.bitfire.at/donate)
or [purchasing it in one of the commercial stores](http://davdroid.bitfire.at/download).

