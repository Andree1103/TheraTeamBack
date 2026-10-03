-- ╔══════════════════════════════════════════════════════════════════════════════════════╗
-- ║  CORRECCION DE DOS SALDOS A FAVOR INFLADOS POR SENDOS FALLOS YA CORREGIDOS            ║
-- ║  Esto NO es una migracion: se ejecuta a mano, una vez, revisando antes las cifras.    ║
-- ╚══════════════════════════════════════════════════════════════════════════════════════╝
--
-- Son dos pacientes y dos causas distintas. Las dos quedaron tapadas en el codigo; esto
-- solo arregla el dato que ya estaba mal.
--
-- 1) JOSHUA EYAL SANCHEZ HURTADO (4993) — saldo 60.00, deberia ser 30.00
--    Dejo S/ 30 de adelanto por Yape (movimiento 219). Ese dinero se uso en la cita 2482
--    (movimiento 222, -30). Al anularla, el sistema le devolvio 60 en vez de 30
--    (movimiento 223), porque el boton de saldo habia registrado 30 de efectivo que nunca
--    entro. Sobran 30.
--
-- 2) DANIEL ALEXANDER QUISPE SALVATIERRA (13550) — saldo 100.00, deberia ser 0.00
--    Sus tres pagos son del metodo "Sin pago": no entro dinero, solo se dejo constancia.
--    Al anular la cita 1923 y marcar inasistencia con devolucion en la 1925, el sistema
--    convirtio esa constancia en credito real: +50 y +50. Nunca hubo esos 100 soles.
--
-- El ajuste queda anotado en su estado de cuenta: un saldo que baja sin explicacion es
-- peor que el saldo mal.
--
-- ANTES DE EJECUTAR, HAZ EL BACKUP.

BEGIN;

\echo '=== ANTES ==='
SELECT id, nombre||' '||apellido AS paciente, saldo_a_favor
  FROM pacientes WHERE id IN (4993, 13550) ORDER BY id;

-- ── 1) JOSHUA: 60.00 -> 30.00 ───────────────────────────────────────────────────────────
INSERT INTO saldo_movimientos (paciente_id, monto, saldo_resultante, motivo, cita_id, fecha)
SELECT 4993, -30.00, 30.00,
       'Ajuste: la anulacion devolvio 30 de mas (efectivo que nunca entro)', 2482, now()
 WHERE (SELECT saldo_a_favor FROM pacientes WHERE id = 4993) = 60.00;

UPDATE pacientes SET saldo_a_favor = 30.00
 WHERE id = 4993 AND saldo_a_favor = 60.00;

-- ── 2) DANIEL: 100.00 -> 0.00 ───────────────────────────────────────────────────────────
INSERT INTO saldo_movimientos (paciente_id, monto, saldo_resultante, motivo, fecha)
SELECT 13550, -100.00, 0.00,
       'Ajuste: ese saldo venia de pagos con metodo "Sin pago" — no hubo dinero', now()
 WHERE (SELECT saldo_a_favor FROM pacientes WHERE id = 13550) = 100.00;

UPDATE pacientes SET saldo_a_favor = 0.00
 WHERE id = 13550 AND saldo_a_favor = 100.00;

\echo '=== DESPUES (esperado: 4993 -> 30.00, 13550 -> 0.00) ==='
SELECT id, nombre||' '||apellido AS paciente, saldo_a_favor
  FROM pacientes WHERE id IN (4993, 13550) ORDER BY id;

COMMIT;
