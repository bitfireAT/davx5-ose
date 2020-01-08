#!/usr/bin/ruby

require 'json'

contributors = {}

transifex = JSON.parse(STDIN.read, :symbolize_names => true)
for t in transifex
  lang = t[:language_code]
  people = t[:translators]
  contributors[lang] = people.sort_by { |nick| nick.downcase }
end

puts contributors.sort.to_h.to_json
