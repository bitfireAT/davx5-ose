<!-- Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details. -->

# Proposal: import new phone contacts into a CardDAV address book

**Status:** draft design for maintainer feedback. This document adds no working importer,
background job, permission request, or user interface. No runtime behavior has been tested.

Related discussion: [DAVx⁵ not present in WhatsApp add contact (#788)](https://github.com/bitfireAT/davx5-ose/discussions/788).
Feature proposal: [opt-in import of new device-local contacts into CardDAV (#2993)](https://github.com/bitfireAT/davx5-ose/issues/2993).

## Problem and scope

Some apps offer only a device-local or Google account destination when creating contacts.
Users of DAVx⁵ then have to re-enter the name and number in a contacts app to save them to
CardDAV. DAVx⁵ cannot control another app's account picker.

An optional importer could copy new contacts from a verified device-local source into one
selected writable CardDAV address book. Users would keep using the original app, including
official WhatsApp, and DAVx⁵ would handle the subsequent CardDAV upload.

This would apply to new contacts in the selected source regardless of which app created them.
It would not read contacts kept only in another app's private storage. Changing WhatsApp's
picker and identifying WhatsApp as the originating app are outside the proposal.

## User workflow

1. In a writable address book, enable **Import new phone contacts**. Show the source and
   destination explicitly, together with the fact that new contacts will be uploaded to that
   address book's server. Allow only one automatic destination for a given source.
2. Establish a baseline before enabling automation. Existing phone contacts are not uploaded
   implicitly; a separate **Review phone contacts** action allows importing selected records.
3. After another app saves a complete contact to the selected source, copy its supported
   fields into the destination and request normal contact synchronization.
4. Show import errors and distinguish a local copy awaiting synchronization from a successful
   server upload. Offline imports remain pending.
5. Keep the original source contact. Record completed imports so scanning again or retrying
   interrupted work does not create another destination record for the same import.

The proposed first version imports each source contact once. Later edits and deletions of
the phone copy are not mirrored. The UI must explain this and the possibility of two visible
copies; Android's contact aggregation must not be presented as guaranteed deduplication.
Possible matches already in the destination should require review, not an automatic merge
based solely on a shared phone number. Two-way synchronization and automatic source deletion
would need a separate design.

Disabling the feature stops future imports; it does not delete copies already created.
Changing the destination requires an explicit selection and a new baseline, with an explanation
that previously imported contacts are not moved. Re-enabling must not silently import contacts
created while automation was disabled.

## Implementation approach to review

The existing upload path can be reused. New work would consist of source discovery, reading
and copying contacts, persistent import state, background reconciliation, and settings/review UI.

| Responsibility | Existing integration point or proposed addition |
| --- | --- |
| Resolve the selected address book and check access | `LocalAddressBookStore` and collection/account repositories |
| Read source records and create destination records | New importer using the contacts provider and `synctools` mapping APIs |
| Track baselines, pending operations, and completed imports | New persistent state behind a repository in `core` |
| Upload new destination contacts | Existing `ContactsSyncManager` and `SyncWorkerManager` |
| Expose controls and review | Address-book settings and a contact review UI, subject to maintainer feedback |

Source inspection at `ff28aa6d2532570e5deeb0a45dc887765d1fa14b` identified these constraints:

- **Source discovery:** verify the actual raw-contact account used by a Phone destination.
  On Android 16+, an insert without an account can use the system default account. Do not
  assume the label Phone means a null-account record, or import from another cloud account
  implicitly. See [DefaultAccount](https://developer.android.com/reference/android/provider/ContactsContract.RawContacts.DefaultAccount).
- **Destination state:** `LocalAddressBook.addContact()` delegates to `AndroidContact.add()`,
  whose builder sets `DIRTY = 0`. A local import must create an uploadable contact instead,
  with appropriate UID, no remote filename/ETag, and recoverable pending-upload state.
- **Copy semantics:** create a new raw contact with the destination account. Android documents
  account name/type/data set as write-once. Do not relabel the source record in place. See
  [RawContacts](https://developer.android.com/reference/android/provider/ContactsContract.RawContacts).
- **Data mapping:** preserve supported structured names, all phone numbers and labels, emails,
  addresses, organization, notes, and photos. Do not copy source account metadata, sync IDs,
  or group row IDs into the destination. Make unsupported data visible in the review flow.
- **Retries and interruptions:** persist an import operation before writing. Resolve the gap
  between contacts-provider writes and recording completion, including interruption during
  photo copying. A destination marker or an equivalent recovery mechanism is needed; a row
  ID alone is insufficient after provider resets or restored backups.
- **Concurrent changes:** revalidate source versions and destination writability before copying.
  Defer incomplete or changing records. Handle revoked permissions, removed accounts, disabled
  synchronization, and read-only policies without deleting or altering source contact data.
- **Scheduling:** a process-local content observer cannot provide reliable background imports
  by itself. Use persistent work and reconciliation. Android scheduling restrictions mean the
  feature should not promise immediate imports while DAVx⁵ is stopped or restricted.

Follow the existing Hilt, repository, coroutine, and WorkManager conventions. The exact state
schema, recovery mechanism, and scheduling integration remain implementation decisions to
agree with maintainers; this document does not establish a proven algorithm.

## Android permission changes

The [Android contact-access announcement](https://developer.android.com/blog/posts/reliable-way-to-add-and-edit-contacts-android)
introduces `MANAGE_CONTACTS` and changes direct-write access for apps targeting the upcoming
release. Background cloud-sync apps are a listed use case. Any implementation must follow
DAVx⁵'s permission migration and handle access being unavailable. This is not a guarantee
that another app will expose the desired account picker, nor that permission is already granted.

## Acceptance tests before implementation is considered usable

Use synthetic contacts and real contacts-provider instrumentation tests, following
`synctools/AGENTS.md`. Validate the upload with a test CardDAV server as well.

- A selected source contact produces one destination contact and, after successful sync, one
  server resource. Failure or offline operation preserves the source and reports pending/error.
- Repeated scans, concurrent workers, process death at each write boundary, photo-write failures,
  provider resets, and backup restoration do not silently duplicate or lose contacts.
- Enabling, disabling, re-enabling, and changing the destination respect the baseline rules.
- Google, other DAVx⁵ accounts, work profiles, and any unselected source are excluded.
- Multiple numbers, custom labels, Unicode names, notes, supported fields, and photos survive
  the supported mapping. Unsupported fields are disclosed before a reviewed import.
- Incomplete records, concurrent edits, duplicate candidates, missing permissions, read-only
  targets, removed accounts, and disabled sync follow the stated behavior.
- Later edits/deletions respect the documented one-time-copy policy.
- Verify the real source used by official WhatsApp on affected devices, including Android's
  default-account behavior, and verify background execution across supported Android versions.

## Questions for maintainers

1. Does a general device-local contact importer belong in DAVx⁵'s scope?
2. Should the first implementation expose reviewed imports only, or include opt-in automation?
3. Where should the controls live, and how should they fit the account/permission roadmap?

Implementation and runtime validation would follow agreement on these points. This draft was
prepared with AI assistance.
