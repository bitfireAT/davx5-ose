#!/bin/sh

# SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
#
# SPDX-License-Identifier: GPL-3.0-only

cd ~/tmp
adb pull /data/data/com.android.providers.contacts/databases/contacts2.db
adb pull /data/data/com.android.providers.calendar/databases/calendar.db
adb pull /data/data/org.dmfs.tasks/databases/tasks.db
