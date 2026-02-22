#!/bin/sh
# Licensed to the Apache Software Foundation (ASF)
# Maven Wrapper Script for Unix/Linux/macOS

set -e

# Determine MAVEN_HOME via wrapper
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(dirname "$0")}"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPS="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Read distributionUrl from properties
DISTRIBUTION_URL=$(grep "distributionUrl" "$WRAPPER_PROPS" | cut -d'=' -f2)

# Resolve local Maven install directory
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
DISTRIBUTION_FILE=$(basename "$DISTRIBUTION_URL")
DISTRIBUTION_NAME="${DISTRIBUTION_FILE%-bin.zip}"
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$DISTRIBUTION_NAME"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Apache Maven from $DISTRIBUTION_URL ..."
    mkdir -p "$MAVEN_HOME"
    TEMP_DIR=$(mktemp -d)
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$TEMP_DIR/$DISTRIBUTION_FILE" "$DISTRIBUTION_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$TEMP_DIR/$DISTRIBUTION_FILE" "$DISTRIBUTION_URL"
    else
        echo "ERROR: curl or wget is required to download Maven." >&2
        exit 1
    fi
    unzip -q "$TEMP_DIR/$DISTRIBUTION_FILE" -d "$MAVEN_HOME"
    rm -rf "$TEMP_DIR"
fi

# Find the actual maven home (unzipped subdir)
MAVEN_EXEC=$(find "$MAVEN_HOME" -name "mvn" -type f | head -1)
if [ -z "$MAVEN_EXEC" ]; then
    echo "ERROR: Could not find mvn executable in $MAVEN_HOME" >&2
    exit 1
fi

exec "$MAVEN_EXEC" "$@"
