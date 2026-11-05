-- La marca de asistencia: el dato que reemplaza al visto bueno en la ficha de la cita.
--
-- QUE ES: una marca de auditoria con tres valores —ASISTIO, NO_ASISTIO, NO_MARCO— que alguien
-- pone a mano sobre la cita. NO HACE NADA MAS. No cambia el estado de la cita, no mueve dinero,
-- no toca las sesiones del paquete y no dispara ningun flujo: registrar la atencion, anotar la
-- inasistencia y anular siguen siendo lo que eran y por donde eran. Esto solo guarda que se
-- marco, quien lo marco y cuando, para los reportes.
--
-- POR QUE: el visto bueno era un si/no, y se habia pedido justamente para dejar constancia de si
-- el paciente vino. Con dos valores no se podia anotar el tercer caso, que es el que mas se da:
-- que nadie llego a marcarla. La marca es el mismo sello con los tres valores que hacian falta.
--
-- Columnas nuevas, no renombradas. validada / validada_por / validada_en se quedan como estan:
-- son los sellos que ya se dieron y su significado era otro —"reviso los datos de la cita"—, asi
-- que convertirlos a ASISTIO seria inventar un dato que nadie afirmo. Si la clinica decide que
-- esos sellos contaban como asistio, es un UPDATE de una linea y aqui esta escrito el porque de
-- no haberlo hecho solo.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE citas
    ADD COLUMN IF NOT EXISTS marca      VARCHAR(12),
    ADD COLUMN IF NOT EXISTS marca_por  BIGINT,
    ADD COLUMN IF NOT EXISTS marca_en   TIMESTAMP;

-- Tres valores y nada mas. Sin el CHECK, un dia entra "ASISTIÓ" con tilde desde cualquier sitio
-- y el reporte deja de cuadrar sin que nadie lo note.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'citas_marca_check') THEN
        ALTER TABLE citas
            ADD CONSTRAINT citas_marca_check
            CHECK (marca IS NULL OR marca IN ('ASISTIO', 'NO_ASISTIO', 'NO_MARCO'));
    END IF;
END $$;

-- Si se borra el usuario, la cita no se va con el: queda marcada y sin nombre, que es mejor que
-- perder la cita o perder la marca entera. Mismo criterio que tenia el visto bueno.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'citas_marca_por_fkey') THEN
        ALTER TABLE citas
            ADD CONSTRAINT citas_marca_por_fkey
            FOREIGN KEY (marca_por) REFERENCES usuarios(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Para los reportes, que es para lo unico que existe esto: filtrar por marca sin barrer la tabla.
CREATE INDEX IF NOT EXISTS idx_citas_marca ON citas (marca) WHERE marca IS NOT NULL;

COMMIT;

-- Verificacion
SELECT marca, count(*) FROM citas GROUP BY marca ORDER BY 2 DESC;
