# DAVx⁵ OSE – Project Structure

DAVx⁵ is a CalDAV/CardDAV synchronization client for Android. The repository is a multi-module Gradle project.

This repository contains the open-source code of DAVx⁵. There's another closed-source repository that contans
some extension code for variants like Managed DAVx⁵ and DAVx⁵ Select, but the main code with all functionality
is shared and the core development always takes place in davx5-ose.

> **Maintenance note for agents:** Keep this file (and `core/AGENTS.md`, `synctools/AGENTS.md`) up to date. When making changes that affect the structure described here — such as adding/removing Gradle modules, renaming packages, changing the DI framework, or replacing major dependencies — update the relevant file(s) as part of the same change. Only reflect genuinely significant structural changes; don't update for routine additions like new classes or minor refactors.

## Committing and pull requests

- When committing, always add a `Co-Authored-By` line to identify yourself as AI agent.
- When creating or editing a PR:
  - Format the PR description as in `.github/pull_request_template.md` and never check the "This PR was mainly driven by a human" checkbox.
  - Before creating a PR, make sure that the PR is properly reviewed by a `code-review` skill and all issues are addressed.

## Gradle modules

Dependency direction: `:app-ose` uses `:core` uses `:synctools`

### `:app-ose` (`app-ose/`)

The Android application. Contains the product flavor `ose`, signing config, and the APK build. Has no business logic of its own — it wires together `:core` with app-level Hilt setup (`@HiltAndroidApp`) and provides the OSE-specific entry point.

### `:core` (`core/`)

Android library. The sync engine, database layer, and Jetpack Compose UI for DAVx⁵. See `core/AGENTS.md` for DI conventions, patterns, and package map.

### `:synctools` (`synctools/`)

Standalone Android library for bidirectional conversion between iCalendar/vCard data and Android content providers (Calendar, Contacts, Tasks, Jtx). Only consumed by `:core`. See `synctools/AGENTS.md` for its API, architecture, and conventions.

## Build infrastructure

### `build-logic/`

Gradle convention plugins (included via `includeBuild`, not a module listed in `settings.gradle.kts`). Defines the `davx5.common-buildconfig` plugin used by all three modules to share compile SDK, version info, and common build settings.
