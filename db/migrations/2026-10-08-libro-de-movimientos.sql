-- El libro de movimientos: un solo sitio donde consta qué pasó con cada sol.
--
-- POR QUÉ
--
-- Hoy la misma cifra vive en cinco sitios —pagos, pacientes.saldo_a_favor, citas.monto_pagado,
-- citas.estado_pago_id y tratamientos.total_cobrado— y cada camino que mueve dinero tiene que
-- acordarse de actualizar los cinco. Cuando uno se olvida no pasa nada: la diferencia se queda
-- callada hasta que alguien cuadra la caja semanas después. Toda la semana ha ido en eso.
--
-- Aquí los cinco dejan de ser datos y pasan a ser sumas. Nadie los mantiene; se calculan. Un
-- camino nuevo no puede olvidarse de nada, porque no hay nada que actualizar: solo escribe su
-- asiento.
--
-- LA ECUACIÓN
--
--   caja_entra + credito_usa + constancia + deuda_libera
--                           =
--        deuda_cubre + credito_genera + caja_sale + constancia_libera
--
-- Izquierda: de dónde sale el valor. Derecha: a dónde va. Un asiento que no cuadra se rechaza.
--
-- Esto no es decorativo. El fallo que costó la semana —devolver un cobro de 235 hecho con 45 en
-- efectivo y 190 de saldo— tenía una sola respuesta correcta y la escribimos mal dos veces.
-- Con la ecuación no hay nada que razonar:
--
--   deuda_libera 235 = caja_sale 45 + credito_genera 190
--
-- y "saco 235 del cajón y además le devuelvo 190" sencillamente no se puede guardar.
--
-- FASE 1
--
-- Por ahora esto solo se ESCRIBE. Nadie lee de aquí. Los caminos actuales siguen funcionando
-- igual y además dejan su asiento; un reconciliador comparará ambos mundos en producción, con
-- datos reales, antes de que nada dependa del libro. Por eso `pago_id` existe: durante la
-- convivencia, cada asiento sabe de qué pago salió.

CREATE TABLE asientos (
  id          BIGSERIAL PRIMARY KEY,
  tipo        TEXT        NOT NULL,
  fecha       TIMESTAMP   NOT NULL,
  paciente_id BIGINT      NOT NULL REFERENCES pacientes(id),
  pago_id     BIGINT      REFERENCES pagos(id) ON DELETE CASCADE,
  usuario_id  BIGINT,
  nota        TEXT,
  created_at  TIMESTAMP   NOT NULL DEFAULT now(),
  CONSTRAINT ck_asientos_tipo CHECK (tipo IN ('COBRO','DEVOLUCION','ANULACION','AJUSTE'))
);

CREATE TABLE asiento_lineas (
  id             BIGSERIAL PRIMARY KEY,
  asiento_id     BIGINT        NOT NULL REFERENCES asientos(id) ON DELETE CASCADE,
  concepto       TEXT          NOT NULL,
  importe        NUMERIC(12,2) NOT NULL,
  metodo_id      BIGINT        REFERENCES cat_metodos_pago(id),
  cita_id        BIGINT        REFERENCES citas(id),
  tratamiento_id BIGINT        REFERENCES tratamientos(id),
  CONSTRAINT ck_lineas_concepto CHECK (concepto IN (
      'CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA',
      'DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA')),
  -- Un importe de cero no es un hecho: es una línea que no hacía falta escribir. Y negativo
  -- tampoco: el sentido lo da el concepto, no el signo — mezclarlos es como se acaba sumando
  -- lo que había que restar.
  CONSTRAINT ck_lineas_importe_positivo CHECK (importe > 0),
  -- El medio solo significa algo cuando hay cajón de por medio.
  CONSTRAINT ck_lineas_metodo_solo_en_caja CHECK (
      metodo_id IS NULL OR concepto IN ('CAJA_ENTRA','CAJA_SALE'))
);

CREATE INDEX ix_asientos_paciente       ON asientos (paciente_id, fecha);
CREATE INDEX ix_asientos_fecha          ON asientos (fecha);
CREATE INDEX ix_asientos_pago           ON asientos (pago_id);
CREATE INDEX ix_lineas_asiento          ON asiento_lineas (asiento_id);
CREATE INDEX ix_lineas_cita             ON asiento_lineas (cita_id)        WHERE cita_id IS NOT NULL;
CREATE INDEX ix_lineas_tratamiento      ON asiento_lineas (tratamiento_id) WHERE tratamiento_id IS NOT NULL;
CREATE INDEX ix_lineas_concepto         ON asiento_lineas (concepto);

-- La ecuación, como restricción de verdad.
--
-- Va en un trigger y no en un CHECK porque abarca varias filas. Es DEFERRABLE INITIALLY
-- DEFERRED a propósito: durante la transacción el asiento está a medio escribir y descuadra;
-- lo que no puede es quedar así al confirmar.
CREATE OR REPLACE FUNCTION asiento_cuadra() RETURNS TRIGGER AS $$
DECLARE
  v_asiento BIGINT := COALESCE(NEW.asiento_id, OLD.asiento_id);
  v_izq NUMERIC(12,2);
  v_der NUMERIC(12,2);
BEGIN
  SELECT COALESCE(sum(importe) FILTER (WHERE concepto IN ('CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA')), 0),
         COALESCE(sum(importe) FILTER (WHERE concepto IN ('DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA')), 0)
    INTO v_izq, v_der
    FROM asiento_lineas WHERE asiento_id = v_asiento;

  -- Un asiento sin líneas es un asiento borrado: no hay nada que comprobar.
  IF v_izq = 0 AND v_der = 0 THEN RETURN NULL; END IF;

  IF abs(v_izq - v_der) > 0.005 THEN
    RAISE EXCEPTION 'El asiento % no cuadra: de dónde sale S/ %, a dónde va S/ % (diferencia S/ %)',
      v_asiento, v_izq, v_der, (v_izq - v_der);
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tg_asiento_cuadra
  AFTER INSERT OR UPDATE OR DELETE ON asiento_lineas
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION asiento_cuadra();

SELECT 'libro creado' AS resultado;
