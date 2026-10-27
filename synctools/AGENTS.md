# `:synctools` — Project Structure

Technically a standalone Android library for bidirectional conversion between iCalendar/vCard data and Android content
providers (Calendar, Contacts, Tasks, Jtx). It is only consumed by `:core`.

**No Hilt or Dagger.** This is a pure library — no DI framework, no Android application components. Dependencies are passed via constructors or obtained directly (e.g. `ContentResolver`). Keep it that way. For logging, use `val logger\nget() = java.util.Logger.getLogger(javaClass.name)`.

**Blocking is fine here.** `:synctools` methods are allowed to block — they're the layer that actually talks to `ContentProviderClient`. It's `:core`'s job (see `core/AGENTS.md`'s Blocking/suspending boundary note) to dispatch to an IO thread when calling into `:synctools`.

**Wrap `RemoteException`.** Any method that calls `ContentProviderClient` must catch `android.os.RemoteException` and rethrow it wrapped in `at.bitfire.synctools.storage.LocalStorageException` (see the many existing examples, e.g. `AndroidCalendar.updateEventRow()`).

## Public API

- **`storage/`** — wraps Android content providers with typed domain objects (`AndroidCalendar`, `AndroidAddressBook`, `DmfsTaskList`, `JtxCollection`, `BatchOperation`, …). Changes here are breaking changes for `:core`.
- **`mapping/`** — bidirectional builder/handler pairs that convert between ical4j/ezvcard objects and Android provider rows. Each data type (calendar event, contact, DMFS task, Jtx object) has a `*Builder` (Android → iCal/vCard) and a `*Handler` (iCal/vCard → Android).

Internal utilities (`icalendar/`, `vcard/`, `log/`, `util/`) are not part of the public contract.

## Architecture

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

## Testing

Instrumented tests run against real Android content providers — do not mock the provider layer. Test fixtures are published so `:core` can reuse them.
