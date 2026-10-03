#!/bin/sh
set -e
GRADLE_VERSION=9.8.0
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$GRADLE_HOME/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_DIR="$GRADLE_HOME/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$GRADLE_HOME"
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GRADLE_ZIP"
  unzip -q -o "$GRADLE_ZIP" -d "$GRADLE_HOME"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
