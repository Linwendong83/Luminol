#!/bin/sh
set -e
echo "Setting up projects"
./gradlew --refresh-dependencies applyAllPatches