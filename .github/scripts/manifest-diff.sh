#!/bin/bash
# Compares the permissions and app components of a built APK with the live release's APK, and
# puts the differences on the run's summary page. Library updates can add either through manifest
# merging, so a reviewer sees it before the build is released. It only reports: it never fails the build.
#
# Usage: manifest-diff.sh <apk>. Needs GH_TOKEN, GITHUB_REPOSITORY and the Android SDK's aapt2.
set -u
APK=$1
AAPT=$(find "$ANDROID_HOME/build-tools" -name aapt2 | sort | tail -1)
WORK=$(mktemp -d)

# Permissions; the application's backup, cleartext and network security settings; and each activity,
# service, receiver and provider with its exported flag, permission and authorities.
describe() {
  "$AAPT" dump permissions "$1" | grep -E "^(uses-permission|permission)" | sed 's/ *$//' | sort -u
  "$AAPT" dump xmltree --file AndroidManifest.xml "$1" | awk '
    function flush() { if (tag != "") print tag; tag = "" }
    { match($0, /^ */); ind = RLENGTH }
    /^ *E: / {
      if (tag != "" && ind <= tind) flush()
      if ($2 ~ /^(application|activity|activity-alias|service|receiver|provider)$/) { flush(); tag = $2; tind = ind }
      next
    }
    tag != "" && ind == tind + 2 && /^ *A: [^(]*android:(name|exported|permission|authorities|allowBackup|usesCleartextTraffic|networkSecurityConfig|debuggable|fullBackupContent|dataExtractionRules)\(0x/ {
      attr = $0; sub(/^ *A: [^(]*android:/, "", attr); sub(/\(0x.*/, "", attr)
      val = $0; sub(/^[^=]*=/, "", val); sub(/ \(Raw: .*/, "", val)
      tag = tag " " attr "=" val
    }
    END { flush() }' | sort -u
}

TAG=$(gh api "repos/$GITHUB_REPOSITORY/releases/latest" --jq .tag_name 2>/dev/null) || TAG=""
if [ -z "$TAG" ] || ! gh release download "$TAG" --repo "$GITHUB_REPOSITORY" --pattern OBD2Reader.apk --dir "$WORK" 2>/dev/null; then
  echo "No live release APK to compare with."; exit 0
fi

describe "$WORK/OBD2Reader.apk" > "$WORK/live.txt"
describe "$APK" > "$WORK/new.txt"
{
  echo "### Permissions and components compared with $TAG"
  echo
  if diff -q "$WORK/live.txt" "$WORK/new.txt" > /dev/null; then
    echo "No change."
  else
    echo "Lines starting with + are new in this build, - are gone."
    echo
    echo '```diff'
    diff -U0 "$WORK/live.txt" "$WORK/new.txt" | grep -E '^[-+][^-+]'
    echo '```'
  fi
} > "$WORK/summary.md"
cat "$WORK/summary.md" | sed 's/^/ /'
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$WORK/summary.md" >> "$GITHUB_STEP_SUMMARY"
if ! diff -q <(grep -E '^(uses-)?permission' "$WORK/live.txt") <(grep -E '^(uses-)?permission' "$WORK/new.txt") > /dev/null; then
  echo "::warning title=Permissions changed::This build's permissions differ from $TAG. See the run summary."
fi
exit 0
