#!/bin/bash
# Decides whether this build should become a release, before anyone is asked to approve one.
#
#   release-check.sh <app version> <newest CHANGELOG version> <newest release tag or ""> <build number>
#
# The newest release is the one with the highest build number, live or pre-release
# (see newest-release.sh), so a version or build number is never used twice.
#
# Prints "publish=true" or "publish=false" (for $GITHUB_OUTPUT) and explains why on stderr.
# Exits 1 when something is wrong that a person has to fix:
#   - the version went backwards (lower than the newest release),
#   - the version went up but CHANGELOG.md's newest entry is for another version,
#   - the build number isn't higher than the newest release's (phones would never be offered it).
# The same version as the newest release isn't an error: the code changed without a new version
# (a test, a workflow or a fix waiting for the next release), so nothing is published.
set -u
VERSION=$1; CHANGELOG=$2; TAG=$3; BUILD=$4

say() { echo "$*" >&2; }
fail() { echo "::error::$*" >&2; echo "publish=false"; exit 1; }

[[ "$VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]] || fail "gradle.properties has no usable obdVersion ('$VERSION')."

if [ -z "$TAG" ]; then
  [ "$CHANGELOG" = "$VERSION" ] || fail "CHANGELOG.md's newest entry is '$CHANGELOG', but the app version is $VERSION. Add a '## $VERSION' entry at the top."
  say "No release yet: $VERSION will be the first."
  echo "publish=true"; exit 0
fi

[[ "$TAG" =~ ^v([0-9]+(\.[0-9]+)*)-build([0-9]+)$ ]] || fail "The newest release's tag '$TAG' isn't in the vX.Y.Z-buildN form."
LATEST=${BASH_REMATCH[1]}; LATEST_BUILD=${BASH_REMATCH[3]}

if [ "$VERSION" = "$LATEST" ]; then
  echo "::notice title=No release::$VERSION is already published as $TAG, so this build isn't released. To release, raise obdVersion in gradle.properties and add a CHANGELOG.md entry. (If $TAG is a pre-release that failed its install test, see docs/RELEASING.md.)" >&2
  echo "publish=false"; exit 0
fi

# sort -V orders version numbers part by part (2.5.10 after 2.5.9).
HIGHER=$(printf '%s\n%s\n' "$VERSION" "$LATEST" | sort -V | tail -1)
[ "$HIGHER" = "$VERSION" ] || fail "The app version $VERSION is lower than the newest release $LATEST ($TAG). Versions must only go up."
[ "$CHANGELOG" = "$VERSION" ] || fail "CHANGELOG.md's newest entry is '$CHANGELOG', but the app version is $VERSION. Add a '## $VERSION' entry at the top."
[ "$BUILD" -gt "$LATEST_BUILD" ] 2>/dev/null || fail "Build number $BUILD isn't higher than the newest release's ($LATEST_BUILD), so phones would never be offered it."

say "$VERSION is new (newest release $LATEST, build $LATEST_BUILD): this build can be released as v$VERSION-build$BUILD."
echo "publish=true"
