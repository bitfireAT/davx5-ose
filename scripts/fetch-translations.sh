#!/bin/sh

export TX_TOKEN=`awk '/token *=/ { print $3; }' <$HOME/.transifexrc`

(cd .. && tx pull -a -f --use-git-timestamps)
if find ../app/src -type d -name 'values-*_*' -exec false '{}' +
then
  echo "No values-XX_RR directory found, good"
else
  echo "Found values-XX_RR directory, update .tx/config mappings to values-XX-rRR!"
  exit 1
fi

curl -H "Authorization: Bearer $TX_TOKEN" 'https://rest.api.transifex.com/team_memberships?filter\[organization\]=o:bitfireAT&filter\[team\]=o:bitfireAT:t:davx5-team' \
  | ./rewrite-translators.rb >../app/src/main/assets/translators.json
