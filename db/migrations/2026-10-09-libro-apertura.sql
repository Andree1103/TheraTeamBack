-- Asientos de apertura: el libro arranca diciendo lo mismo que el sistema.
--
-- El relleno trajo los pagos, pero el dinero tambien se movio por caminos que nunca pasaron por
-- la tabla pagos: anulaciones que devolvieron saldo, pagos eliminados, ajustes hechos a mano.
-- Esos hechos son anteriores al libro y no hay de donde reconstruirlos uno a uno.
--
-- Sin esto el reconciliador de la fase 2 nace mintiendo: cantaria 22 pacientes y 64 citas
-- descuadrados el primer dia, por historia que nadie va a corregir. Y un aviso que siempre esta
-- encendido es un aviso que nadie mira — se vuelve ruido y acaba tapando el fallo de verdad.
--
-- Asi que se hace lo que se hace en contabilidad cuando se abre un libro nuevo: un asiento de
-- apertura por cada saldo que no cuadra, con la diferencia exacta y dicho claramente que es
-- arrastre. A partir de aqui, cualquier diferencia que aparezca es un camino que se olvido de
-- anotar. Que es justo lo que queremos cazar.
--
-- La contrapartida es CONSTANCIA: ese valor existe en el sistema y no entro por el cajon hoy.
-- No se inventa dinero — se reconoce lo que ya estaba.

BEGIN;

-- ── Pacientes ────────────────────────────────────────────────────────────────
WITH proy AS (
  SELECT a.paciente_id,
         SUM(CASE WHEN l.concepto='CREDITO_GENERA' THEN l.importe
                  WHEN l.concepto='CREDITO_USA'    THEN -l.importe ELSE 0 END) AS libro
    FROM asientos a JOIN asiento_lineas l ON l.asiento_id = a.id GROUP BY 1),
dif AS (
  SELECT pa.id AS paciente_id,
         (COALESCE(pa.saldo_a_favor,0) - COALESCE(pr.libro,0))::numeric(12,2) AS d
    FROM pacientes pa LEFT JOIN proy pr ON pr.paciente_id = pa.id
   WHERE abs(COALESCE(pa.saldo_a_favor,0) - COALESCE(pr.libro,0)) > 0.005),
nuevos AS (
  INSERT INTO asientos (tipo, fecha, paciente_id, nota)
  SELECT 'AJUSTE', now(), paciente_id,
         'Apertura del libro: saldo arrastrado de movimientos anteriores'
    FROM dif RETURNING id, paciente_id)
INSERT INTO asiento_lineas (asiento_id, concepto, importe)
SELECT n.id, c.concepto, c.importe
  FROM nuevos n JOIN dif d ON d.paciente_id = n.paciente_id
  CROSS JOIN LATERAL (VALUES
      (CASE WHEN d.d > 0 THEN 'CONSTANCIA'        ELSE 'CREDITO_USA'       END, abs(d.d)),
      (CASE WHEN d.d > 0 THEN 'CREDITO_GENERA'    ELSE 'CONSTANCIA_LIBERA' END, abs(d.d))
  ) AS c(concepto, importe);

-- ── Citas ────────────────────────────────────────────────────────────────────
WITH proy AS (
  SELECT l.cita_id,
         SUM(CASE WHEN l.concepto='DEUDA_CUBRE' THEN l.importe
                  WHEN l.concepto='DEUDA_LIBERA' THEN -l.importe ELSE 0 END) AS libro
    FROM asiento_lineas l WHERE l.cita_id IS NOT NULL GROUP BY 1),
dif AS (
  SELECT c.id AS cita_id, c.paciente_id,
         (COALESCE(c.monto_pagado,0) - COALESCE(pr.libro,0))::numeric(12,2) AS d
    FROM citas c LEFT JOIN proy pr ON pr.cita_id = c.id
   WHERE c.eliminado = false AND c.paciente_id IS NOT NULL
     AND abs(COALESCE(c.monto_pagado,0) - COALESCE(pr.libro,0)) > 0.005),
nuevos AS (
  INSERT INTO asientos (tipo, fecha, paciente_id, nota)
  SELECT 'AJUSTE', now(), paciente_id,
         'Apertura del libro: cobrado arrastrado de la cita #' || cita_id
    FROM dif RETURNING id, paciente_id, nota)
INSERT INTO asiento_lineas (asiento_id, concepto, importe, cita_id)
SELECT n.id, c.concepto, c.importe, CASE WHEN c.lleva_cita THEN d.cita_id END
  FROM nuevos n
  JOIN dif d ON n.nota = 'Apertura del libro: cobrado arrastrado de la cita #' || d.cita_id
  CROSS JOIN LATERAL (VALUES
      (CASE WHEN d.d > 0 THEN 'CONSTANCIA'   ELSE 'DEUDA_LIBERA'      END, abs(d.d), d.d <= 0),
      (CASE WHEN d.d > 0 THEN 'DEUDA_CUBRE'  ELSE 'CONSTANCIA_LIBERA' END, abs(d.d), d.d > 0)
  ) AS c(concepto, importe, lleva_cita);

-- ── Paquetes ─────────────────────────────────────────────────────────────────
WITH proy AS (
  SELECT l.tratamiento_id,
         SUM(CASE WHEN l.concepto='DEUDA_CUBRE' THEN l.importe
                  WHEN l.concepto='DEUDA_LIBERA' THEN -l.importe ELSE 0 END) AS libro
    FROM asiento_lineas l WHERE l.tratamiento_id IS NOT NULL GROUP BY 1),
dif AS (
  SELECT t.id AS tratamiento_id, t.paciente_id,
         (COALESCE(t.total_cobrado,0) - COALESCE(pr.libro,0))::numeric(12,2) AS d
    FROM tratamientos t LEFT JOIN proy pr ON pr.tratamiento_id = t.id
   WHERE t.paciente_id IS NOT NULL
     AND abs(COALESCE(t.total_cobrado,0) - COALESCE(pr.libro,0)) > 0.005),
nuevos AS (
  INSERT INTO asientos (tipo, fecha, paciente_id, nota)
  SELECT 'AJUSTE', now(), paciente_id,
         'Apertura del libro: cobrado arrastrado del paquete #' || tratamiento_id
    FROM dif RETURNING id, nota)
INSERT INTO asiento_lineas (asiento_id, concepto, importe, tratamiento_id)
SELECT n.id, c.concepto, c.importe, CASE WHEN c.lleva THEN d.tratamiento_id END
  FROM nuevos n
  JOIN dif d ON n.nota = 'Apertura del libro: cobrado arrastrado del paquete #' || d.tratamiento_id
  CROSS JOIN LATERAL (VALUES
      (CASE WHEN d.d > 0 THEN 'CONSTANCIA'  ELSE 'DEUDA_LIBERA'      END, abs(d.d), d.d <= 0),
      (CASE WHEN d.d > 0 THEN 'DEUDA_CUBRE' ELSE 'CONSTANCIA_LIBERA' END, abs(d.d), d.d > 0)
  ) AS c(concepto, importe, lleva);

COMMIT;

SELECT count(*) AS asientos_de_apertura FROM asientos WHERE tipo='AJUSTE' AND nota LIKE 'Apertura del libro%';
