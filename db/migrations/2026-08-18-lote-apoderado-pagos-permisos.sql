-- Migración 2026-08-18 — despliegue de:
--   * Citas masivas agrupadas por lote (sin crear paquetes en el catálogo)
--   * Pagos adicionales / devoluciones / concepto
--   * Permiso por rol para ver el celular de los pacientes
--   * Datos del apoderado obligatorios cuando el paciente es menor de 18 años
--
-- Todas las columnas son aditivas (nullable o con DEFAULT), así que es seguro aplicarlas
-- ANTES de desplegar el código nuevo: el backend actualmente en producción las ignora.
--
-- Es idempotente (IF NOT EXISTS) — se puede volver a correr sin romper nada.

BEGIN;

-- ── Citas masivas: agrupación liviana por lote ───────────────────────────────
-- loteMasivoId lo genera el frontend (UUID) y se repite en todas las citas del lote;
-- loteTotalPlaneado guarda cuántas se planearon, para saber cuántas faltan por crear.
ALTER TABLE citas ADD COLUMN IF NOT EXISTS lote_masivo_id      VARCHAR(64);
ALTER TABLE citas ADD COLUMN IF NOT EXISTS lote_total_planeado INTEGER;

-- Se consulta el lote entero por su id en cada creación (validación de cupo).
CREATE INDEX IF NOT EXISTS idx_citas_lote_masivo_id ON citas (lote_masivo_id);

-- ── Pagos: adicionales, devoluciones y concepto libre ────────────────────────
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS concepto       VARCHAR(255);
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS es_adicional   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS es_devolucion  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS pago_origen_id BIGINT;

-- ── Permiso por rol: ver el celular de los pacientes ─────────────────────────
-- Arranca en FALSE para TODOS los roles (incluido ADMIN) a propósito: nadie ve
-- teléfonos hasta que se active explícitamente en Seguridad > Roles.
ALTER TABLE cat_roles ADD COLUMN IF NOT EXISTS pacientes_ver_telefono BOOLEAN NOT NULL DEFAULT FALSE;

-- ── Pacientes: datos del apoderado (obligatorios solo si es menor de edad) ───
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS dni_apoderado     VARCHAR(20);
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS nombre_apoderado  VARCHAR(150);
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS celular_apoderado VARCHAR(20);

COMMIT;

-- ── Verificación ────────────────────────────────────────────────────────────
-- Debe devolver 10 filas.
SELECT table_name, column_name
FROM information_schema.columns
WHERE (table_name = 'citas'     AND column_name IN ('lote_masivo_id', 'lote_total_planeado'))
   OR (table_name = 'pagos'     AND column_name IN ('concepto', 'es_adicional', 'es_devolucion', 'pago_origen_id'))
   OR (table_name = 'cat_roles' AND column_name = 'pacientes_ver_telefono')
   OR (table_name = 'pacientes' AND column_name IN ('dni_apoderado', 'nombre_apoderado', 'celular_apoderado'))
ORDER BY table_name, column_name;
