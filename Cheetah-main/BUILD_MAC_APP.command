#!/bin/bash
# Produces a native .app bundle for this Mac with an embedded Java runtime.
cd "$(dirname "$0")" || exit 1
if ! command -v jpackage >/dev/null 2>&1; then
    echo 'jpackage not found. Install a full JDK 17+.'
    read -r -p 'Press Return to close...' _
    exit 1
fi
if [ ! -f dist/CheetahLudo.jar ]; then
    echo 'Missing dist/CheetahLudo.jar.'
    read -r -p 'Press Return to close...' _
    exit 1
fi
mkdir -p release
if [ -e release/CheetahLudo.app ]; then
    echo 'release/CheetahLudo.app already exists. Move or remove it to rebuild.'
    read -r -p 'Press Return to close...' _
    exit 1
fi
jpackage --type app-image --name CheetahLudo --input dist --main-jar CheetahLudo.jar --dest release --java-options '-Dfile.encoding=UTF-8'
status=$?
if [ "$status" -eq 0 ]; then
    echo 'Created release/CheetahLudo.app. Test it locally before sharing.'
else
    echo "Packaging failed, exit code $status."
fi
read -r -p 'Press Return to close...' _
exit "$status"
