#!/usr/bin/env bash
#
# Aplica las migraciones pendientes de db/migrations/, una sola vez cada una.
#
#   ./db/aplicar-migraciones.sh              aplica lo que falte
#   ./db/aplicar-migraciones.sh --estado     solo muestra qué está aplicado y qué falta
#
# POR QUÉ EXISTE: antes deploy.sh reaplicaba TODOS los .sql en cada despliegue. Funcionaba
# solo mientras cada migración fuera repetible, y bastaba una con DELETE, UPDATE o un ALTER
# destructivo para que el segundo despliegue hiciera daño de verdad. Ahora hay una tabla
# schema_migrations que registra lo ya aplicado y cada archivo corre exactamente una vez.
#
# Variables que acepta (todas con valor por defecto):
#   DB_NAME, DB_USER, MIGRATIONS_DIR
#   PSQL_CMD  — cómo llegar a psql. Por defecto pasa por el contenedor db de docker compose.
#   ALLOW_CHECKSUM_MISMATCH=1 — reaplicar una migración que cambió desde que se aplicó.

set -euo pipefail

DB_NAME="${DB_NAME:-BDClinicaSAAS}"
DB_USER="${DB_USER:-postgres}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$(dirname "$0")/migrations}"

if [ -z "${PSQL_CMD:-}" ]; then
  if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi
  PSQL_CMD="$DC exec -T db psql -U $DB_USER -d $DB_NAME"
fi

SOLO_ESTADO=0
[ "${1:-}" = "--estado" ] && SOLO_ESTADO=1

# Consulta que devuelve un valor suelto, sin cabeceras ni espacios alrededor.
q() { $PSQL_CMD -v ON_ERROR_STOP=1 -q -t -A -c "$1" | tr -d '[:space:]'; }
# Sentencia cuyo resultado no interesa.
x() { $PSQL_CMD -v ON_ERROR_STOP=1 -q -c "$1" >/dev/null; }

suma() { sha256sum "$1" | cut -d' ' -f1; }

# ── Tabla de control ────────────────────────────────────────────────────────
ES_PRIMERA_VEZ=0
[ "$(q "SELECT to_regclass('public.schema_migrations') IS NULL")" = "t" ] && ES_PRIMERA_VEZ=1

# El SET calla el "NOTICE: la relación ya existe, omitiendo" que sale en cada despliegue a
# partir del segundo y que, sin serlo, parece un problema.
x "SET client_min_messages = warning;
   CREATE TABLE IF NOT EXISTS schema_migrations (
     nombre      TEXT PRIMARY KEY,
     checksum    TEXT NOT NULL,
     aplicada_en TIMESTAMPTZ NOT NULL DEFAULT now()
   )"

# Primera vez sobre una base que YA existía: sus migraciones ya corrieron (el deploy anterior
# las aplicaba todas en cada despliegue), así que se registran sin volver a ejecutarlas.
# Reejecutarlas sería justamente el riesgo que este script viene a quitar.
# En una base recién creada no hay nada que registrar y se aplican normalmente.
if [ "$ES_PRIMERA_VEZ" = "1" ] && [ "$(q "SELECT to_regclass('public.pacientes') IS NOT NULL")" = "t" ]; then
  echo "  Base existente — se registran las migraciones ya aplicadas, sin re-ejecutarlas:"
  for f in "$MIGRATIONS_DIR"/*.sql; do
    [ -e "$f" ] || continue
    n="$(basename "$f")"
    x "INSERT INTO schema_migrations (nombre, checksum) VALUES ('$n', '$(suma "$f")')
       ON CONFLICT (nombre) DO NOTHING"
    echo "    ya aplicada   $n"
  done
fi

# ── Recorrido de los archivos ───────────────────────────────────────────────
APLICADAS=0
PENDIENTES=0

shopt -s nullglob
for f in "$MIGRATIONS_DIR"/*.sql; do
  n="$(basename "$f")"
  s="$(suma "$f")"
  previo="$(q "SELECT checksum FROM schema_migrations WHERE nombre = '$n'")"

  if [ -n "$previo" ] && [ "$previo" = "$s" ]; then
    [ "$SOLO_ESTADO" = "1" ] && echo "  aplicada   $n"
    continue
  fi

  if [ -n "$previo" ]; then
    # El archivo cambió después de haberse aplicado. Es un aviso importante: lo que se editó
    # NO está en la base, y sin este control el despliegue seguía en silencio como si sí.
    echo "" >&2
    echo "  AVISO: $n cambió desde que se aplicó." >&2
    echo "         Lo que editaste NO está en la base de datos." >&2
    echo "         Lo correcto casi siempre es crear una migración nueva con ese cambio." >&2
    echo "         Si de verdad quieres reaplicar este archivo tal cual:" >&2
    echo "           ALLOW_CHECKSUM_MISMATCH=1 $0" >&2
    echo "" >&2
    if [ "${ALLOW_CHECKSUM_MISMATCH:-0}" != "1" ]; then exit 1; fi
    echo "  ALLOW_CHECKSUM_MISMATCH=1 — se reaplica $n"
  fi

  if [ "$SOLO_ESTADO" = "1" ]; then
    echo "  PENDIENTE  $n"
    PENDIENTES=$((PENDIENTES + 1))
    continue
  fi

  echo "  aplicando  $n"
  $PSQL_CMD -v ON_ERROR_STOP=1 < "$f"
  # Solo se registra si psql terminó bien: con ON_ERROR_STOP un fallo aborta el script antes
  # de llegar aquí, así que una migración a medias nunca queda marcada como aplicada.
  x "INSERT INTO schema_migrations (nombre, checksum) VALUES ('$n', '$s')
     ON CONFLICT (nombre) DO UPDATE SET checksum = EXCLUDED.checksum, aplicada_en = now()"
  APLICADAS=$((APLICADAS + 1))
done
shopt -u nullglob

if [ "$SOLO_ESTADO" = "1" ]; then
  echo "  ($PENDIENTES pendiente(s))"
elif [ "$APLICADAS" = "0" ]; then
  echo "  Sin migraciones pendientes."
else
  echo "  $APLICADAS migración(es) aplicada(s)."
fi
