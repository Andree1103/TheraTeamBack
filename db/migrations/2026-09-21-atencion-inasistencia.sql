-- La inasistencia se registra como una atención más, no como un hueco.
--
-- QUÉ CAMBIA: cuando un paciente no viene, hasta ahora lo único que quedaba era la cita en
-- "No asistió". Eso vive en la agenda, que se mira por semana; a los dos meses nadie sabe cuántas
-- inasistencias tuvo un paciente ni por qué. Ahora se crea una fila en atencion_clinica marcada
-- como INASISTENCIA y con su motivo, así aparece en Atenciones junto a las sesiones que sí se
-- dieron y se puede contar, filtrar y exportar igual que el resto.
--
-- POR QUÉ EN LA MISMA TABLA Y NO EN OTRA: la pregunta que se hace la clínica es "¿qué pasó con
-- esta cita?", y la respuesta es una sola por cita — de hecho atencion_clinica ya tiene UNIQUE
-- sobre cita_id. Una tabla aparte obligaría a consultar las dos y unirlas para cualquier listado.
--
-- LO QUE NO HACE: una inasistencia no cuenta como sesión atendida. No toca sesiones_atendidas del
-- paquete ni marca la sesión como ATENDIDA; si la clínica decide cobrarla igual, eso se resuelve
-- por el lado del pago, que es donde se decide el dinero.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE atencion_clinica
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'ATENDIDA';

ALTER TABLE atencion_clinica
    ADD COLUMN IF NOT EXISTS motivo VARCHAR(255);

-- El CHECK va aparte y con guardia: ADD CONSTRAINT no admite IF NOT EXISTS.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'atencion_clinica_tipo_check') THEN
        ALTER TABLE atencion_clinica
            ADD CONSTRAINT atencion_clinica_tipo_check
            CHECK (tipo IN ('ATENDIDA', 'INASISTENCIA'));
    END IF;
END $$;

COMMENT ON COLUMN atencion_clinica.tipo IS
    'ATENDIDA = la sesión se dio; INASISTENCIA = el paciente no vino.';
-- El destino del dinero va en columnas propias, no dentro del texto del motivo.
--
-- POR QUE: "con devolucion" escrito en una frase no se puede filtrar, ni sumar, ni sobrevive a
-- que alguien corrija la redaccion. Como flag se responde de una consulta cuantas inasistencias
-- se devolvieron y cuanto dinero es; el motivo vuelve a ser solo lo que dijo el paciente.
ALTER TABLE atencion_clinica
    ADD COLUMN IF NOT EXISTS con_devolucion BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE atencion_clinica
    ADD COLUMN IF NOT EXISTS monto_devuelto NUMERIC(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN atencion_clinica.motivo IS
    'Por qué no vino, tal como se anotó. Solo tiene valor cuando tipo = INASISTENCIA.';
COMMENT ON COLUMN atencion_clinica.con_devolucion IS
    'true = lo cobrado volvió al paciente como saldo a favor; false = se cobró igual.';
COMMENT ON COLUMN atencion_clinica.monto_devuelto IS
    'Cuánto volvió al paciente. 0 cuando no hubo devolución.';

-- Las filas que se registraron mientras el dato vivia en el texto: se rescata el flag y se
-- devuelve el motivo a lo que se escribio.
UPDATE atencion_clinica
SET con_devolucion = true
WHERE tipo = 'INASISTENCIA' AND con_devolucion = false AND motivo LIKE '%con devoluci%';

UPDATE atencion_clinica
SET motivo = btrim(split_part(motivo, ' · ', 1))
WHERE tipo = 'INASISTENCIA' AND motivo LIKE '%· %devoluci%';

-- La cita guarda una copia del motivo (es lo que muestra el listado de Atenciones): se limpia
-- igual, o seguiria enseñando la frase que ahora vive en las columnas.
UPDATE citas
SET motivo_estado = btrim(split_part(motivo_estado, ' · ', 1))
WHERE motivo_estado LIKE '%· %devoluci%';

-- Las filas que ya existían son todas sesiones dadas: el DEFAULT ya las dejó en ATENDIDA.

COMMIT;
