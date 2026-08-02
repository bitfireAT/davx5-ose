# DAVx⁵ OSE – Project Structure

DAVx⁵ is a CalDAV/CardDAV synchronization client for Android. The repository is a multi-module Gradle project.

This repository contains the open-source code of DAVx⁵. There's another closed-source repository that contans
some extension code for variants like Managed DAVx⁵ and DAVx⁵ Select, but the main code with all functionality
is shared and the core development always takes place in davx5-ose.

> **Maintenance note for agents:** Keep this file up to date. When making changes that affect the structure described here — such as adding/removing Gradle modules, renaming packages, changing the DI framework, or replacing major dependencies — update the relevant sections of this file as part of the same change. Only reflect genuinely significant structural changes; don't update for routine additions like new classes or minor refactors.

## Gradle modules

Dependency direction: `:app-ose` uses `:core` uses `:synctools`

### `:app-ose` (`app-ose/`)

The Android application. Contains the product flavor `ose`, signing config, and the APK build. Has no business logic of its own — it wires together `:core` with app-level Hilt setup (`@HiltAndroidApp`) and provides the OSE-specific entry point.

### `:core` (`core/`)

Android library. The sync engine, database layer, and Jetpack Compose UI for DAVx⁵. It orchestrates CalDAV/CardDAV synchronization via dav4jvm and delegates content-provider access and format mapping to `:synctools`.

#### Dependency injection — Hilt

**Hilt is the DI framework for this module.** Follow these conventions:

- Use `@Inject` constructors everywhere; avoid manual instantiation.
- Add new Hilt bindings and qualifiers in `di/` (same style as the existing ones).
- ViewModels use `@HiltViewModel`.
- WorkManager workers integrate via the Hilt worker factory — do not construct workers manually.

#### Patterns

**Repository pattern** — DAOs (in `db/`) are always wrapped by a repository in `repository/`. UI code and sync managers talk to repositories, never to DAOs directly.

**ViewModel pattern** — Each Compose screen has a `@HiltViewModel` in `ui/`. Keep business logic out of Composables; Composables observe state from the ViewModel.

**Startup actions** — App-initialization hooks implement the `StartupAction` interface and are registered via set-based
Hilt injection. Do not add init logic directly to `CoreApp`.

**Background sync** — Sync runs in WorkManager workers (`sync/worker/`). The Hilt worker factory wires DI into workers.

#### Package map

```
db/           Room database — AppDatabase, DAOs, entities, migrations
di/           Hilt modules and qualifiers
repository/   Business-logic wrappers around DAOs
sync/         Sync managers (Calendar, Contacts, Tasks, Jtx) and workers, Android Account logic
network/      HTTP/WebDAV layer (Ktor + OkHttp + dav4jvm)
webdav/       WebDAV file operations
ui/           Compose screens, ViewModels, Activities
startup/      StartupAction interface and built-in actions
settings/     Preference management and migrations
push/         UnifiedPush / FCM integration
log/          Logging infrastructure
```

#### Key dependencies

- `:synctools` — content-provider access and format mapping
- `cert4android` — custom certificate management
- `dav4jvm` — WebDAV/CalDAV/CardDAV client

### `:synctools` (`synctools/`)

Technically a standalone Android library for bidirectional conversion between iCalendar/vCard data and Android content
providers (Calendar, Contacts, Tasks, Jtx). It is only consumed by `:core`.

**No Hilt or Dagger.** This is a pure library — no DI framework, no Android application components. Dependencies are passed via constructors or obtained directly (e.g. `ContentResolver`). Keep it that way. For logging, use `val logger\nget() = java.util.Logger.getLogger(javaClass.name)`.

#### Public API

- **`storage/`** — wraps Android content providers with typed domain objects (`AndroidCalendar`, `AndroidAddressBook`, `DmfsTaskList`, `JtxCollection`, `BatchOperation`, …). Changes here are breaking changes for `:core`.
- **`mapping/`** — bidirectional builder/handler pairs that convert between ical4j/ezvcard objects and Android provider rows. Each data type (calendar event, contact, DMFS task, Jtx object) has a `*Builder` (Android → iCal/vCard) and a `*Handler` (iCal/vCard → Android).

Internal utilities (`icalendar/`, `vcard/`, `log/`, `util/`) are not part of the public contract.

#### Architecture

```
storage/
  calendar/   AndroidCalendar, AndroidEvent, CalendarBatchOperation
  contacts/   AndroidAddressBook, AndroidContact, AndroidGroup
  tasks/      DmfsTaskList, DmfsTask (DMFS/OpenTasks provider)
  jtx/        JtxCollection, JtxObject (jtx Board provider)

mapping/
  calendar/
    builder/  ~30 builders composing AndroidEventBuilder
    handler/  ~30 handlers composing AndroidEventHandler
  contacts/
    builder/  DataRowBuilder hierarchy
    handler/  per-property handlers
  tasks/      DmfsTaskBuilder / DmfsTaskHandler
  jtx/        JtxObjectBuilder / JtxObjectHandler

icalendar/    ICalendarGenerator, ICalPreprocessor, Ical4jHelpers
vcard/        VCardParser, VCardGenerator
```

#### Testing

Instrumented tests run against real Android content providers — do not mock the provider layer. Test fixtures are published so `:core` can reuse them.

## Build infrastructure

### `build-logic/`

Gradle convention plugins (included via `includeBuild`, not a module listed in `settings.gradle.kts`). Defines the `davx5.common-buildconfig` plugin used by all three modules to share compile SDK, version info, and common build settings.
