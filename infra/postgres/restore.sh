#!/usr/bin/env bash
# ---------------------------------------------------------------
# Point-in-Time Recovery (PITR) restore script — EU DORA Art. 11
#
# Restores a PostgreSQL cluster from a base backup archive and
# replays WAL up to a specified target time.
#
# Usage:
#   ./restore.sh <backup_archive.tar.gz> <target_time> [new_data_dir]
#
# Arguments:
#   backup_archive  — path to the gzipped base backup tarball
#   target_time     — ISO-8601 timestamp to recover to
#                     (e.g. "2025-06-15 14:30:00+00")
#   new_data_dir    — optional; defaults to /var/lib/postgresql/data_restored
#
# Required env vars:
#   WAL_ARCHIVE_DIR — directory containing archived WAL segments
#
# WARNING: This script does NOT auto-restart PostgreSQL. The
#          operator must review the restored data directory and
#          start the server manually.
#
# Exit codes:
#   0 — restore prepared successfully
#   1 — missing arguments / env vars
#   2 — extraction failure
#   3 — configuration error
# ---------------------------------------------------------------
set -euo pipefail

# ---------- argument parsing ----------
if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <backup_archive.tar.gz> <target_time> [new_data_dir]" >&2
  exit 1
fi

ARCHIVE="$1"
TARGET_TIME="$2"
DATA_DIR="${3:-/var/lib/postgresql/data_restored}"

if [[ -z "${WAL_ARCHIVE_DIR:-}" ]]; then
  echo "ERROR: WAL_ARCHIVE_DIR is not set." >&2
  exit 1
fi

if [[ ! -f "${ARCHIVE}" ]]; then
  echo "ERROR: Backup archive not found: ${ARCHIVE}" >&2
  exit 1
fi

if [[ ! -d "${WAL_ARCHIVE_DIR}" ]]; then
  echo "ERROR: WAL archive directory not found: ${WAL_ARCHIVE_DIR}" >&2
  exit 1
fi

# ---------- prepare data directory ----------
if [[ -d "${DATA_DIR}" ]]; then
  echo "WARNING: ${DATA_DIR} already exists — aborting to prevent data loss." >&2
  exit 1
fi

echo "[$(date -u +%FT%TZ)] Extracting base backup to ${DATA_DIR} ..."
mkdir -p "${DATA_DIR}"
if ! tar -xzf "${ARCHIVE}" -C "${DATA_DIR}"; then
  echo "ERROR: Failed to extract ${ARCHIVE}." >&2
  rm -rf "${DATA_DIR}"
  exit 2
fi

# ---------- write recovery configuration ----------
# PostgreSQL 12+ uses recovery signal files instead of recovery.conf
RECOVERY_SIGNAL="${DATA_DIR}/recovery.signal"
PG_AUTO_CONF="${DATA_DIR}/postgresql.auto.conf"

touch "${RECOVERY_SIGNAL}"

cat >> "${PG_AUTO_CONF}" <<EOF

# --- PITR recovery settings (added by restore.sh) ---
restore_command = 'cp ${WAL_ARCHIVE_DIR}/%f %p'
recovery_target_time = '${TARGET_TIME}'
recovery_target_action = 'pause'
EOF

echo "[$(date -u +%FT%TZ)] Recovery configuration written."
echo ""
echo "=== Next steps ==="
echo "1. Review ${PG_AUTO_CONF} for correctness."
echo "2. Start PostgreSQL pointing at the restored data directory:"
echo "     pg_ctl -D ${DATA_DIR} start"
echo "3. Verify data, then promote if satisfied:"
echo "     SELECT pg_wal_replay_resume();  -- if paused"
echo "     SELECT pg_promote();            -- finalize"
echo "4. Update connection strings to point to the restored instance."
echo ""
echo "[$(date -u +%FT%TZ)] Restore preparation complete."
exit 0
