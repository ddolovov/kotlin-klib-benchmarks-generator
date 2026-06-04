#!/bin/sh

set -e

CURRENT_DIR=`pwd`
ROOT_DIR=`realpath $CURRENT_DIR/../..`

OUTPUT_DIR=$CURRENT_DIR/generated-projects
ARG_FILE=$(mktemp)

cat > $ARG_FILE <<EOF
--output-dir $OUTPUT_DIR
--generation-mode separate-gradle-projects
--number-of-libraries 5000
--cinterop-libraries 500
--declarations-per-library 5
--dependencies-per-library 3
--unique-packages 1000
--kotlin-version 2.4.0
EOF

echo "Generator CLI arg file:\n$ARG_FILE\n"
echo "Generator CLI args:\n$(cat $ARG_FILE)"

echo
echo "===== Removing previously generated projects in $OUTPUT_DIR"
rm -rfv $OUTPUT_DIR
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
echo "To build and publish to Maven local the generated libraries run the following command:"
echo "cd $OUTPUT_DIR && ./build-libs.sh"
echo
echo "After the libraries are published, you can build the application by running:"
echo "cd $OUTPUT_DIR && ./build-app.sh"
