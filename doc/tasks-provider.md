# DAVx⁵ Tasks Provider — Design & Scoping

Status: **draft for discussion**, working document — not for commit.
Context: [bitfireAT/davx5-ose#2740](https://github.com/bitfireAT/davx5-ose/discussions/2740)

**Goal:** a DAVx⁵-hosted Android `ContentProvider` for VTODO data that third-party
task apps ("frontends") bind to at runtime, covering the full RFC 5545 VTODO feature
set — with every element of the contract traceable to a normative source.

---

## 0. Traceability policy

The Android side of this contract has **no standards authority** to appeal to. There
is no AOSP tasks contract; `org.dmfs.tasks.contract.TaskContract` (vendored at
`synctools/opentasks-contract/`, 1717 lines) is one app author's design, and its
column set is older and narrower than RFC 5545. jtx Board has a second, richer,
incompatible model.

The data model, by contrast, *is* settled. So the rule for this contract is:

> **Every column, mimetype and enum value MUST cite a normative source: an RFC
> section, or an explicit "DAVx⁵ sync-local" marker. No unattributed columns.**

This is the strongest legitimacy argument available for a contract nobody has the
standing to bless, and it is what makes the contract reviewable by the OpenTasks /
tasks.org / jtx authors rather than merely assertable at them.

### Normative sources

| Source | Title | Role here |
|---|---|---|
| **RFC 5545** | Internet Calendaring and Scheduling Core Object Specification | Baseline. §3.6.2 defines VTODO as a *closed* ABNF grammar; `x-prop`/`iana-prop` are the only escape hatches. Obsoletes RFC 2445. |
| **IANA iCalendar Element Registries** | (est. by RFC 5545 §8.2) | The living authority for what counts as a registered property/parameter. |
| **RFC 7986** | New Properties for iCalendar | Adds COLOR, IMAGE, CONFERENCE to VTODO. |
| **RFC 9253** | Support for iCalendar Relationships | Task dependencies: extended RELTYPE values, GAP parameter, LINK / CONCEPT / REFID. |
| **RFC 9074** | VALARM Extensions for iCalendar | Alarm UID, ACKNOWLEDGED, PROXIMITY, snooze via RELATED-TO. |
| **RFC 7529** | Non-Gregorian Recurrence Rules | RSCALE parameter. Out of scope v1. |
| **RFC 6638** | Scheduling Extensions to CalDAV | SCHEDULE-* parameters. Transport layer, already in `doc/`. |
| **RFC 8607** | CalDAV Managed Attachments | MANAGED-ID. Out of scope v1. |

Explicitly **excluded**: `EXRULE` is RFC 2445 legacy, *removed* by RFC 5545. It gets
no column. Preserving it as an opaque property — which
`mapping/tasks/builder/UnknownPropertiesBuilder` already does deliberately — is the
correct treatment.

> **Verification TODO before the contract is frozen:** section numbers for RFC 5545
> and RFC 7986 are cited inline below and are believed accurate. RFC 9253 and
> RFC 9074 are cited at RFC level only; their section numbers must be filled in
> against the published texts. Suggest vendoring `rfc5545`, `rfc7986`, `rfc9074`,
> `rfc9253` into `doc/` alongside the existing `rfc4791` / `rfc6638` texts, matching
> repo practice.

---

## 1. Why a new contract — the concrete gap

Everything not listed in `UnknownPropertiesBuilder.KNOWN_PROPERTY_NAMES` is
serialized to an opaque JSON blob (`UnknownProperty.toJsonString`) in a `Properties`
row. The data **survives a sync round-trip** but is **invisible and uneditable for
any frontend**. It is not corruption; it is a dead letter box.

Four properties are VTODO-exclusive: DUE, COMPLETED, PERCENT-COMPLETE, and the
VTODO-specific STATUS value set (`NEEDS-ACTION` / `COMPLETED` / `IN-PROCESS` /
`CANCELLED`, RFC 5545 §3.8.1.11) — not VEVENT's `TENTATIVE`/`CONFIRMED`.

| Property | Authority | DMFS path today | Proposed home |
|---|---|---|---|
| UID, SUMMARY, DESCRIPTION, LOCATION, GEO, URL, CLASS, STATUS, PRIORITY, PERCENT-COMPLETE, COMPLETED, CREATED, LAST-MODIFIED, SEQUENCE, DTSTART, DUE, DURATION | 5545 | mapped | `Tasks` main row |
| ORGANIZER | 5545 §3.8.4.3 | mapped, **params dropped** (no CN / SENT-BY) | `Tasks` + params |
| CATEGORIES, COMMENT | 5545 §3.8.1.2/.4 | mapped (sub-rows) | `Properties` |
| RRULE, RDATE, EXDATE | 5545 §3.8.5 | mapped | `Tasks` |
| RELATED-TO | 5545 §3.8.4.5 | mapped, **PARENT/CHILD/SIBLING only** | `Properties` |
| RELTYPE extensions (`DEPENDS-ON`, `FINISHTOSTART`, …), GAP, LINK / CONCEPT / REFID | **9253** | not supported | `Properties` — *the* task-dependency spec |
| VALARM | 5545 §3.6.6 | **lossy** — collapsed to `minutes_before` + START/DUE reference. Absolute triggers, DURATION, REPEAT, alarm ATTENDEE/ATTACH all lost | `Alarms` table |
| VALARM UID / ACKNOWLEDGED / PROXIMITY / snooze RELATED-TO | **9074** | not supported | `Alarms` table |
| **ATTENDEE** | 5545 §3.8.4.1 | opaque JSON | `Properties` |
| **ATTACH** | 5545 §3.8.1.1 | opaque JSON, **dropped entirely if > 25 000 octets** (`MAX_UNKNOWN_PROPERTY_SIZE`) → real data loss for inline BINARY | `Properties` + `openFile()` |
| **CONTACT** | 5545 §3.8.4.2 | opaque JSON | `Properties` |
| **RESOURCES** | 5545 §3.8.1.10 | opaque JSON | `Properties` |
| **REQUEST-STATUS** | 5545 §3.8.8.3 | opaque JSON | `Properties` |
| **RECURRENCE-ID** | 5545 §3.8.4.4 | **exceptions dropped at build time** (`DmfsTaskBuilder`, #2357) | `Tasks.original_instance_*` |
| DTSTAMP | 5545 §3.8.7.2 | explicitly discarded, regenerated on write | `Tasks.dtstamp` |
| COLOR | **7986 §5.9** | mapped | `Tasks.color` |
| IMAGE | **7986 §5.10** | opaque JSON | `Properties` (v2) |
| CONFERENCE | **7986 §5.11** | opaque JSON | `Properties` (v2) |
| RSCALE param | **7529** | not supported | out of scope v1 |
| SCHEDULE-AGENT / SCHEDULE-STATUS | **6638** | not supported | CalDAV layer, not provider schema |
| EXRULE | 2445, **removed by 5545** | opaque JSON (deliberate) | stays opaque — correct as-is |
| X- / IANA properties | 5545 §3.8.8.1/.2 | opaque JSON (correct) | `Properties/unknown-property` |

**7 RFC 5545 properties unusable, 2 lossy, 1 feature (recurrence exceptions)
unimplemented** — plus the whole of RFC 9253 task dependencies.

This table is the justification for the project. Lead the discussion with it.

---

## 2. Decisions to make before any code

Cheap now, extremely expensive after v1 — the contract becomes a frozen public API.

### D1 — Authority discovery (blocks everything)

A fixed authority (`at.bitfire.davdroid.tasks`) means only one DAVx⁵ variant can be
installed at a time: OSE, Managed DAVx⁵ and Select would collide on both the
authority and the permission definitions.

**Recommendation: discovery by intent action** — the DocumentsProvider pattern.

```xml
<provider android:name=".tasks.provider.DavTasksProvider"
          android:authorities="${tasksAuthority}"
          android:exported="true"
          android:grantUriPermissions="true"
          android:readPermission="at.bitfire.tasks.permission.READ"
          android:writePermission="at.bitfire.tasks.permission.WRITE">
    <intent-filter>
        <action android:name="at.bitfire.tasks.action.TASKS_PROVIDER" />
    </intent-filter>
</provider>
```

```kotlin
// frontend
val authority = packageManager
    .queryIntentContentProviders(Intent("at.bitfire.tasks.action.TASKS_PROVIDER"), 0)
    .firstOrNull()?.providerInfo?.authority
```

plus `<queries><intent><action android:name="at.bitfire.tasks.action.TASKS_PROVIDER"/></intent></queries>`
for API 30+ package visibility.

This makes the authority a runtime value while keeping **permission names fixed** —
which they must be, since `<uses-permission>` cannot be dynamic. Permission names
should therefore be vendor-neutral (`at.bitfire.tasks.permission.*`, not
`...davdroid...`) so a future non-DAVx⁵ implementation can serve the same frontends.

### D2 — Component scope

VTODO only for v1, but include a `component` column from day one (jtx does this) so
VJOURNAL can be added without a breaking change. Do **not** attempt to subsume jtx
Board in v1 — its model is larger and it remains a separate sync path regardless.

### D3 — Who expands recurrences?

**The provider.** If frontends do it, each reimplements RFC 5545 §3.3.10 RECUR and
they disagree. An `Instances` table is the single largest reason a frontend would
prefer this provider over talking to a sync app directly.

### D4 — Sub-row column style

Generic `data1..dataN` with per-mimetype documented meaning (ContactsContract style),
not named columns per mimetype. New mimetypes then ship without a DB migration —
which is why ContactsContract has survived 15 years. Directly relevant here: it is
what lets RFC 7986 IMAGE/CONFERENCE and RFC 9253 LINK/CONCEPT/REFID land in v2
without breaking v1 clients.

### D5 — Store raw iCalendar alongside the parsed model?

**No.** Keep the `UnknownProperty` JSON row approach synctools already uses. A raw
copy inevitably diverges from the structured rows, producing two sources of truth.

### D6 — Separate database

The provider must **not** live in `AppDatabase` (`services.db`), which has
`fallbackToDestructiveMigration(dropAllTables = true)` (`db/AppDatabase.kt:88`) — an
acceptable last resort for cached service metadata, catastrophic for user task data.
Use a separate Room DB (`tasks.db`) with its own migration cadence and no destructive
fallback.

---

## 3. Contract — traceability matrix

Legend for **Source**: `5545 §x` = RFC 5545 section · `7986`/`9074`/`9253` = that RFC ·
**`SYNC`** = DAVx⁵ sync-local, no iCalendar counterpart · **`PROV`** = provider
bookkeeping, no iCalendar counterpart.

### 3.1 `TaskLists`

| Column | Source | Notes |
|---|---|---|
| `_id` | `PROV` | |
| `account_name`, `account_type` | `SYNC` | Android account binding |
| `_sync_id` | `SYNC` | DAVx⁵ `Collection.id` |
| `sync_version` | `SYNC` | serialized `SyncState` |
| `sync1..sync4` | `SYNC` | sync-adapter private |
| `list_name` | RFC 4791 `displayname` | CalDAV collection property |
| `list_description` | RFC 4791 `calendar-description` | |
| `list_color` | Apple `calendar-color` / 7986 §5.9 | de-facto CalDAV property |
| `list_owner` | RFC 4791 / 3744 owner | |
| `access_level` | RFC 3744 ACL | derived from `privWriteContent` / `forceReadOnly` |
| `visible`, `sync_enabled` | `PROV` | user/UI state |
| `supported_components` | RFC 4791 `supported-calendar-component-set` | `VTODO` for v1 (D2) |

### 3.2 `Tasks` — one row per VTODO component (main *or* recurrence override)

| Column | Source | Notes |
|---|---|---|
| `_id`, `list_id` | `PROV` | |
| `_uid` | 5545 §3.8.4.7 | UID |
| `_sync_id`, `_dirty`, `_deleted`, `sync_version`, `sync1..sync4` | `SYNC` | |
| `original_instance_id`, `original_instance_time`, `original_instance_allday` | 5545 §3.8.4.4 | RECURRENCE-ID — **unblocks #2357** |
| `summary` | 5545 §3.8.1.12 | |
| `description` | 5545 §3.8.1.5 | |
| `location` | 5545 §3.8.1.7 | |
| `geo_lat`, `geo_lon` | 5545 §3.8.1.6 | GEO is `float;float` |
| `url` | 5545 §3.8.4.6 | |
| `color` | **7986 §5.9** | CSS3 colour name |
| `organizer`, `organizer_cn`, `organizer_sent_by` | 5545 §3.8.4.3 + §3.2.2, §3.2.18 | params currently dropped |
| `priority` | 5545 §3.8.1.9 | 0–9 |
| `classification` | 5545 §3.8.1.3 | PUBLIC / PRIVATE / CONFIDENTIAL |
| `status` | 5545 §3.8.1.11 | VTODO set only |
| `percent_complete` | 5545 §3.8.1.8 | VTODO-exclusive |
| `completed`, `completed_allday` | 5545 §3.8.2.1 | VTODO-exclusive |
| `sequence` | 5545 §3.8.7.4 | |
| `created` | 5545 §3.8.7.1 | |
| `last_modified` | 5545 §3.8.7.3 | |
| `dtstamp` | 5545 §3.8.7.2 | **currently discarded** |
| `dtstart`, `dtstart_tz` | 5545 §3.8.2.4 + §3.2.19 | TZID param |
| `due`, `due_tz` | 5545 §3.8.2.3 | VTODO-exclusive; mutually exclusive with `duration` |
| `duration` | 5545 §3.8.2.5 | requires `dtstart` per §3.6.2 |
| `is_allday` | 5545 §3.3.4 vs §3.3.5 | DATE vs DATE-TIME value type |
| `rrule` | 5545 §3.8.5.3 | RECUR, §3.3.10 |
| `rdate` | 5545 §3.8.5.2 | |
| `exdate` | 5545 §3.8.5.1 | *(no `exrule` — removed by 5545)* |

Provider MUST enforce the §3.6.2 grammar constraints: DUE xor DURATION; DURATION
implies DTSTART; main row has no RECURRENCE-ID.

### 3.3 `Properties` — mimetype-discriminated sub-rows

`_id`, `task_id`, `mimetype`, `data1..data15`, `data_sync1..4`

| Mimetype | Source | Payload |
|---|---|---|
| `…/category` | 5545 §3.8.1.2 | value; LANGUAGE §3.2.10 |
| `…/comment` | 5545 §3.8.1.4 | value; ALTREP §3.2.1, LANGUAGE |
| `…/relation` | 5545 §3.8.4.5 + **9253** | target UID; RELTYPE §3.2.15 incl. 9253 values (`DEPENDS-ON`, `FINISHTOSTART`, `FINISHTOFINISH`, `STARTTOSTART`, `STARTTOFINISH`, `FIRST`, `NEXT`, …); GAP param (9253) |
| `…/attendee` | 5545 §3.8.4.1 | CAL-ADDRESS; CN §3.2.2, CUTYPE §3.2.3, DELEGATED-FROM §3.2.4, DELEGATED-TO §3.2.5, DIR §3.2.6, LANGUAGE §3.2.10, MEMBER §3.2.11, PARTSTAT §3.2.12, ROLE §3.2.16, RSVP §3.2.17, SENT-BY §3.2.18; SCHEDULE-STATUS (**6638**) |
| `…/attachment` | 5545 §3.8.1.1 | URI or inline BINARY; FMTTYPE §3.2.8, ENCODING §3.2.7. Blob via `openFile()` |
| `…/contact` | 5545 §3.8.4.2 | value; ALTREP, LANGUAGE |
| `…/resource` | 5545 §3.8.1.10 | value; LANGUAGE |
| `…/request-status` | 5545 §3.8.8.3 | statcode;statdesc;extdata |
| `…/unknown-property` | 5545 §3.8.8.1/.2 | X-/IANA props as `UnknownProperty` JSON |
| *(v2)* `…/image` | **7986 §5.10** | |
| *(v2)* `…/conference` | **7986 §5.11** | |
| *(v2)* `…/link`, `…/concept`, `…/refid` | **9253** | |

The `attendee` mimetype alone consumes ~11 data columns — size `dataN` accordingly.

### 3.4 `Alarms` — VALARM (own table, not a `Properties` mimetype)

VALARM (5545 §3.6.6) is a sub-component with its own multi-valued children, so it
cannot be one flat row.

| Column | Source |
|---|---|
| `_id`, `task_id` | `PROV` |
| `action` | 5545 §3.8.6.1 — AUDIO / DISPLAY / EMAIL |
| `trigger_relative`, `trigger_related` | 5545 §3.8.6.3 + RELATED §3.2.14 (START/END) |
| `trigger_absolute` | 5545 §3.8.6.3, DATE-TIME form |
| `duration`, `repeat` | 5545 §3.8.2.5, §3.8.6.2 |
| `description`, `summary` | 5545 §3.8.1.5, §3.8.1.12 |
| `uid`, `acknowledged`, `related_to`, `proximity` | **9074** |

`AlarmProperties` (keyed by `alarm_id`) carries alarm ATTENDEE (EMAIL action) and
ATTACH (AUDIO action).

The current `mapping/tasks/builder/AlarmsBuilder` collapses all of this to
`minutes_before` + a START/DUE enum. Full-fidelity means not doing that.

### 3.5 `Instances` — provider-maintained, read-mostly

| Column | Source |
|---|---|
| `_id`, `task_id` | `PROV` |
| `instance_start`, `instance_due` | derived from 5545 §3.8.5 expansion |
| `instance_start_sorting`, `instance_due_sorting` | `PROV` — local-time-normalised so `ORDER BY` works across timezones |
| `distance_from_current` | `PROV` |

The sorting columns are not optional: CalendarProvider and DMFS both need them.

---

## 4. Provider semantics

Where content providers actually go wrong.

- **`CALLER_IS_SYNCADAPTER` query param** — controls tombstone-vs-real-delete and
  suppresses `_dirty`. Plus `account_name` / `account_type` params.
- **Dirty propagation from sub-rows to parent.** Inserting a category must dirty the
  task. This exact bug class already hit the DMFS path — commit `f16a34a68`,
  *"Fix task dirty flag regression with properties"*. Test it from day one.
- **Tombstones** (`_deleted`) and sub-row cascade rules.
- **`notifyChange(uri, null, syncToNetwork = !callerIsSyncAdapter)`** — drives
  content-triggered sync. Wrong granularity produces sync storms.
- **SQL injection defence.** An exported provider receives `selection` strings from
  untrusted callers: `SQLiteQueryBuilder` + `setStrict(true)` + `setProjectionMap()`
  + `setStrictColumns()`. Non-negotiable.
- `applyBatch()` in one transaction, `bulkInsert()`, `getType()`, `call()`.
- `openFile()` / `openAssetFile()` + per-URI grants for attachments.
- Work profile / multi-user; exclude the DB from backup.

---

## 5. Integration with the existing sync engine

The seams already exist and are clean.

| Layer | What's needed |
|---|---|
| `synctools/storage/davtasks/` | `DavTaskList`, `DavRecurringTaskList`, `DavTaskListProvider`, batch ops — mirrors `storage/tasks/` (~1150 LOC today) |
| `synctools/mapping/davtasks/` | builder + handler pairs. DMFS has ~32/25; the new set is larger (attendees, attachments, contact, resources, request-status, full alarms) — ~40/35 |
| `core/resource/` | `LocalDavTaskListStore : LocalDataStore<LocalDavTaskList>`, `LocalDavTaskList : LocalCollection`, `LocalDavTask : LocalResource`. **No changes to `TaskSyncer` / `TasksSyncManager` logic** — they already talk to these interfaces |
| `core/sync/` | `TasksAppManager.getDataStore()` gains a branch; `SyncDataType.TASKS.possibleAuthorities()` gains the new authority |
| manifest / res | one `sync-adapter` XML + one `SyncAdapterService`, mirroring `sync_opentasks.xml`. **Simpler than existing ones**: authority always present, no version check, no `setAccountVisibility` (`TaskSyncer.kt:64-71`), no `TasksAppWatcher` |

Eventually deleted on this path: `ProviderTooOldException`, `notifyProviderTooOld`,
the min-version table in `TaskProvider.ProviderName`.

---

## 6. Phasing

| Phase | Work | Est. |
|---|---|---|
| **0. Contract agreement** | Contract class + spec doc with the §3 traceability matrix filled in and RFC section numbers verified. Circulate to rfc2822 and the OpenTasks / tasks.org / jtx authors. **No provider code before sign-off.** | 2–3 w |
| **1. Provider core** | Room schema, URI matcher, CRUD, sync semantics, batch/bulk, notifications, strict query builder, permissions. Instrumented tests against the real provider (project convention — no mocking). | 6–8 w |
| **2. Sync integration** | synctools storage + mapping, `LocalDataStore` impl, sync adapter, UI/settings/debug-info. First end-to-end sync. | 4–6 w |
| **3. Instances & recurrence** | Expansion engine, RECURRENCE-ID override support (#2357), instance maintenance on write, DST/all-day correctness. | 4–6 w |
| **4. Attachments** | Blob storage, `openFile`, URI grants. (RFC 8607 managed attachments out of scope v1.) | 2–3 w |
| **5. Client library + docs + sample frontend** | Without this, nobody adopts it. | 2–3 w |
| **6. Migration** | Opt-in import from OpenTasks / tasks.org; dual-run period. | 3–4 w |

**~6 months** focused single-developer work to something shippable — then a
permanently frozen public API to maintain.

Phases 0–2 are the minimum viable slice worth proposing. Phase 3 will overrun;
recurrence expansion is where CalendarProvider and OpenTasks both accumulated their
long-tail bugs.

---

## 7. Risks

1. **Ecosystem, not code.** ContactsContract works because it is in AOSP. This
   provider asks frontend authors to depend on DAVx⁵ being installed, for which they
   have no incentive. jtx Board is the one working precedent — and both sides are
   bitfire-adjacent. *Mitigation: land your own app as the first frontend, and get one
   external author committed before Phase 1.*
2. **N+1 stores, not 1.** OpenTasks / tasks.org / jtx paths all stay for years. The
   "fewer integrations" benefit does not arrive on any near horizon. Raise this
   yourself — it is the obvious objection.
3. **Frozen API.** Every column is forever. This is why Phase 0 is not optional, and
   why §0's no-unattributed-columns rule matters.
4. **Timing.** rfc2822 mentioned substantial internal architectural changes underway.
   The `AccountId` refactoring is 8 PRs deep and `LocalDataStore` looks settled — but
   confirm whether the *tasks* layer specifically is in flight, or Phase 2 collides.

---

## 8. Open questions for the discussion thread

1. Is a DAVx⁵-hosted tasks provider a direction bitfire wants to own long-term, or
   would a merged contribution become an unowned maintenance burden?
2. Fixed authority vs. intent discovery — must OSE / Managed / Select coexist?
3. Should the contract be vendor-neutral (`at.bitfire.tasks.*`) so a non-DAVx⁵ app
   could implement it, or explicitly DAVx⁵-owned?
4. Is jtx Board in scope eventually, or permanently a separate path?
5. Acceptable first deliverable — the full thing, or Phases 0–2 behind a flag?
