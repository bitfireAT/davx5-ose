#!/bin/bash

declare -A android
android=([ar_SA]=ar [ca]=ca [cs]=cs [da]=da [de]=de [el]=el [es]=es [fa]=fa [fr]=fr [gl]=gl [hu]=hu [it]=it [ja]=ja [nl]=nl [nb_NO]=nb-rNO [pl]=pl [pt]=pt [pt_BR]=pt-rBR [ru]=ru [sk_SK]=sk [sl_SI]=sl-rSI [sr]=sr [tr_TR]=tr-rTR [uk]=uk [zh_CN]=zh-rCN [zh_TW]=zh-rTW)

BASE_DIR=`realpath -L $0 | xargs dirname`/..


function fetch_txt {
	URL=$1
	LANG=$2
	FILE=$3

	TRANSLATIONS=`mktemp`
	curl -sn $1 >$TRANSLATIONS
	diff --ignore-trailing-space -aq $TRANSLATIONS $BASE_DIR/fastlane/metadata/android/en-US/$FILE
	if [[ $? -ne 0 ]]; then
		# translations are not the same as en-us
		mkdir -p $BASE_DIR/fastlane/metadata/android/$LANG
		mv $TRANSLATIONS $BASE_DIR/fastlane/metadata/android/$LANG/$FILE
	fi
	rm -f $TRANSLATIONS
}


for lang in ${!android[@]}
do
	echo Fetching translations for $lang
	target_app=$BASE_DIR/app/src/main/res/values-${android[$lang]}
	target_cert4android=$BASE_DIR/cert4android/src/main/res/values-${android[$lang]}

	echo -e '\tapp strings'
	mkdir -p $target_app
	curl -sn "https://www.transifex.com/api/2/project/davx5/resource/app/translation/$lang?file" |
		sed 's/\.\.\./…/g' > $target_app/strings.xml

	echo -e '\tcert4android'
	#mkdir -p $target_cert4android
	#curl -sn "https://www.transifex.com/api/2/project/davx5/resource/cert4android/translation/$lang?file" >$target_cert4android/strings.xml

	echo -e '\tmetadata'
	fetch_txt "https://www.transifex.com/api/2/project/davx5/resource/metadata-full-description/translation/$lang?file" ${android[$lang]} full_description.txt
	fetch_txt "https://www.transifex.com/api/2/project/davx5/resource/metadata-short-description/translation/$lang?file" ${android[$lang]} short_description.txt
done
