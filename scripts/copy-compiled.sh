#!/bin/sh

# SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
#
# SPDX-License-Identifier: GPL-3.0-only

SOURCE_DIR=~/tmp/davx5
BASE_DIR=`dirname $0`/../app
MAPPING_DIR=$BASE_DIR/build/outputs/mapping
TARGET_DIR=$BASE_DIR/target

rsync -arvt $SOURCE_DIR/ $TARGET_DIR/
rsync -arvt $MAPPING_DIR/ $TARGET_DIR/latest-mapping/
