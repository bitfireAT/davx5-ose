#!/bin/bash

#
# Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
#

set -uo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DATE=$(date +'%Y-%m-%d')

# Performs an authenticated request against the Weblate API.
# On success, sets the HTTP_STATUS and HTTP_BODY globals to the response's status code and body.
weblate_request() {
  local method="$1" url="$2"
  shift 2
  local tmp_file curl_status
  tmp_file=$(mktemp)
  HTTP_STATUS=$(curl -sS -o "$tmp_file" -w '%{http_code}' \
    -H 'Accept: application/json' \
    -H "Authorization: Token $WEBLATE_API_TOKEN" \
    -X "$method" "$@" "$url")
  curl_status=$?
  HTTP_BODY=$(cat "$tmp_file")
  rm -f "$tmp_file"
  if [[ "$curl_status" -ne 0 ]]; then
    echo "Error: Request to $url failed (curl exit code $curl_status)."
    exit 1
  fi
}

# Checks that HTTP_STATUS is a 2xx response, exiting with the given error message otherwise.
check_http_status() {
  local message="$1"
  if [[ "$HTTP_STATUS" -lt 200 || "$HTTP_STATUS" -ge 300 ]]; then
    echo "Error: $message (HTTP $HTTP_STATUS)."
    echo "Response: $HTTP_BODY"
    exit 1
  fi
}

# 1. Schedule report generation.
# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X POST -H 'Content-Type: application/json' -d '{"project":"davx5","kind":"credits","start":"2026-01-01","end":"2026-07-20"}' https://hosted.weblate.org/api/reports/
# {"detail":"Report generation scheduled.","task_url":"/api/tasks/58cda3c6-6a1d-43da-baa6-24fd6f471aab/"}
weblate_request POST "https://hosted.weblate.org/api/reports/" \
  -H 'Content-Type: application/json' \
  -d '{"project":"davx5","kind":"credits","start":"2026-01-01","end":"'"$DATE"'"}'
check_http_status "Failed to schedule report generation. Please check your WEBLATE_API_TOKEN and try again"

TASK_URL=$(echo "$HTTP_BODY" | jq -er '.task_url') || {
  echo "Error: Unexpected response when scheduling report generation (missing task_url)."
  echo "Response: $HTTP_BODY"
  exit 1
}

# 2. Poll the task until it is completed.
# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" https://hosted.weblate.org/api/tasks/58cda3c6-6a1d-43da-baa6-24fd6f471aab/
# {"completed":true,"progress":100,"result":{"message":"Report generated.","url":"/api/reports/21/"},"log":""}
# We must take into account that tasks may take some time, and result may not be available immediately. We will poll the task until it is completed.
# Timeout at 60 seconds (12 attempts with 5 seconds interval)
COUNT=0
while true; do
  weblate_request GET "https://hosted.weblate.org$TASK_URL"
  check_http_status "Failed to fetch task status"

  TASK_COMPLETED=$(echo "$HTTP_BODY" | jq -er '.completed') || {
    echo "Error: Failed to parse task status response."
    echo "Response: $HTTP_BODY"
    exit 1
  }

  if [[ "$TASK_COMPLETED" == "true" ]]; then
    break
  fi

  COUNT=$((COUNT + 1))
  if [[ "$COUNT" -ge 12 ]]; then
    echo "Error: Task did not complete within the timeout period."
    exit 1
  fi
  echo "Task not completed yet, waiting for 5 seconds..."
  sleep 5
done

TASK_ID=$(echo "$HTTP_BODY" | jq -er '.result.url') || {
  echo "Error: Task completed but result URL is missing."
  echo "Response: $HTTP_BODY"
  exit 1
}
TASK_ID=$(echo "$TASK_ID" | cut -d '/' -f 4)

# 3. Fetch the generated report.
# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X GET https://hosted.weblate.org/api/reports/21/json/
# [JSON] -> data ==> weblate-translators.json
weblate_request GET "https://hosted.weblate.org/api/reports/$TASK_ID/json/"
check_http_status "Failed to fetch report"

if ! echo "$HTTP_BODY" | jq -e 'type == "array"' > /dev/null; then
  echo "Error: Report response is not a JSON array."
  echo "Response: $HTTP_BODY"
  exit 1
fi

printf '%s\n' "$HTTP_BODY" > "$SCRIPT_DIR/../core/src/main/assets/weblate-translators.json"
