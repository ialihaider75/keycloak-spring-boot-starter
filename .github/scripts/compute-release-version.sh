#!/usr/bin/env bash
# Computes the next release version for a push to master: reads the latest
# vX.Y.Z tag and the committed pom.xml version, and either honors a
# deliberate manual override (pom.xml bumped above the latest tag) or
# auto-bumps the tag's patch digit. Prints only the resolved version to
# stdout on success. See docs/superpowers/plans/2026-11-05-publish-to-maven-central.md
# Task 4 for the full contract.
set -euo pipefail

resolve_latest_tag() {
    if [[ -n "${OVERRIDE_LATEST_TAG:-}" ]]; then
        echo "$OVERRIDE_LATEST_TAG"
        return
    fi
    git describe --tags --match 'v*' --abbrev=0 2>/dev/null | sed 's/^v//' || echo "0.0.0"
}

resolve_pom_version() {
    if [[ -n "${OVERRIDE_POM_VERSION:-}" ]]; then
        echo "$OVERRIDE_POM_VERSION"
        return
    fi
    ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout
}

# True (exit 0) if version $1 is strictly greater than version $2, compared
# as a numeric major.minor.patch tuple - never as strings, so 1.10.0 > 1.9.0.
version_is_greater() {
    local a_major a_minor a_patch b_major b_minor b_patch
    IFS='.' read -r a_major a_minor a_patch <<< "$1"
    IFS='.' read -r b_major b_minor b_patch <<< "$2"

    if (( a_major > b_major )); then return 0; fi
    if (( a_major < b_major )); then return 1; fi
    if (( a_minor > b_minor )); then return 0; fi
    if (( a_minor < b_minor )); then return 1; fi
    if (( a_patch > b_patch )); then return 0; fi
    return 1
}

latest_tag=$(resolve_latest_tag)
pom_version=$(resolve_pom_version)

if [[ "$pom_version" == *-SNAPSHOT* ]]; then
    echo "error: pom.xml version '$pom_version' still contains -SNAPSHOT; refusing to compute a release version" >&2
    exit 1
fi

if version_is_greater "$pom_version" "$latest_tag"; then
    echo "$pom_version"
else
    IFS='.' read -r major minor patch <<< "$latest_tag"
    echo "$major.$minor.$((patch + 1))"
fi
