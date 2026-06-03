#!/bin/sh

set -e

CURRENT_DIR=`pwd`
ROOT_DIR=`realpath $CURRENT_DIR/../..`

echo "===== Building the generator..."
$ROOT_DIR/gradlew -p $ROOT_DIR assemble
echo "===== Building the generator: Done."

echo
echo "===== Running the generator CLI..."
$ROOT_DIR/gradlew -p $ROOT_DIR run
echo "===== Running the generator CLI: Done."
