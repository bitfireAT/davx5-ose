# Automated PR Review Guide

## Review focus

- **Functional bugs & logic errors** – incorrect behavior, missing edge cases, null/type safety issues
- **Architectural concerns** – design patterns, maintainability, API contracts
- **Security issues** – unsafe patterns, injection risks, credential exposure
- **Code quality** – clarity, testability, redundancy

## Conventional Comments

Format all review feedback using [Conventional Comments](https://conventionalcomments.org/):

```
**<label> [decorations]:** <subject>

[optional discussion]
```

The subject must be a single short sentence. Put any additional context or explanation in the discussion block below.

### Labels

- `issue` — a specific problem that must be addressed
- `suggestion` — a proposed improvement; be explicit about what to change and why it's better
- `todo` — a small, necessary change (trivial but required)
- `typo` — a misspelling that needs correcting
- `quibble` — a trivial style or preference request; non-blocking by nature. Always use `quibble` instead of `nitpick`.
- `polish` — an improvement to quality where nothing is technically wrong
- `note` — always non-blocking; highlights something the reader should be aware of

### Decorations

Use decorations only when they add real value:

- `(blocking)` — must be resolved before merging; reserve for comments with significant impact
- `(non-blocking)` — should not block merging; use rarely, only when the distinction genuinely matters
- `(if-minor)` — resolve only if the change turns out to be small/trivial

### Examples

```
**issue (blocking):** `syncResult` can be null here if the worker is cancelled mid-sync.

The `SyncManager` checks for cancellation after `syncResult` is assigned, but the null path
is never handled. Add a null check or restructure the cancellation guard.
```

```
**suggestion:** Extract the retry logic into a separate function for testability.
```

```
**quibble:** `tmp` → `tempFile` for clarity.
```
