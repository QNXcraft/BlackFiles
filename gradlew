#!/usr/bin/env sh

set -e

GRADLE_VERSION="2.14.1"
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
GRADLE_USER_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
CACHE_DIR="${GRADLE_USER_HOME_DIR}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
ZIP_PATH="${CACHE_DIR}/${DIST_NAME}"
INSTALL_DIR="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${INSTALL_DIR}/bin/gradle"

mkdir -p "${CACHE_DIR}"

if [ ! -f "${ZIP_PATH}" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    curl -fL -o "${ZIP_PATH}" "${DIST_URL}"
fi

if [ ! -x "${GRADLE_BIN}" ]; then
    echo "Extracting Gradle ${GRADLE_VERSION}..."
    unzip -q -o "${ZIP_PATH}" -d "${CACHE_DIR}"
fi

exec "${GRADLE_BIN}" "$@"