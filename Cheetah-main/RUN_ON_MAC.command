#!/bin/bash
# Cross-platform Java JAR launcher for macOS (Intel and Apple Silicon).
cd "$(dirname "$0")" || exit 1
printf '\nCHEETAH LUDO - MAC DESKTOP GAME\n==============================\n'
if ! command -v java >/dev/null 2>&1; then
    echo 'Java was not found. Install JDK 17+ or run the project in Eclipse.'
    read -r -p 'Press Return to close...' _
    exit 1
fi
if [ ! -f dist/CheetahLudo.jar ]; then
    echo 'Game JAR is missing. Extract the entire ZIP before launching.'
    read -r -p 'Press Return to close...' _
    exit 1
fi
java -version 2>&1
printf '\nChecking resources...\n'
java -jar dist/CheetahLudo.jar --check || {
    echo 'Game check failed. Copy the errors above.'
    read -r -p 'Press Return to close...' _
    exit 1
}
printf '\nOpening Cheetah Ludo in a separate window...\n'
java -jar dist/CheetahLudo.jar
status=$?
if [ "$status" -ne 0 ]; then echo "Game failed, exit code: $status"; fi
read -r -p 'Press Return to close...' _
exit "$status"
