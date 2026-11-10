#!/bin/sh
# Prints the CHANGELOG.md section for one version, without its heading.
# Usage: scripts/changelog-section.sh 1.1.0
set -eu
awk -v v="$1" '
    /^## / { found = ($2 == v); next }
    found  { print }
' "$(dirname "$0")/../CHANGELOG.md"
