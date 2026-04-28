# PostgreSQL Backup & Point-in-Time Recovery (PITR)

EU DORA Art. 11 requires financial entities to maintain backup policies and
restoration/recovery procedures for ICT systems. This directory contains
scripts and configuration to support continuous WAL archiving and PITR for
the DevilsVault PostgreSQL database.

## Recovery Objectives

| Metric | Target | Rationale |
|--------|--------|-----------|
| **RPO** (Recovery Point Objective) | **5 minutes** | `archive_timeout = 300` ensures WAL segments are archived at most every 5 min, bounding maximum data loss. |
| **RTO** (Recovery Time Objective) | **1 hour** | Base backup restore + WAL replay for a moderately-sized database completes well within 1 h. Operator must practice drills to validate. |

## Files

| File | Purpose |
|------|---------|
| `backup.sh` | Takes a full base backup via `pg_basebackup`, compresses with gzip, stores in `$BACKUP_DIR`. |
| `restore.sh` | Restores from a base backup archive to a target point in time using archived WAL. |
| `postgresql.conf.sample` | Sample PostgreSQL configuration enabling WAL archiving. |

## Prerequisites

- PostgreSQL client tools (`pg_basebackup`, `pg_ctl`) available on PATH.
- WAL archiving enabled on the server (see `postgresql.conf.sample`).
- A WAL archive directory accessible to both the server and the restore host.

## Scheduling Backups

### Cron (Linux)

```cron
# Daily base backup at 02:00 UTC
0 2 * * * PGHOST=localhost PGPORT=5432 PGUSER=devilsvault BACKUP_DIR=/backups/postgres /path/to/backup.sh >> /var/log/pg_backup.log 2>&1
```

### Kubernetes CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: pg-base-backup
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: postgres:16-alpine
              command: ["/scripts/backup.sh"]
              env:
                - name: PGHOST
                  value: postgres-primary
                - name: PGPORT
                  value: "5432"
                - name: PGUSER
                  valueFrom:
                    secretKeyRef:
                      name: pg-credentials
                      key: username
                - name: PGPASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: pg-credentials
                      key: password
                - name: BACKUP_DIR
                  value: /backups
              volumeMounts:
                - name: backup-volume
                  mountPath: /backups
                - name: scripts
                  mountPath: /scripts
          restartPolicy: OnFailure
          volumes:
            - name: backup-volume
              persistentVolumeClaim:
                claimName: pg-backups-pvc
            - name: scripts
              configMap:
                name: pg-backup-scripts
                defaultMode: 0755
```

## Taking a Manual Backup

```bash
export PGHOST=localhost
export PGPORT=5432
export PGUSER=devilsvault
export PGPASSWORD=devilsvault      # Use .pgpass or a secret manager in production
export BACKUP_DIR=/tmp/pg_backups

./backup.sh
```

## Restoring to a Point in Time

```bash
export WAL_ARCHIVE_DIR=/var/lib/postgresql/wal_archive

./restore.sh /tmp/pg_backups/base_backup_20250615T020000Z.tar.gz \
             "2025-06-15 14:30:00+00" \
             /var/lib/postgresql/data_restored
```

After the script completes, review the generated `postgresql.auto.conf`,
start the server against the restored data directory, verify the data, and
promote.

## Testing the Restore (Drill)

A GitHub Actions workflow (`.github/workflows/restore-drill.yml`) is
provided for manual restore drills. It is triggered via `workflow_dispatch`
(not on every PR — the drill is expensive).

The drill:
1. Starts a PostgreSQL instance with WAL archiving enabled.
2. Writes known test data.
3. Takes a base backup via `backup.sh`.
4. Writes additional data and records the timestamp.
5. Drops the database.
6. Restores from the backup using `restore.sh`.
7. Asserts the known data is present in the restored instance.

Run it from the GitHub Actions UI: **Actions > Restore Drill > Run workflow**.

## Production Considerations

- **Remote storage**: Replace the local `cp` archive command with one that
  ships WAL to S3, GCS, Azure Blob, or another durable store. See
  `postgresql.conf.sample` for examples.
- **Encryption at rest**: Enable filesystem-level or storage-level encryption
  on the backup destination. Schema-level encryption is handled separately
  (see DORA-2.8).
- **Retention policy**: Keep at least 30 days of base backups and WAL. Align
  with your data-retention schedule.
- **Monitoring**: Alert on backup failures (non-zero exit from `backup.sh`)
  and WAL archiving lag (`pg_stat_archiver.last_failed_time`).
- **Deployment is on the operator**: This repo provides scripts and docs
  only. No production cron or scheduled job is deployed automatically.
