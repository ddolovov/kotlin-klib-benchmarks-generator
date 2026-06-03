#!/bin/sh

set -e

CURRENT_DIR=`pwd`
ROOT_DIR=`realpath $CURRENT_DIR/../..`

OUTPUT_DIR=$CURRENT_DIR/generated-projects
ARG_FILE=$(mktemp)

cat > $ARG_FILE <<EOF
--output-dir $OUTPUT_DIR
--number-of-projects 100
--cinterop-projects 50
--declarations-per-project 5
--dependencies-per-project 3
--unique-packages 20
--kotlin-version 2.4.0
EOF

echo "Generator CLI arg file:\n$ARG_FILE\n"
echo "Generator CLI args:\n$(cat $ARG_FILE)"

echo
echo "===== Removing previously generated projects in $OUTPUT_DIR"
rm -rf $OUTPUT_DIR
mkdir $OUTPUT_DIR
echo "===== Removing previously generated projects in $OUTPUT_DIR: Done."

echo
echo "===== Building the generator..."
$ROOT_DIR/gradlew -p $ROOT_DIR assemble
echo "===== Building the generator: Done."

echo
echo "===== Generating projects..."
$ROOT_DIR/gradlew -p $ROOT_DIR run -P appArgs=$ARG_FILE
echo "===== Generating projects: Done."

echo
echo "To build the generated projects run the following command:"
echo "cd $OUTPUT_DIR && ./build.sh"
