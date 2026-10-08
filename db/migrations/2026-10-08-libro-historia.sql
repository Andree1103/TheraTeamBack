-- Pasa al libro los pagos que ya existen.
--
-- Cada pago guarda lo suficiente para reconstruir su asiento sin adivinar nada:
--
--   caja_entra     = lo recibido, si el método era dinero de verdad
--   constancia     = lo recibido, si NO lo era ("Sin pago", "Paquete") — deuda saldada sin plata
--   credito_usa    = saldo_previo - saldo_generado, cuando bajó
--   credito_genera = saldo_generado - saldo_previo, cuando subió
--   deuda_cubre    = lo aplicado
--
-- Las devoluciones son el único caso que no se reconstruye desde su propia fila, y eso es
-- exactamente el defecto que el libro viene a corregir: hoy la devolución anota que salió
-- dinero, pero el efecto sobre la deuda lo aplica otro código en otro sitio, así que mirando la
-- fila sola no cuadra. Su contrapartida se saca del pago de origen, que sí dice qué deuda había
-- cubierto. Las 25 de producción tienen pago_origen_id, así que salen todas.
--
-- Comprobado en seco contra el volcado antes de escribir esto: de 745 pagos, 726 cuadraban con
-- los cuatro conceptos simples y los 19 restantes eran justamente devoluciones.

-- Todo en una sola transacción, a propósito.
--
-- El trigger que comprueba la ecuación es DEFERRABLE INITIALLY DEFERRED: espera al COMMIT. Sin
-- el BEGIN, psql confirma cada INSERT por su cuenta y el asiento se evalúa cuando solo tiene la
-- mitad de sus líneas — salta "no cuadra" por un asiento que estaba a medio escribir.
BEGIN;

INSERT INTO asientos (tipo, fecha, paciente_id, pago_id, usuario_id, nota)
SELECT CASE WHEN COALESCE(p.es_devolucion,false) THEN 'DEVOLUCION' ELSE 'COBRO' END,
       p.fecha_pago, p.paciente_id, p.id, p.idusuario_creacion,
       'Traído de pagos #' || p.id
  FROM pagos p
 WHERE p.paciente_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM asientos a WHERE a.pago_id = p.id);

-- ── Cobros ────────────────────────────────────────────────────────────────────

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CAJA_ENTRA', p.monto_recibido, p.metodo_id, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'COBRO' AND COALESCE(p.trajo_dinero,false) AND COALESCE(p.monto_recibido,0) > 0;

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CONSTANCIA', p.monto_recibido, NULL, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'COBRO' AND NOT COALESCE(p.trajo_dinero,false) AND COALESCE(p.monto_recibido,0) > 0;

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CREDITO_USA', COALESCE(p.saldo_previo,0) - COALESCE(p.saldo_generado,0), NULL, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'COBRO' AND COALESCE(p.saldo_previo,0) - COALESCE(p.saldo_generado,0) > 0;

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CREDITO_GENERA', COALESCE(p.saldo_generado,0) - COALESCE(p.saldo_previo,0), NULL, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'COBRO' AND COALESCE(p.saldo_generado,0) - COALESCE(p.saldo_previo,0) > 0;

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'DEUDA_CUBRE', p.monto_aplicado, NULL, p.cita_id, p.tratamiento_id
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'COBRO' AND COALESCE(p.monto_aplicado,0) > 0;

-- ── Devoluciones ──────────────────────────────────────────────────────────────
-- Sale dinero (o se reconoce crédito) y, al otro lado, se libera la deuda que el pago original
-- había cubierto. De dónde salía ese dinero lo dice el pago de origen: si había entrado en
-- efectivo, se libera deuda; si se había pagado con constancia, se libera constancia.

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CAJA_SALE', p.monto_recibido, p.metodo_id, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'DEVOLUCION' AND COALESCE(p.monto_recibido,0) > 0;

INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id,
       CASE WHEN COALESCE(o.trajo_dinero,false) THEN 'DEUDA_LIBERA' ELSE 'CONSTANCIA' END,
       p.monto_recibido, NULL,
       CASE WHEN COALESCE(o.trajo_dinero,false) THEN COALESCE(p.cita_id, o.cita_id) END,
       CASE WHEN COALESCE(o.trajo_dinero,false) THEN COALESCE(p.tratamiento_id, o.tratamiento_id) END
  FROM asientos a
  JOIN pagos p ON p.id = a.pago_id
  JOIN pagos o ON o.id = p.pago_origen_id
 WHERE a.tipo = 'DEVOLUCION' AND COALESCE(p.monto_recibido,0) > 0;

-- Una devolución sin pago de origen (ninguna hoy, pero el futuro existe) queda sin
-- contrapartida y el trigger la rechazaría al confirmar. Se le pone CONSTANCIA: se sabe que
-- salió el dinero, no de qué deuda venía. Queda anotado para que nadie lo lea como un hecho.
INSERT INTO asiento_lineas (asiento_id, concepto, importe, metodo_id, cita_id, tratamiento_id)
SELECT a.id, 'CONSTANCIA', p.monto_recibido, NULL, NULL, NULL
  FROM asientos a JOIN pagos p ON p.id = a.pago_id
 WHERE a.tipo = 'DEVOLUCION' AND COALESCE(p.monto_recibido,0) > 0 AND p.pago_origen_id IS NULL;

UPDATE asientos a SET nota = a.nota || ' — sin pago de origen: no consta de qué deuda salió'
  FROM pagos p WHERE p.id = a.pago_id AND a.tipo = 'DEVOLUCION' AND p.pago_origen_id IS NULL;

COMMIT;

-- ── Qué salió ─────────────────────────────────────────────────────────────────

SELECT a.tipo, count(DISTINCT a.id) AS asientos, count(l.id) AS lineas
  FROM asientos a LEFT JOIN asiento_lineas l ON l.asiento_id = a.id
 GROUP BY a.tipo ORDER BY 1;

SELECT l.concepto, count(*) AS lineas, sum(l.importe)::numeric(12,2) AS importe
  FROM asiento_lineas l GROUP BY 1 ORDER BY 3 DESC;
