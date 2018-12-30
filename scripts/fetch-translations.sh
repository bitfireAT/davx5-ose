#!/bin/bash

declare -A android
android=([ar_SA]=ar [ca]=ca [cs]=cs [da]=da [de]=de [es]=es [fa]=fa [fr]=fr [hu]=hu [it]=it [ja]=ja [nl]=nl [nb_NO]=nb-rNO [pl]=pl [pt]=pt [pt_BR]=pt-rBR [ru]=ru [sl_SI]=sl-rSI [sr]=sr [tr_TR]=tr-rTR [uk]=uk [zh_CN]=zh-rCN [zh_TW]=zh-rTW)

for lang in ${!android[@]}
do
	target_app=../app/src/main/res/values-${android[$lang]}
	target_cert4android=../cert4android/src/main/res/values-${android[$lang]}

	mkdir -p $target_app
	curl -n "https://www.transifex.com/api/2/project/davx5/resource/app/translation/$lang?file" |
		sed 's/\.\.\./…/g' > $target_app/strings.xml

	#mkdir -p $target_cert4android
	#curl -n "https://www.transifex.com/api/2/project/davx5/resource/cert4android/translation/$lang?file" >$target_cert4android/strings.xml
done
