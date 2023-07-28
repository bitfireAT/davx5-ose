#!/usr/bin/ruby

# SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
#
# SPDX-License-Identifier: GPL-3.0-only

require 'json'

contributors = {}

transifex = JSON.parse(STDIN.read, :symbolize_names => true)
for t in transifex[:data]
  raise unless t[:type] == 'team_memberships'
  next unless t[:attributes][:role] == 'translator'
  rel = t[:relationships]
  lang = rel[:language][:data][:id].delete_prefix('l:')
  user = rel[:user][:data][:id].delete_prefix('u:')

  contributors[lang] = [] if contributors[lang].nil?
  contributors[lang] << user
end

contributors.transform_values! { |u| u.sort }

puts contributors.sort.to_h.to_json
