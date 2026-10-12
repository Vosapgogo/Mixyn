#!/bin/bash
# Puts the wardrobe back from an archive made by scripts/backup.sh.
#
# Usage:  scripts/restore.sh <archive.tar.gz>
#
# The database is REPLACED by the one in the archive: anything added after that backup is lost.
# Pictures from the archive are copied back; pictures that are already there are left alone.
# Stop the backend first, so it doesn't write while the data is being replaced.
set -euo pipefail
cd "$(dirname "$0")/.."

ARCHIVE="${1:-}"
if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
    echo "Usage: scripts/restore.sh <archive.tar.gz>" >&2
    exit 1
fi
IMAGES="backend/data/images"

setting() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- || true; }
DB_USER="$(setting POSTGRES_USER)"; DB_USER="${DB_USER:-mixyn}"
DB_NAME="$(setting POSTGRES_DB)";   DB_NAME="${DB_NAME:-mixyn}"

if ! docker compose exec -T db pg_isready -U "$DB_USER" -d "$DB_NAME" </dev/null >/dev/null 2>&1; then
    echo "The database is not running. Start Docker, then: docker compose up -d db" >&2
    exit 1
fi

echo "This replaces the current wardrobe database with the one from:"
echo "  $ARCHIVE"
printf "Type yes to continue: "
read -r answer
if [ "$answer" != "yes" ]; then
    echo "Nothing was changed."
    exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
tar -xzf "$ARCHIVE" -C "$WORK"

docker compose exec -T db psql -q -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$WORK/database.sql" >/dev/null
mkdir -p "$IMAGES"
cp -Rn "$WORK/images/." "$IMAGES/" 2>/dev/null || true

echo "Restored the database and $(ls "$WORK/images" | wc -l | tr -d ' ') pictures. Start the backend again."
