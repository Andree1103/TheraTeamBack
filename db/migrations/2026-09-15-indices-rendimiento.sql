-- Índices que faltaban en las tablas que más se consultan.
--
-- POR QUÉ: `citas` y `pagos` solo tenían su PRIMARY KEY. Sin índice en pagos.cita_id, la
-- subconsulta que resuelve el método de pago del listado ("el último pago de esta cita")
-- obliga a Postgres a escanear la tabla ENTERA de pagos una vez por cada cita mostrada.
-- Medido sobre un volumen equivalente al de producción (7.000 pacientes, 42.000 citas,
-- 42.000 pagos), cargar la agenda de una semana pasó de 2.771 ms a 9 ms.
--
-- El efecto no se notaba con pocos datos y crece con el uso: es la explicación más probable
-- de que la aplicación se haya vuelto lenta después de la importación de pacientes.
--
-- Aditiva e idempotente: solo crea índices, no toca datos ni estructura.
-- CONCURRENTLY no se usa a propósito — exige estar fuera de transacción y estas tablas son
-- lo bastante chicas como para que el bloqueo dure menos de un segundo.

BEGIN;

-- ── pagos: el más importante ─────────────────────────────────────────────────
-- Lo usa la subconsulta del "último pago" en todas las proyecciones de cita.
CREATE INDEX IF NOT EXISTS ix_pagos_cita        ON pagos (cita_id);
CREATE INDEX IF NOT EXISTS ix_pagos_paciente    ON pagos (paciente_id);
-- Caja y reportes filtran por día.
CREATE INDEX IF NOT EXISTS ix_pagos_fecha       ON pagos (fecha_pago);
CREATE INDEX IF NOT EXISTS ix_pagos_tratamiento ON pagos (tratamiento_id);

-- ── citas: la agenda filtra por rango de fechas y por terapeuta ─────────────
CREATE INDEX IF NOT EXISTS ix_citas_fecha_inicio ON citas (fecha_inicio);
CREATE INDEX IF NOT EXISTS ix_citas_paciente     ON citas (paciente_id);
-- Compuesto: es exactamente como consulta la disponibilidad del terapeuta.
CREATE INDEX IF NOT EXISTS ix_citas_terapeuta_fecha ON citas (terapeuta_id, fecha_inicio);
CREATE INDEX IF NOT EXISTS ix_citas_estado       ON citas (estado_id);
CREATE INDEX IF NOT EXISTS ix_citas_sesion       ON citas (sesion_id);
CREATE INDEX IF NOT EXISTS ix_citas_lote         ON citas (lote_masivo_id);

-- ── Relaciones que se recorren en cada carga ────────────────────────────────
CREATE INDEX IF NOT EXISTS ix_sesiones_tratamiento   ON sesiones (tratamiento_id);
CREATE INDEX IF NOT EXISTS ix_sesiones_cita_activa   ON sesiones (cita_activa_id);
CREATE INDEX IF NOT EXISTS ix_tratamientos_paciente  ON tratamientos (paciente_id);
CREATE INDEX IF NOT EXISTS ix_cita_historial_cita    ON cita_historial (cita_id);
CREATE INDEX IF NOT EXISTS ix_saldo_mov_paciente     ON saldo_movimientos (paciente_id);
CREATE INDEX IF NOT EXISTS ix_pago_sesiones_pago     ON pago_sesiones (pago_id);

-- ── Búsqueda de pacientes ───────────────────────────────────────────────────
-- El buscador usa LIKE '%texto%' con LOWER(), que un índice normal no puede resolver.
-- pg_trgm sí: indexa trigramas y acelera la coincidencia parcial en medio del texto.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS ix_pacientes_nombre_trgm
    ON pacientes USING gin (LOWER(nombre || ' ' || apellido) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_pacientes_dni_trgm
    ON pacientes USING gin (LOWER(dni) gin_trgm_ops);
-- El Excel de pacientes filtra por fecha de alta.
CREATE INDEX IF NOT EXISTS ix_pacientes_created ON pacientes (created_at);

COMMIT;

-- Deja las estadísticas al día para que el planificador use los índices nuevos de inmediato.
ANALYZE pagos;
ANALYZE citas;
ANALYZE pacientes;
ANALYZE sesiones;
ANALYZE tratamientos;

-- Verificación
SELECT tablename, count(*) AS indices
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename IN ('citas','pagos','pacientes','sesiones','tratamientos','cita_historial')
 GROUP BY tablename
 ORDER BY tablename;
