#!/usr/bin/env bash
#
# Despliegue del backend en el VPS (49.13.196.23) — se ejecuta EN EL SERVIDOR, por SSH.
#
#   ssh root@49.13.196.23
#   cd /ruta/del/repo && ./deploy.sh
#
# Qué hace, en orden:
#   1. Backup de la base de datos (dentro del contenedor db, copiado al host).
#   2. git pull de main.
#   3. Aplica las migraciones SQL pendientes de db/migrations/ (son idempotentes).
#   4. Reconstruye y reinicia el contenedor del backend.
#   5. Espera a que responda y hace un smoke test.
#
# Las migraciones se aplican ANTES de levantar el código nuevo porque son aditivas:
# el backend viejo que sigue corriendo en ese momento simplemente ignora las columnas.

set -euo pipefail

DB_NAME="${DB_NAME:-BDClinicaSAAS}"
DB_USER="${DB_USER:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
API_URL="${API_URL:-https://49.13.196.23.sslip.io}"

cd "$(dirname "$0")"

# docker compose (v2) o docker-compose (v1), lo que esté disponible.
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

echo "==> 1/5  Backup de la base de datos"
mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/therateam-$STAMP.dump"
$DC exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" -F c > "$BACKUP_FILE"
echo "    Backup guardado en $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
# Un backup de 0 bytes significa que el pg_dump falló silenciosamente — abortar antes de tocar nada.
if [ ! -s "$BACKUP_FILE" ]; then
  echo "    ERROR: el backup salió vacío. Se aborta el despliegue." >&2
  exit 1
fi

echo "==> 2/5  git pull"
git pull --ff-only origin main

echo "==> 3/5  Migraciones SQL"
shopt -s nullglob
for f in db/migrations/*.sql; do
  echo "    Aplicando $f"
  $DC exec -T db psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$f"
done
shopt -u nullglob

echo "==> 4/5  Rebuild del backend"
$DC up -d --build backend

echo "==> 5/5  Esperando a que el backend responda"
for i in $(seq 1 60); do
  # 400 = la API está viva y rechazó el body vacío. 5xx/000 = todavía arrancando.
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API_URL/api/auth/login" \
          -H 'Content-Type: application/json' -d '{}' --max-time 5 || true)"
  if [ "$code" = "400" ] || [ "$code" = "401" ]; then
    echo "    OK — el backend responde (HTTP $code en /api/auth/login)"
    echo
    echo "Despliegue terminado."
    echo "RECUERDA: entrar a Seguridad > Roles y activar 'ver celular de pacientes'"
    echo "en los roles que corresponda — arranca desactivado para todos, incluido ADMIN."
    exit 0
  fi
  sleep 5
done

echo "    ERROR: el backend no respondió en 5 minutos. Revisa los logs:" >&2
echo "      $DC logs --tail=100 backend" >&2
echo "    Para revertir la BD:  $DC exec -T db pg_restore -U $DB_USER -d $DB_NAME --clean --no-owner < $BACKUP_FILE" >&2
exit 1
