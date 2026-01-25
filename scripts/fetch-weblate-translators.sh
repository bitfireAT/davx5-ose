#!/bin/bash

#
# Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
#

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DATE=$(date +'%Y-%m-%d')

curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" \
  "https://hosted.weblate.org/api/projects/davx5/credits/?start=2026-01-01&end=$DATE" \
  > "$SCRIPT_DIR/../app/src/main/assets/weblate-translators.json"
