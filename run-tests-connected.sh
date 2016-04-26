#!/bin/bash
./gradlew -i testDebug && \
	./gradlew -i deviceCheck mergeAndroidReports --continue
