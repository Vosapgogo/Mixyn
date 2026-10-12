#!/bin/bash
# Saves the wardrobe into one dated archive, so it survives a broken Docker or a broken disk.
#
# The wardrobe is the only part of Mixyn that is not in git:
#   - the database (users, items, their tags) lives in a Docker volume,
#   - the item pictures live in backend/data/images.
# The archive holds a dump of the database and a copy of the pictures. It does NOT hold .env
# (the Gemini key): keep that one in a password manager.
#
# Usage:  scripts/backup.sh [folder]
# Without a folder the archive goes to "Mixyn backups" in iCloud Drive. The newest $KEEP archives
# are kept; older ones made by this script are removed.
set -euo pipefail
cd "$(dirname "$0")/.."

DEST="${1:-$HOME/Library/Mobile Documents/com~apple~CloudDocs/Mixyn backups}"
KEEP=5
IMAGES="backend/data/images"

# The database name and user, as Docker Compose reads them from .env.
setting() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- || true; }
DB_USER="$(setting POSTGRES_USER)"; DB_USER="${DB_USER:-mixyn}"
DB_NAME="$(setting POSTGRES_DB)";   DB_NAME="${DB_NAME:-mixyn}"

if ! docker compose exec -T db pg_isready -U "$DB_USER" -d "$DB_NAME" </dev/null >/dev/null 2>&1; then
    echo "The database is not running. Start Docker, then: docker compose up -d db" >&2
    exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --clean --if-exists: restoring the dump replaces whatever is in the database at that moment.
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists </dev/null > "$WORK/database.sql"
mkdir -p "$WORK/images"
if [ -d "$IMAGES" ]; then
    cp -R "$IMAGES/." "$WORK/images/"
fi

mkdir -p "$DEST"
ARCHIVE="$DEST/mixyn-wardrobe-$(date +%Y-%m-%d-%H%M).tar.gz"
tar -czf "$ARCHIVE" -C "$WORK" database.sql images

# Keep only the newest few.
ls -1t "$DEST"/mixyn-wardrobe-*.tar.gz 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
    rm -f "$old"
done

echo "Saved $(ls "$WORK/images" | wc -l | tr -d ' ') pictures and the database to:"
echo "  $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1 | tr -d ' '))"
