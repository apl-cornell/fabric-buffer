#!/bin/bash
#
# Fabric buffer CLI build script
#

# Create the compiler using gradle, create xic binary
./gradlew --no-daemon customFatJar
cat make_jar_executable.sh build/libs/all-in-one-jar-1.0-SNAPSHOT.jar > fbuffer && chmod +x fbuffer