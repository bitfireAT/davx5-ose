#!/bin/sh

SOURCE_DIR=~/tmp/davx5
TARGET_DIR=`dirname $0`/../app/target

rsync -arvt $SOURCE_DIR/ $TARGET_DIR/
