#!/bin/sh

TX_TOKEN=`awk '/password =/ { print $3; }' <$HOME/.transifexrc`

(cd .. && tx pull -a --use-git-timestamps)

curl -H "Authorization: Bearer $TX_TOKEN" 'https://rest.api.transifex.com/team_memberships?filter\[organization\]=o:bitfireAT&filter\[team\]=o:bitfireAT:t:davx5-team' \
  | ./rewrite-translators.rb >../app/src/main/assets/translators.json
