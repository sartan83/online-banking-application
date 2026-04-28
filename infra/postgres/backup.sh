#!/usr/bin/env bash
# ---------------------------------------------------------------
# pg_basebackup wrapper — EU DORA Art. 11 (BCP/DR)
#
# Takes a full base backup of the PostgreSQL cluster, compresses
# it with gzip, and stores it in $BACKUP_DIR with a UTC timestamp.
#
# Required env vars:
#   PGHOST, PGPORT, PGUSER   — connection details (or rely on libpq defaults)
#   BACKUP_DIR                — destination directory for the tarball
#
# Optional:
#   PGPASSWORD / .pgpass      — authentication
#   BACKUP_LABEL              — custom label (default: "dora_base_backup")
#
# Exit codes:
#   0 — success
#   1 — missing prerequisites / env vars
#   2 — pg_basebackup failure
#   3 — compression failure
# ---------------------------------------------------------------
set -euo pipefail

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
LABEL="${BACKUP_LABEL:-dora_base_backup}"

# ---------- pre-flight checks ----------
if [[ -z "${BACKUP_DIR:-}" ]]; then
  echo "ERROR: BACKUP_DIR is not set." >&2
  exit 1
fi

if ! command -v pg_basebackup &>/dev/null; then
  echo "ERROR: pg_basebackup not found on PATH." >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

BACKUP_FILE="${BACKUP_DIR}/base_backup_${TIMESTAMP}.tar"
COMPRESSED="${BACKUP_FILE}.gz"

# ---------- take the backup ----------
echo "[$(date -u +%FT%TZ)] Starting base backup (label: ${LABEL}) ..."
if ! pg_basebackup \
       --pgdata=- \
       --format=tar \
       --wal-method=fetch \
       --label="${LABEL}" \
       --checkpoint=fast \
       > "${BACKUP_FILE}"; then
  echo "ERROR: pg_basebackup failed." >&2
  rm -f "${BACKUP_FILE}"
  exit 2
fi

# ---------- compress ----------
echo "[$(date -u +%FT%TZ)] Compressing backup ..."
if ! gzip "${BACKUP_FILE}"; then
  echo "ERROR: gzip compression failed." >&2
  exit 3
fi

SIZE="$(du -h "${COMPRESSED}" | cut -f1)"
echo "[$(date -u +%FT%TZ)] Backup complete: ${COMPRESSED} (${SIZE})"
exit 0
