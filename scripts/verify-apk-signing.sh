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

ACTUAL=$(printf '%s\n' "$out" \
  | sed -n 's/^Signer #1 certificate SHA-1 digest: //p' \
  | head -1)
ACTUAL=$(norm "$ACTUAL")

if [ -z "$ACTUAL" ]; then
  echo "apksigner printed no SHA-1 digest for $APK" >&2
  exit 2
fi

echo "apk:      $APK"
echo "expected: $EXPECTED"
echo "actual:   $ACTUAL"

if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "::error::APK is signed by $ACTUAL, not the key registered in the OAuth client ($EXPECTED). Google Sign-In will fail with DEVELOPER_ERROR."
  exit 1
fi

echo "OK: signing certificate matches the registered OAuth client."
