#!/data/data/com.termux/files/usr/bin/bash
# Apply the hosted Supabase Auth SMTP settings for Tribal TV.
#
# Local dev is deliberately untouched: `supabase start` keeps using Inbucket
# (http://localhost:54324). This script only PATCHes the linked hosted project,
# through the Management API.
#
# Secrets are read from files, never from argv, never echoed:
#   ~/.supabase_token   Supabase personal access token (sbp_...)
#   ~/.resend_key       Resend API key, sending-only, restricted to the domain
#
# Still a placeholder:
#   SENDER_EMAIL   the verified sending address, e.g. no-reply@mail.example.com
#
# PROJECT_REF is filled in; SUPABASE_PROJECT_REF overrides it.
#
# Usage:
#   bash scripts/apply-auth-smtp.sh --get     current config, secrets redacted
#   bash scripts/apply-auth-smtp.sh --check   the payload, key redacted
#   bash scripts/apply-auth-smtp.sh           apply

set -euo pipefail
export TMPDIR="${TMPDIR:-$HOME/tmp}"
mkdir -p "$TMPDIR"

PROJECT_REF="${SUPABASE_PROJECT_REF:-jigzoerxsbumpijksveo}"
SENDER_EMAIL="${VOD_SENDER_EMAIL:-__SENDER_EMAIL__}"
SENDER_NAME="${VOD_SENDER_NAME:-Tribal TV}"

TOKEN_FILE="$HOME/.supabase_token"
RESEND_FILE="$HOME/.resend_key"

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
TEMPLATE="$SCRIPT_DIR/../supabase/templates/magic_link.html"

die() { printf 'ABORT: %s\n' "$*" >&2; exit 1; }

[ -r "$TOKEN_FILE" ] || die "missing $TOKEN_FILE (chmod 600)"

SUPABASE_TOKEN=$(tr -d '\r\n' < "$TOKEN_FILE")
[ -n "$SUPABASE_TOKEN" ] || die "$TOKEN_FILE is empty"

API="https://api.supabase.com/v1/projects/$PROJECT_REF/config/auth"

# Keep the token out of argv: curl reads the header from a 0600 config file.
cfg="$TMPDIR/curl-auth.cfg"
( umask 077; printf 'header = "Authorization: Bearer %s"\n' "$SUPABASE_TOKEN" > "$cfg" )
trap 'rm -f "$cfg" "$TMPDIR/auth-smtp.out"' EXIT

redact() {
  python3 -c '
import json, sys
d = json.load(sys.stdin)
# Suffix match, not substring: external_keycloak_client_id is not a secret,
# and password_hibp_enabled is a boolean. Mask only non-empty strings, so a
# null field stays visibly null instead of looking set-but-hidden.
SECRET = ("_pass", "_password", "_secret", "_secrets", "_key", "_token")
for k in list(d):
    if k.endswith(SECRET) and isinstance(d[k], str) and d[k]:
        d[k] = "<redacted>"
print(json.dumps(d, indent=2, sort_keys=True))
'
}

case "$PROJECT_REF" in
  __*) die "PROJECT_REF is still a placeholder" ;;
esac

case "${1:-}" in
  --get)
    curl -sS -K "$cfg" "$API" | redact
    exit 0
    ;;
esac

# Everything below sends mail, so it needs the Resend key. --get does not.
[ -r "$RESEND_FILE" ] || die "missing $RESEND_FILE (chmod 600)"
[ -s "$RESEND_FILE" ] || die "$RESEND_FILE is empty"
[ -r "$TEMPLATE" ] || die "missing $TEMPLATE"

case "$SENDER_EMAIL" in
  __*) die "SENDER_EMAIL is still a placeholder" ;;
esac

# Build the payload in Python so the key is read from the file, never from argv.
payload=$(python3 - "$SENDER_EMAIL" "$SENDER_NAME" "$TEMPLATE" <<'PY'
import json, os, sys
email, name, template_path = sys.argv[1], sys.argv[2], sys.argv[3]
key = open(os.path.expanduser("~/.resend_key")).read().strip()
body = open(template_path, encoding="utf-8").read()
print(json.dumps({
    "smtp_host": "smtp.resend.com",
    "smtp_port": "465",
    "smtp_user": "resend",
    "smtp_pass": key,
    "smtp_admin_email": email,
    "smtp_sender_name": name,
    "mailer_otp_exp": 600,
    # 3 is the largest value that stays under Resend's 100/day even if this
    # limit is per-project: 3 * 24 = 72, leaving 28 for a burst. If it is
    # per-user, 3/hour is still enough for a mistyped address plus a retry.
    # Either way Supabase's 429 is the first thing to trip, and a 429 is a
    # visible error -- Resend's daily cap is not, it just stops delivering.
    "rate_limit_email_sent": 3,
    "external_email_enabled": True,
    "disable_signup": False,
    # The hosted project still ships the default magic-link template, which
    # is a {{ .ConfirmationURL }} link and no code. The app has no deep-link
    # handler and no App Links verification, so that email cannot be used.
    # This is the code-only template the app actually reads.
    "mailer_subjects_magic_link": "Your Tribal TV sign-in code",
    "mailer_templates_magic_link_content": body,
}))
PY
)

if [ "${1:-}" = "--check" ]; then
  printf '%s\n' "$payload" | redact
  exit 0
fi

code=$(curl -sS -K "$cfg" -o "$TMPDIR/auth-smtp.out" -w '%{http_code}' \
  -X PATCH "$API" \
  -H 'Content-Type: application/json' \
  --data-binary "$payload")

if [ "$code" != "200" ]; then
  printf 'HTTP %s\n' "$code" >&2
  cat "$TMPDIR/auth-smtp.out" >&2
  exit 1
fi

echo "applied (HTTP 200)"
echo
echo "Now verify the template actually took. The API accepts the field, but a"
echo "silently-ignored write would leave the default link template in place and"
echo "the app would receive an email with no code in it:"
echo
echo "  bash scripts/apply-auth-smtp.sh --get | grep magic_link"
echo
echo "Expect {{ .Token }} in the content and no {{ .ConfirmationURL }}."
