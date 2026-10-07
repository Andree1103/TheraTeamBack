-- ╔══════════════════════════════════════════════════════════════════════════════════════╗
-- ║  AUDITORIA DEL DINERO — busca incoherencias en una base real (solo LEE, no cambia)    ║
-- ╚══════════════════════════════════════════════════════════════════════════════════════╝
--
-- Comprobaciones A..J y, al final, la unica que de verdad dice si hay fuga: dinero que ENTRO
-- de verdad (por un metodo marcado como caja), salio de una cita al anularla o al marcarla
-- inasistencia, y no aparece ni en el saldo del paciente ni en una devolucion registrada.
--
-- Lo normal es que D de un numero distinto de cero: una cita revertida se queda en SIN_PAGO y
-- su pago sigue ahi como historia. Eso NO es fuga — la prueba de fuga es la del final.
--
-- Contra produccion:
--   docker compose exec -T db sh -c 'psql -U postgres -d "$POSTGRES_DB"' < db/auditoria-dinero.sql

\echo '=== A. El saldo del paciente vs su ultimo movimiento (deben coincidir) ==='
WITH ult AS (
  SELECT DISTINCT ON (paciente_id) paciente_id, saldo_resultante
  FROM saldo_movimientos ORDER BY paciente_id, id DESC)
SELECT count(*) AS pacientes_descuadrados
FROM pacientes p JOIN ult u ON u.paciente_id = p.id
WHERE COALESCE(p.saldo_a_favor,0) <> u.saldo_resultante;

\echo ''
\echo '=== B. Saldos negativos (nunca deberia haber) ==='
SELECT count(*) AS saldos_negativos FROM pacientes WHERE COALESCE(saldo_a_favor,0) < 0;

\echo ''
\echo '=== C. Saldo sin ningun movimiento que lo respalde ==='
SELECT count(*) AS saldo_sin_historial FROM pacientes p
WHERE COALESCE(p.saldo_a_favor,0) > 0
  AND NOT EXISTS (SELECT 1 FROM saldo_movimientos m WHERE m.paciente_id = p.id);

\echo ''
\echo '=== D. Lo pagado de la cita vs lo aplicado por sus pagos ==='
SELECT count(*) AS citas_descuadradas FROM (
  SELECT c.id, c.monto_pagado,
         COALESCE((SELECT sum(CASE WHEN pg.es_devolucion THEN -pg.monto_aplicado ELSE pg.monto_aplicado END)
                   FROM pagos pg WHERE pg.cita_id = c.id AND NOT COALESCE(pg.es_adicional,false)),0) AS aplicado
  FROM citas c WHERE c.eliminado = false AND c.sesion_id IS NULL) x
WHERE abs(COALESCE(monto_pagado,0) - aplicado) > 0.01;

\echo ''
\echo '=== E. Estado de pago que no concuerda con las cifras ==='
SELECT ep.key AS dice, count(*) AS citas FROM citas c
JOIN cat_estados_pago_cita ep ON ep.id = c.estado_pago_id
WHERE c.eliminado = false AND (
      (ep.key = 'PAGADA'   AND COALESCE(c.monto_pagado,0) < COALESCE(c.precio,0))
   OR (ep.key = 'SIN_PAGO' AND COALESCE(c.monto_pagado,0) > 0)
   OR (ep.key = 'PARCIAL'  AND (COALESCE(c.monto_pagado,0) <= 0 OR COALESCE(c.monto_pagado,0) >= COALESCE(c.precio,0))))
GROUP BY ep.key ORDER BY 1;

\echo ''
\echo '=== F. Pagos que aplicaron mas dinero del que tenian disponible ==='
SELECT count(*) AS pagos_imposibles FROM pagos
WHERE NOT COALESCE(es_devolucion,false) AND NOT COALESCE(es_adicional,false)
  AND monto_aplicado > COALESCE(monto_recibido,0) + COALESCE(saldo_previo,0) + 0.01;

\echo ''
\echo '=== G. Lo cobrado del paquete vs la suma de sus pagos ==='
SELECT count(*) AS paquetes_descuadrados FROM tratamientos t
WHERE abs(COALESCE(t.total_cobrado,0) - COALESCE(
      (SELECT sum(CASE WHEN p.es_devolucion THEN -p.monto_aplicado ELSE p.monto_aplicado END)
       FROM pagos p WHERE p.tratamiento_id = t.id),0)) > 0.01;

\echo ''
\echo '=== H. Citas huerfanas: con sesion de un paquete que ya no existe ==='
SELECT count(*) AS citas_huerfanas FROM citas c
WHERE c.sesion_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sesiones s WHERE s.id = c.sesion_id);

\echo ''
\echo '=== I. Pagos apuntando a citas borradas ==='
SELECT count(*) AS pagos_de_citas_borradas FROM pagos p
JOIN citas c ON c.id = p.cita_id WHERE c.eliminado = true;

\echo ''
\echo '=== J. Inasistencias cobradas que no quedaron DESCONTADA ==='
SELECT count(*) AS inasistencias_mal_marcadas FROM citas c
JOIN cat_estados_cita e ON e.id = c.estado_id
JOIN cat_estados_pago_cita ep ON ep.id = c.estado_pago_id
WHERE e.key = 'NO_ASISTIO' AND ep.key = 'PAGADA' AND c.eliminado = false
  AND NOT EXISTS (SELECT 1 FROM atencion_clinica ac WHERE ac.cita_id=c.id AND COALESCE(ac.con_devolucion,false));
\echo '=== FUGA REAL: dinero que SI entro (metodo de caja), salio de la cita, y no aterrizo ==='
WITH revertidas AS (
  SELECT c.id, c.paciente_id, e.key AS estado,
         COALESCE((SELECT sum(CASE WHEN pg.es_devolucion THEN -pg.monto_aplicado ELSE pg.monto_aplicado END)
                   FROM pagos pg WHERE pg.cita_id=c.id AND NOT COALESCE(pg.es_adicional,false)),0)
         - COALESCE(c.monto_pagado,0) AS salio,
         -- solo cuenta el dinero que de verdad entro: metodos marcados como caja
         COALESCE((SELECT sum(pg.monto_recibido) FROM pagos pg
                    JOIN cat_metodos_pago mm ON mm.id = pg.metodo_id
                   WHERE pg.cita_id=c.id AND NOT COALESCE(pg.es_devolucion,false)
                     AND NOT COALESCE(pg.es_adicional,false) AND mm.cuenta_en_caja),0) AS entro_de_verdad
  FROM citas c JOIN cat_estados_cita e ON e.id=c.estado_id
  WHERE c.eliminado=false AND c.sesion_id IS NULL AND e.key IN ('ANULADA','NO_ASISTIO'))
SELECT count(*) AS citas_con_fuga,
       COALESCE(sum(LEAST(r.salio, r.entro_de_verdad)),0) AS soles_sin_rastro
FROM revertidas r
WHERE r.salio > 0.01 AND r.entro_de_verdad > 0.01
  AND NOT EXISTS (SELECT 1 FROM saldo_movimientos m WHERE m.cita_id=r.id AND m.monto>0)
  AND NOT EXISTS (SELECT 1 FROM pagos pg WHERE pg.cita_id=r.id AND pg.es_devolucion);

\echo ''
\echo '=== CUADRE GLOBAL: todo el dinero real que entro, contra donde esta ==='
SELECT
  (SELECT COALESCE(sum(CASE WHEN p.es_devolucion THEN -p.monto_recibido ELSE p.monto_recibido END),0)
     FROM pagos p JOIN cat_metodos_pago m ON m.id=p.metodo_id WHERE m.cuenta_en_caja) AS entro_a_caja,
  (SELECT COALESCE(sum(saldo_a_favor),0) FROM pacientes) AS en_saldos_a_favor,
  (SELECT COALESCE(sum(monto_pagado),0) FROM citas WHERE eliminado=false) AS cubriendo_citas,
  (SELECT COALESCE(sum(total_cobrado),0) FROM tratamientos) AS cubriendo_paquetes;
