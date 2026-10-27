# `:core` — Project Structure

Android library. The sync engine, database layer, and Jetpack Compose UI for DAVx⁵. It orchestrates CalDAV/CardDAV synchronization via dav4jvm and delegates content-provider access and format mapping to `:synctools`.

## Dependency injection — Hilt

**Hilt is the DI framework for this module.** Follow these conventions:

- Use `@Inject` constructors everywhere; avoid manual instantiation.
- Add new Hilt bindings and qualifiers in `di/` (same style as the existing ones).
- ViewModels use `@HiltViewModel`.
- WorkManager workers integrate via the Hilt worker factory — do not construct workers manually.
- Android system services that have no `@Inject` constructor (e.g. `AccountManager`) are bound via `@Provides` in a
  `di/` module (e.g. `AndroidServicesModule`) — never call their static `.get(context)`/similar factory method directly
  in a Hilt-managed class.
- For such bindings, pick the injection shape by how often it's actually *called at runtime* (not how many call sites
  reference it): inject the type directly when it's on the hot path; inject `dagger.Lazy<T>` when a given instance has a
  good chance of never needing it; inject `javax.inject.Provider<T>` when it's only invoked rarely.

## Patterns

**Repository pattern** — DAOs (in `db/`) are always wrapped by a repository in `repository/`. UI code and sync managers talk to repositories, never to DAOs directly.

**Blocking/suspending boundary** — `resource/` (the `Local*` classes wrapping `:synctools`) and `repository/` must expose non-blocking, suspending APIs. Callers must not need to wrap calls into these packages in `withContext` themselves; if the underlying `:synctools` call is blocking, the `resource/`/`repository/` method wraps it in `withContext(ioDispatcher)` internally (narrowly, around the actual blocking call).

**DAO/repository naming convention** — applies to both DAO methods (`db/`) and their repository wrappers (
`repository/`):

- Suspending functions have **no suffix** (e.g. `get`, `insert`) — never `...Async`.
- Non-suspending (blocking) functions have a **`Blocking`** suffix (e.g. `getBlocking`, `insertBlocking`).
- Functions returning `Flow` have a **`Flow`** suffix (e.g. `getAllFlow`).
- Functions returning `PagingSource` use a **`page`/`pageXxx`** prefix instead of a suffix (e.g.
  `pageByServiceAndType`), mirroring how `Flow`-returning functions are marked.

**ViewModel pattern** — Each Compose screen has a `@HiltViewModel` in `ui/`. Keep business logic out of Composables; Composables observe state from the ViewModel.

**Startup actions** — App-initialization hooks implement the `StartupAction` interface and are registered via set-based
Hilt injection. Do not add init logic directly to `CoreApp`.

**Background sync** — Sync runs in WorkManager workers (`sync/worker/`). The Hilt worker factory wires DI into workers.

## Package map

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

## Key dependencies

- `:synctools` — content-provider access and format mapping
- `cert4android` — custom certificate management
- `dav4jvm` — WebDAV/CalDAV/CardDAV client
