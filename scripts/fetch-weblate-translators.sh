#!/bin/bash

#
# Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
#

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
DATE=$(date +'%Y-%m-%d')

# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X POST -H 'Content-Type: application/json' -d '{"project":"davx5","kind":"credits","start":"2026-01-01","end":"2026-07-20"}' https://hosted.weblate.org/api/reports/
# {"detail":"Report generation scheduled.","task_url":"/api/tasks/58cda3c6-6a1d-43da-baa6-24fd6f471aab/"}
TASK_URL=$(curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X POST -H 'Content-Type: application/json' -d '{"project":"davx5","kind":"credits","start":"2026-01-01","end":"'"$DATE"'"}' https://hosted.weblate.org/api/reports/ | jq -r '.task_url')

# If TASK_URL is null, it means the request failed. We should exit with an error message.
if [[ "$TASK_URL" == "null" ]]; then
  echo "Error: Failed to schedule report generation. Please check your WEBLATE_API_TOKEN and try again."
  exit 1
fi

# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" https://hosted.weblate.org/api/tasks/58cda3c6-6a1d-43da-baa6-24fd6f471aab/
# {"completed":true,"progress":100,"result":{"message":"Report generated.","url":"/api/reports/21/"},"log":""}
# We must take into account that tasks may take some time, and result may not be available immediately. We will poll the task until it is completed.
# Timeout at 60 seconds (12 attempts with 5 seconds interval)
COUNT=0
while true; do
  TASK_STATUS=$(curl -fsS -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" "https://hosted.weblate.org$TASK_URL") || { echo "Error: Failed to fetch task status."; exit 1; }
  TASK_COMPLETED=$(echo "$TASK_STATUS" | jq -er '.completed') || { echo "Error: Failed to parse task status response."; exit 1; }
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

TASK_ID=$(echo "$TASK_STATUS" | jq -r '.result.url' | cut -d '/' -f 4)

# curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X GET https://hosted.weblate.org/api/reports/21/json/
# [JSON] -> data ==> weblate-translators.json
REPORT=$(curl -H 'Accept: application/json' -H "Authorization: Token $WEBLATE_API_TOKEN" -X GET "https://hosted.weblate.org/api/reports/$TASK_ID/json/")
echo "$REPORT" > "$SCRIPT_DIR/../core/src/main/assets/weblate-translators.json"
