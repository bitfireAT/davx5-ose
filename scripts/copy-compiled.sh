#!/bin/sh

SOURCE_DIR=~/tmp/davx5
BASE_DIR=`dirname $0`/../app
MAPPING_DIR=$BASE_DIR/build/outputs/mapping
TARGET_DIR=$BASE_DIR/target

rsync -arvt $SOURCE_DIR/ $TARGET_DIR/
rsync -arvt $MAPPING_DIR/ $TARGET_DIR/latest-mapping/
