
[![Development tests](https://github.com/bitfireAT/synctools/actions/workflows/tests.yml/badge.svg)](https://github.com/bitfireAT/synctools/actions/workflows/tests.yml)
[![Documentation](https://img.shields.io/badge/documentation-kdoc-brightgreen)](https://bitfireat.github.io/synctools/)


# bitfireAT/synctools

This library for Android provides:

- low-level access to contacts, calendar and task content providers and
- mappings between iCalendar/vCard and the content providers.

It is mainly used by [DAVx⁵](https://www.davx5.com).

Generated KDoc: https://bitfireat.github.io/synctools/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/synctools/discussions).
We're happy about contributions! In case of bigger changes, please let us know in the discussions before.
Then make the changes in your own repository and send a pull request.


# Packages

- `at.bitfire.synctools`: new package where everything shall be refactored into
  - `.icalendar`: high-level operations on iCalendar objects
  - `.mapping`: mappers between low-level (database rows) and high-level (iCalendar/vCard) objects
  - `.storage`: low-level operations on content-provider storage (`ContentValues` / `Entity` to store data)
- `at.bitfire.ical4android`: legacy [ical4android](https://github.com/bitfireAT/ical4android)
- `at.bitfire.vcard4android`: legacy [vcard4android](https://github.com/bitfireAT/vcard4android)



# How to use

Add the [jitpack.io](https://jitpack.io) repository to your project's level `build.gradle`:
```groovy
allprojects {
    repositories {
        // ... more repos
        maven { url "https://jitpack.io" }
    }
}
```

or if you are using `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ... more repos
        maven { url "https://jitpack.io" }
    }
}
```

Then add the dependency to your module's `build.gradle` file:
```groovy
dependencies {
   implementation 'com.github.bitfireat:synctools:<commit-id>'
}
```

To view the available gradle tasks for the library: `./gradlew synctools:tasks`
(the `synctools` module is defined in `settings.gradle`).


## License

This library is released under GPLv3. Copyright © All Contributors.
See the LICENSE and AUTHOR files in the root directory for details.
