#!/bin/sh
cd ~/tmp
adb pull /data/data/com.android.providers.contacts/databases/contacts2.db
adb pull /data/data/com.android.providers.calendar/databases/calendar.db
