#!/bin/bash

declare -A android
android=([ca]=ca [cs]=cs [de]=de [es]=es [fr]=fr [hu]=hu [nl]=nl [pl]=pl [pt]=pt [ru]=ru [sr]=sr [uk]=uk [zh_CN]=zh-rcn)

for lang in ${!android[@]}
do
	target=../app/src/main/res/values-${android[$lang]}
	mkdir -p $target
	curl -n "https://www.transifex.com/api/2/project/davdroid/resource/davdroid/translation/$lang?file" >$target/strings.xml
done
