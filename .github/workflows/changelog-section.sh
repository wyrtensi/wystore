#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version, so a release says what actually changed.
#
# Usage: changelog-section.sh 0.1.17 [CHANGELOG.md]
#
# Fails when the version has no section. That is deliberate: it stops a release that was tagged
# before anyone wrote down what is in it.
set -euo pipefail

version="${1:?usage: changelog-section.sh <version> [file]}"
file="${2:-CHANGELOG.md}"

section=$(
  awk -v version="$version" '
    # Section headings look like "## [0.1.17] — 2026-09-07".
    $0 ~ "^## \\[" version "\\]" { inside = 1; next }
    inside && /^## \[/ { exit }
    inside { print }
  ' "$file"
)

# Trim the blank lines the heading and the next section leave behind.
section=$(printf '%s\n' "$section" | sed -e '/./,$!d' -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')

if [ -z "$section" ]; then
  echo "CHANGELOG.md has no section for $version." >&2
  exit 1
fi

printf '%s\n' "$section"
