#!/bin/bash
# Prints the tag of the newest release, live or not: the one with the highest build number,
# counting pre-releases (a release waiting for its install test, or one that failed it) but not drafts.
# Prints nothing when there's no release yet. Exits 1 if GitHub can't be asked.
#
# A new build is checked against this rather than the "latest" (live) release, so a version or
# build number can't be used twice, and a build waiting for its test can't be overtaken by an older one.
# Needs GH_TOKEN and GITHUB_REPOSITORY.
set -u
if ! OUT=$(gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
    --jq '[.[] | select(.draft | not) | {t: .tag_name, b: (try (.tag_name | capture("-build(?<b>[0-9]+)$").b | tonumber) catch -1)}] | max_by(.b) | .t // ""' 2> gh-error.txt); then
  cat gh-error.txt >&2
  echo "::error::Couldn't read the releases from GitHub." >&2
  exit 1
fi
echo "$OUT"
