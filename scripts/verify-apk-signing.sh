#!/data/data/com.termux/files/usr/bin/bash
# Verify that an APK is signed by the certificate registered in the Google
# OAuth client for Tribal TV.
#
# Why this exists: Google Sign-In compares the calling app's signing
# certificate against the SHA-1 in the OAuth client. A mismatch is reported to
# the app as DEVELOPER_ERROR -- no detail, no hint, and nothing in the build
# says anything is wrong. The APK is the only place the truth lives, so the
# build checks it there.
#
# This runs in CI, where apksigner is part of the Android SDK build-tools. It
# does not need a JDK on the dev device.
#
# Usage:
#   bash scripts/verify-apk-signing.sh <apk> <expected-sha1>
#
# <expected-sha1> is 40 hex characters; colons and case are ignored.
#
# Exit codes:
#   0  the APK's signing certificate matches
#   1  it does not match
#   2  the check could not be performed (missing APK, no apksigner, unsigned)

set -euo pipefail

APK="${1:-}"
EXPECTED="${2:-}"

if [ -z "$APK" ] || [ -z "$EXPECTED" ]; then
  echo "usage: $0 <apk> <expected-sha1>" >&2
  exit 2
fi

if [ ! -f "$APK" ]; then
  echo "no such APK: $APK" >&2
  exit 2
fi

norm() { printf '%s' "$1" | tr -d ':' | tr 'A-F' 'a-f'; }

EXPECTED=$(norm "$EXPECTED")
case "$EXPECTED" in
  *[!0-9a-f]*|'') echo "expected SHA-1 is not 40 hex characters: $EXPECTED" >&2; exit 2 ;;
esac
[ "${#EXPECTED}" -eq 40 ] || { echo "expected SHA-1 is ${#EXPECTED} chars, not 40" >&2; exit 2; }

APKSIGNER=""
for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
  [ -n "$root" ] || continue
  candidate=$(ls -d "$root"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
  if [ -n "$candidate" ]; then
    APKSIGNER="$candidate"
    break
  fi
done

if [ -z "$APKSIGNER" ]; then
  echo "apksigner not found under ANDROID_HOME/ANDROID_SDK_ROOT build-tools" >&2
  exit 2
fi

if ! out=$("$APKSIGNER" verify --print-certs "$APK" 2>&1); then
  printf 'apksigner could not verify %s:\n%s\n' "$APK" "$out" >&2
  exit 2
fi

if [ -z "$out" ]; then
  echo "::error::apksigner produced no output for $APK" >&2
  exit 2
fi

# Membership test, not extraction. Parsing apksigner's label is what broke this
# check the first time it ran: the wording and spacing of the
# "Signer #1 certificate ... digest" line are not a stable interface across
# build-tools versions. The digest is 40 hex characters, so if it appears
# anywhere in the output, it is the signer's.
flat=$(printf '%s\n' "$out" | tr -d ':' | tr 'A-F' 'a-f')

echo "apk:      $APK"
echo "expected: $EXPECTED"

if printf '%s' "$flat" | grep -q "$EXPECTED"; then
  echo "OK: signing certificate matches the registered OAuth client."
  exit 0
fi

echo "apksigner said:"
printf '%s\n' "$out"
echo "::error::the APK is not signed by $EXPECTED. Google Sign-In would fail with DEVELOPER_ERROR."
exit 1
