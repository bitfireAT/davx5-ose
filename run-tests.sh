#!/bin/sh

./gradlew -i --continue check mergeAndroidReports

echo
echo View lint report:
echo -n file://
realpath app/build/outputs/lint-results-gplayDebug.html

echo
echo View local unit test reports:
echo -n file://
realpath app/build/reports/tests/testStandardReleaseUnitTest/standardRelease/index.html

echo
echo "View merged Android test reports (debug):"
echo -n file://
realpath build/reports/androidTests/index.html
