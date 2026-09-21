#!/usr/bin/env bash
#
# Despliegue del backend en el VPS (49.13.196.23) — se ejecuta EN EL SERVIDOR, por SSH.
#
#   ssh root@49.13.196.23
#   cd /ruta/del/repo && ./deploy.sh
#
# Formas de llamarlo:
#   ./deploy.sh                    despliega; si hay migraciones pendientes, las lista y PREGUNTA
#   ./deploy.sh --sin-migraciones  despliega solo el codigo, sin tocar la base
#   ./deploy.sh --si               no pregunta (para llamarlo desde otro script)
#
# Qué hace, en orden:
#   1. Backup de la base de datos (dentro del contenedor db, copiado al host).
#   2. git pull de main.
#   3. Aplica las migraciones SQL que todavía no se hayan aplicado (db/aplicar-migraciones.sh).
#   4. Reconstruye y reinicia el contenedor del backend.
#   5. Espera a que responda y hace un smoke test.
#
# Las migraciones se aplican ANTES de levantar el código nuevo porque son aditivas:
# el backend viejo que sigue corriendo en ese momento simplemente ignora las columnas.
#
# Ninguno de los pasos borra datos. El backup del paso 1 se hace antes de tocar nada y el
# script se aborta si sale vacío. Para ver qué falta por aplicar sin desplegar:
#   ./db/aplicar-migraciones.sh --estado

set -euo pipefail

SIN_MIGRACIONES=0
SIN_PREGUNTAR=0
for arg in "$@"; do
  case "$arg" in
    --sin-migraciones) SIN_MIGRACIONES=1 ;;
    --si|--yes|-y)     SIN_PREGUNTAR=1 ;;
    *) echo "Opcion desconocida: $arg" >&2
       echo "Uso: $0 [--sin-migraciones] [--si]" >&2
       exit 2 ;;
  esac
done

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
# Cada archivo se aplica UNA sola vez: db/aplicar-migraciones.sh lleva el registro en la tabla
# schema_migrations. Antes este paso reaplicaba todos los .sql en cada despliegue, lo que
# dependía de que ninguna migración dejara nunca de ser repetible.
#
# Aun así el paso PREGUNTA antes de tocar la base, y con --sin-migraciones ni la mira: el
# registro evita que una migración se repita, pero no vuelve inofensiva a la que todavía no se
# aplicó. Ver qué va a correr antes de que corra es lo que permite parar a tiempo.
if [ "$SIN_MIGRACIONES" = "1" ]; then
  echo "    --sin-migraciones: no se toca la base de datos."
  echo "    Lo que queda pendiente para un proximo despliegue:"
  DB_NAME="$DB_NAME" DB_USER="$DB_USER" ./db/aplicar-migraciones.sh --estado | grep 'PENDIENTE'     || echo "      (ninguna)"
else
  ESTADO="$(DB_NAME="$DB_NAME" DB_USER="$DB_USER" ./db/aplicar-migraciones.sh --estado)"
  PENDIENTES="$(printf '%s
' "$ESTADO" | grep -c 'PENDIENTE' || true)"

  if [ "$PENDIENTES" = "0" ]; then
    echo "    Sin migraciones pendientes — la base se queda como está."
  else
    echo "    $PENDIENTES migracion(es) sin aplicar:"
    printf '%s
' "$ESTADO" | grep 'PENDIENTE' | sed 's/^ */      /'
    echo "    El backup de esta corrida ya está en $BACKUP_FILE"
    # Nada toca la base sin un si explicito. Cuando no hay terminal para preguntar (cron, otro
    # script, ./deploy.sh < /dev/null) se para en vez de seguir: aplicar una migracion sin que
    # nadie la haya visto es justo lo que se quiere evitar. Para esos casos esta --si.
    if [ "$SIN_PREGUNTAR" != "1" ]; then
      if [ ! -t 0 ]; then
        echo "    No hay terminal para confirmar y no se paso --si: no se toca la base." >&2
        echo "      ./deploy.sh --si                 aplicarlas sin preguntar" >&2
        echo "      ./deploy.sh --sin-migraciones    desplegar solo el codigo" >&2
        exit 1
      fi
      printf "    ¿Aplicarlas ahora? [s/N] "
      read -r RESPUESTA
      case "$RESPUESTA" in
        s|S|si|SI|Si|y|Y) ;;
        *) echo "    Cancelado. No se toco la base. Para desplegar solo el codigo:" >&2
           echo "      ./deploy.sh --sin-migraciones" >&2
           exit 1 ;;
      esac
    fi
    DB_NAME="$DB_NAME" DB_USER="$DB_USER" ./db/aplicar-migraciones.sh
  fi
fi

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
