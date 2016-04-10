#!/bin/bash

declare -A android
android=([ca]=ca [cs]=cs [da]=da [de]=de [es]=es [fr]=fr [hu]=hu [it]=it [ja]=ja [nl]=nl [pl]=pl [pt]=pt [pt_BR]=pt-rBR [ru]=ru [sr]=sr [tr_TR]=tr-rTR [uk]=uk [zh_CN]=zh-rCN)

for lang in ${!android[@]}
do
	target=../app/src/main/res/values-${android[$lang]}
	mkdir -p $target
	curl -n "https://www.transifex.com/api/2/project/davdroid/resource/davdroid/translation/$lang?file" >$target/strings.xml
done
