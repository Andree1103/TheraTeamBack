-- Una sola anulación, con motivo escrito, en vez de dos estados de cancelación.
--
-- QUÉ CAMBIA: "Cancelada por paciente" y "Cancelada por clínica" eran dos ESTADOS del catálogo.
-- Anular es un estado; quién lo pidió es el motivo. Con dos estados, cada vez que apareciera un
-- caso nuevo ("error de registro", "el terapeuta faltó") tocaba crear otro estado y enseñárselo
-- al código, que compara claves a mano en nueve sitios. Ahora hay un estado, ANULADA, y un campo
-- de texto donde se escribe el porqué.
--
-- POR QUÉ NO SE BORRAN LOS ESTADOS VIEJOS: citas.estado_id y cita_historial (estado anterior y
-- nuevo) los referencian con FK. Se quedan en el catálogo marcados como inactivos —el endpoint
-- deja de ofrecerlos— para que cualquier fila que aún los apunte siga teniendo a qué apuntar.
--
-- POR QUÉ SE CONVIERTEN LAS CITAS YA ANULADAS: para que los informes no tengan que sumar tres
-- etiquetas distintas para decir lo mismo. La distinción no se pierde: se escribe en el motivo.
--
-- REPROGRAMAR, por lo mismo: era un estado que se elegía a mano en el desplegable y movía la
-- cita de sitio, sin dejar rastro de cuándo estaba antes ni por qué cambió. Ahora la cita
-- original se queda donde estaba, marcada REPROGRAMADA y con su motivo, y la nueva nace aparte
-- apuntando a ella por citas.reprogramacion_de (la columna ya existía, sin usarse).
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

-- 1. El motivo, en la propia cita. Va aquí y no solo en cita_historial porque es un dato que se
--    consulta con la cita ("¿por qué se cayó esta?"), no un paso de su bitácora.
--
--    Un solo campo para anular y para reprogramar: en los dos casos responde a la misma pregunta,
--    "¿por qué esta cita no se hizo como estaba?", y el estado ya dice cuál de los dos fue. Dos
--    columnas obligarían a mirar el estado para saber cuál leer, sin ganar nada.
ALTER TABLE citas ADD COLUMN IF NOT EXISTS motivo_estado VARCHAR(255);

-- Una versión anterior de esta migración creó la columna como motivo_anulacion, antes de que
-- reprogramar también pidiera motivo. Si alguna base la tiene, se conserva lo escrito.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'citas' AND column_name = 'motivo_anulacion') THEN
        UPDATE citas SET motivo_estado = motivo_anulacion
        WHERE motivo_estado IS NULL AND motivo_anulacion IS NOT NULL;
        ALTER TABLE citas DROP COLUMN motivo_anulacion;
    END IF;
END $$;

COMMENT ON COLUMN citas.motivo_estado IS
    'Por qué la cita no se hizo como estaba: motivo de la anulación o de la reprogramación.';

-- 2. El estado único.
INSERT INTO cat_estados_cita (key, nombre, color_hex, activo)
SELECT 'ANULADA', 'Anulada', '#DC2626', true
WHERE NOT EXISTS (SELECT 1 FROM cat_estados_cita WHERE key = 'ANULADA');

-- 3. Las citas ya canceladas pasan a ANULADA conservando en el motivo quién la pidió.
--    COALESCE: si alguna ya traía motivo (no debería, la columna acaba de nacer), se respeta.
UPDATE citas c
SET estado_id = (SELECT id FROM cat_estados_cita WHERE key = 'ANULADA'),
    motivo_estado = COALESCE(c.motivo_estado,
        CASE e.key
            WHEN 'CANCELADA_PACIENTE' THEN 'Cancelada por el paciente'
            WHEN 'CANCELADA_CLINICA'  THEN 'Cancelada por la clínica'
        END)
FROM cat_estados_cita e
WHERE e.id = c.estado_id
  AND e.key IN ('CANCELADA_PACIENTE', 'CANCELADA_CLINICA');

-- 4. El historial apunta al estado nuevo. Se reescribe la etiqueta, no el hecho: la cita se
--    canceló cuando se canceló, y el porqué quedó guardado en el paso 3.
UPDATE cita_historial h
SET estado_anterior_id = (SELECT id FROM cat_estados_cita WHERE key = 'ANULADA')
WHERE h.estado_anterior_id IN (SELECT id FROM cat_estados_cita
                               WHERE key IN ('CANCELADA_PACIENTE', 'CANCELADA_CLINICA'));

UPDATE cita_historial h
SET estado_nuevo_id = (SELECT id FROM cat_estados_cita WHERE key = 'ANULADA')
WHERE h.estado_nuevo_id IN (SELECT id FROM cat_estados_cita
                            WHERE key IN ('CANCELADA_PACIENTE', 'CANCELADA_CLINICA'));

-- 5. Los viejos dejan de ofrecerse. Se conservan las filas: ver la nota de arriba.
UPDATE cat_estados_cita SET activo = false
WHERE key IN ('CANCELADA_PACIENTE', 'CANCELADA_CLINICA');

COMMIT;
