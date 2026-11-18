#!/data/data/com.termux/files/usr/bin/bash
# Generate the Tribal TV release signing key. Run this ONCE.
#
# Needs the `openssl-tool` package (~2 MB), NOT a JDK. The `openssl` package
# already installed on this device ships only libssl/libcrypto and headers;
# the CLI binary is a separate package.
#
# Why once: this key is the app's permanent identity. ANDROID_ID is scoped to
# (signing key, user, device), so replacing the key after a public build changes
# the identity of every existing install at the same moment and orphans every
# session row the backend holds for every user. There is no graceful migration.
#
# The key it replaces is burned: its password is hardcoded in the committed
# workflows and its base64 was printed to a public step summary. Rotating now
# costs nothing because no backend exists yet to hold session rows. Rotating
# later costs every user.
#
# The password is read with `read -s` and handed to openssl on stdin. It never
# appears in argv, in this script, in .bash_history, or in the environment.
#
# Usage:
#   bash scripts/rotate-signing-key.sh

set -euo pipefail
export TMPDIR="${TMPDIR:-$HOME/tmp}"
mkdir -p "$TMPDIR"

ALIAS="tribaltv"
DIR="$HOME/keys"
P12="$DIR/tribal-tv-release.p12"
KEY="$DIR/tribal-tv-release.key.pem"
CRT="$DIR/tribal-tv-release.cert.pem"

die() { printf 'ABORT: %s\n' "$*" >&2; exit 1; }

command -v openssl >/dev/null 2>&1 || die "no openssl CLI. Run: pkg install -y openssl-tool"

[ -e "$P12" ] && die "$P12 already exists.
  If it is the key you want, do not regenerate it -- back it up and use it.
  If you really mean to replace it, move it aside first:
    mv \"$P12\" \"$P12.old-\$(date +%s)\""

umask 077
mkdir -p "$DIR"
chmod 700 "$DIR"

cat <<'NOTE'
Generating the release key.

  Password:  choose a NEW strong one, at least 12 characters.
             NOT tribaltv-release-2026 -- that string is in this repo's
             git history forever.

You will type it twice. Nothing is echoed.
NOTE
printf 'Password: '
read -rs PASS
echo
printf 'Again:    '
read -rs PASS2
echo

[ "$PASS" = "$PASS2" ] || die "passwords differ"
[ "${#PASS}" -ge 12 ] || die "use at least 12 characters"
unset PASS2

# 1. RSA 2048 + self-signed cert. -nodes keeps the intermediate key file
#    unencrypted; it is deleted in step 3 and only ever exists inside $DIR.
openssl req -x509 -newkey rsa:2048 -nodes -sha256 \
  -keyout "$KEY" -out "$CRT" \
  -days 10000 \
  -subj "/CN=Tribal TV/O=Tribal TV/C=ZA" \
  -addext "basicConstraints=critical,CA:FALSE" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "subjectKeyIdentifier=hash" \
  || die "openssl req failed"

# 2. PKCS#12 with the OLD algorithms on purpose.
#
#    OpenSSL 3 defaults to AES-256-CBC + HmacPBESHA256. Java's PKCS12 KeyStore
#    only learned to read those in JDK 19; on JDK 17 (what CI uses) the load
#    fails with "keystore password was incorrect", which reads like a wrong
#    password and is not one. 3DES + SHA-1 MAC is readable by every JDK from 8
#    to 21. It is weaker than AES, but this file is a signing key protected by
#    a long password, not a data store.
printf '%s\n' "$PASS" | openssl pkcs12 -export \
  -inkey "$KEY" -in "$CRT" \
  -name "$ALIAS" \
  -certpbe PBE-SHA1-3DES \
  -keypbe PBE-SHA1-3DES \
  -macalg sha1 \
  -out "$P12" \
  -passout stdin || die "openssl pkcs12 -export failed"

# 3. Prove the .p12 opens before deleting the only other copy of the key.
printf '%s\n' "$PASS" | openssl pkcs12 -in "$P12" -noout -passin stdin \
  || die "the .p12 does not open with that password -- key NOT deleted, nothing registered"
rm -f "$KEY"

chmod 600 "$P12" "$CRT"

sha1=$(openssl x509 -in "$CRT" -noout -fingerprint -sha1 | sed 's/^.*=//')
sha256=$(openssl x509 -in "$CRT" -noout -fingerprint -sha256 | sed 's/^.*=//')

[ -n "$sha1" ] || die "could not read SHA1 from the certificate"
[ -n "$sha256" ] || die "could not read SHA256 from the certificate"

cat <<EOF

================================================================
Paste this into Google Cloud Console -> Credentials ->
Android OAuth client -> "SHA-1 certificate fingerprint":

  $sha1

Package name for that same client:

  com.tribaltv.app

(Not com.example.iptvapp -- that is the namespace, not the app id.)

SHA-256, for App Links / Play later:

  $sha256
================================================================

EOF

# The base64 is the private key. Clipboard if possible, 0600 file otherwise --
# never stdout, so it cannot land in a scrollback buffer or a pasted log.
if command -v termux-clipboard-set >/dev/null 2>&1; then
  base64 -w 0 "$P12" | termux-clipboard-set
  cat <<'EOF'
The keystore base64 is now on the clipboard (it was never printed).

  1. GitHub -> Settings -> Secrets and variables -> Actions
     -> New repository secret -> name TRIBAL_TV_KEYSTORE_B64
     -> paste.
  2. Clear the clipboard:  termux-clipboard-set ''
  3. Add three more secrets, typed by hand:
       TRIBAL_TV_KEYSTORE_PASSWORD   the password you just chose
       TRIBAL_TV_KEY_ALIAS           tribaltv
       TRIBAL_TV_KEY_PASSWORD        the same password
EOF
else
  B64="$P12.b64"
  base64 -w 0 "$P12" > "$B64"
  chmod 600 "$B64"
  cat <<EOF
termux-clipboard-set is not installed, so the base64 is in a 0600 file:

  $B64

  1. cat it, copy the single line, paste into the GitHub secret
     TRIBAL_TV_KEYSTORE_B64.
  2. rm "$B64"
  3. Add three more secrets, typed by hand:
       TRIBAL_TV_KEYSTORE_PASSWORD   the password you just chose
       TRIBAL_TV_KEY_ALIAS           tribaltv
       TRIBAL_TV_KEY_PASSWORD        the same password
EOF
fi

cat <<EOF

Then, before you register anything in Google:

  - Back up $P12 and its password to a password manager or
    encrypted storage. Not to this repo, not to Drive unencrypted, not to a
    chat. Lose it and you can never update this app identity again.
  - Delete .github/workflows/generate-keystore.yml -- it hardcodes the old
    password and prints base64 to a public step summary.
  - Push. The release job now runs scripts/verify-apk-signing.sh against the
    APK it just built and fails if the certificate is not the one above, so a
    green build means the APK matches what you registered. There is no local
    keytool here and none is needed.
EOF
