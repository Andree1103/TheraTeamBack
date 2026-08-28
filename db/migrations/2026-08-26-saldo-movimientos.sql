-- Historial de movimientos del saldo a favor.
--
-- Antes el saldo era un unico numero en pacientes.saldo_a_favor: no habia forma de saber
-- por que un paciente tenia S/ 120 a favor ni de que terapeuta venia. Cada suma o resta
-- deja ahora su propia fila con el motivo y, cuando aplica, la cita/pago que lo origino.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

CREATE TABLE IF NOT EXISTS saldo_movimientos (
    id                  BIGSERIAL PRIMARY KEY,
    paciente_id         BIGINT        NOT NULL REFERENCES pacientes(id),
    -- Con signo: positivo suma saldo, negativo lo consume.
    monto               NUMERIC(12,2) NOT NULL,
    -- Saldo del paciente despues de este movimiento, para poder auditar sin recalcular.
    saldo_resultante    NUMERIC(12,2) NOT NULL,
    motivo              VARCHAR(255)  NOT NULL,
    cita_id             BIGINT        REFERENCES citas(id),
    pago_id             BIGINT        REFERENCES pagos(id),
    -- Se copia de la cita al momento del movimiento: si despues cambian de terapeuta,
    -- el historico debe seguir diciendo quien atendia cuando se genero el saldo.
    terapeuta_id        BIGINT        REFERENCES terapeutas(id),
    fecha               TIMESTAMP     NOT NULL DEFAULT now(),
    usuario_creacion_id BIGINT
);

-- El listado de Adelantos pide el ultimo movimiento de cada paciente.
CREATE INDEX IF NOT EXISTS idx_saldo_mov_paciente_fecha
    ON saldo_movimientos (paciente_id, fecha DESC);

COMMIT;

-- Verificacion
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'saldo_movimientos' ORDER BY ordinal_position;
