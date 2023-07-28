#!/usr/bin/ruby

# SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
#
# SPDX-License-Identifier: GPL-3.0-only

File.open("contacts.vcf", "w") do |f|
	for i in 1..600 do
		f.puts "BEGIN:VCARD"
		f.puts "VERSION:3.0"
		f.puts "FN:Kontakt Nr. #{i}"
		f.puts "N:Kontakt Nr. #{i}"
		f.puts "EMAIL:#{i}@google-god.com"
		f.puts "PHONE:#{i}#{i}#{i}"
		f.puts "END:VCARD"
	end
end
