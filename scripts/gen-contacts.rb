#!/usr/bin/ruby

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
