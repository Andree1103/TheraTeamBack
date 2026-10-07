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

-- ── Segunda tanda (K..T): los caminos que la primera no miraba ──────────────────────────
-- Devoluciones por encima de lo cobrado, pagos duplicados, ventas de producto y su stock,
-- cobros adicionales sin concepto, reprogramaciones que dupliquen dinero, cierres de caja
-- contra lo que suman sus pagos, el reparto dentro de un paquete, adelantos sin movimiento
-- de saldo, y citas dadas por pagadas sin importe.
--
-- Ojo con L: dos pagos iguales a la misma cita no son necesariamente un duplicado — pueden
-- ser dos cuotas. Hay que mirar la hora y el medio antes de concluir.

\echo '=== K. Devoluciones que devuelven MAS de lo que se cobro por esa cita ==='
SELECT count(*) AS devoluciones_excesivas FROM (
  SELECT c.id,
    COALESCE(sum(CASE WHEN pg.es_devolucion THEN pg.monto_recibido ELSE 0 END),0) AS devuelto,
    COALESCE(sum(CASE WHEN NOT COALESCE(pg.es_devolucion,false) THEN pg.monto_recibido ELSE 0 END),0) AS cobrado
  FROM citas c JOIN pagos pg ON pg.cita_id=c.id GROUP BY c.id) x
WHERE devuelto > cobrado + 0.01;

\echo ''
\echo '=== L. Pagos duplicados: mismo paciente, misma cita, mismo importe, en menos de 1 min ==='
SELECT count(*) AS posibles_duplicados FROM (
  SELECT p.id, lag(p.id) OVER w AS anterior
  FROM pagos p WHERE p.cita_id IS NOT NULL AND NOT COALESCE(p.es_devolucion,false)
  WINDOW w AS (PARTITION BY p.paciente_id, p.cita_id, p.monto_recibido ORDER BY p.fecha_pago)
) x WHERE anterior IS NOT NULL;

\echo ''
\echo '=== M. Ventas de producto sin pago detras, o pagos de venta sin lineas ==='
SELECT (SELECT count(*) FROM venta_items vi WHERE NOT EXISTS (SELECT 1 FROM pagos p WHERE p.id=vi.pago_id)) AS lineas_sin_pago,
       (SELECT count(*) FROM pagos p WHERE COALESCE(p.es_adicional,false)
          AND EXISTS (SELECT 1 FROM venta_items vi WHERE vi.pago_id=p.id)
          AND p.monto_recibido <> COALESCE((SELECT sum(vi.subtotal) FROM venta_items vi WHERE vi.pago_id=p.id),0)) AS venta_con_importe_distinto;

\echo ''
\echo '=== N. Cobros adicionales sin concepto (no se sabe de que son) ==='
SELECT count(*) AS adicionales_sin_concepto FROM pagos
WHERE COALESCE(es_adicional,false) AND COALESCE(NULLIF(btrim(COALESCE(concepto,'')),''), NULLIF(btrim(COALESCE(notas,'')),'')) IS NULL;

\echo ''
\echo '=== O. Reprogramaciones: ¿quedo dinero en la cita vieja Y en la nueva? ==='
SELECT count(*) AS dinero_duplicado_al_reprogramar FROM citas vieja
JOIN citas nueva ON nueva.reprogramacion_de = vieja.id
WHERE COALESCE(vieja.monto_pagado,0) > 0 AND COALESCE(nueva.monto_pagado,0) > 0;

\echo ''
\echo '=== P. Cierres de caja: lo guardado vs lo que suman los pagos de ese turno ==='
SELECT count(*) AS cierres_descuadrados FROM cierres_caja cc
WHERE abs(COALESCE(cc.total_ingresos,0) - COALESCE((
  SELECT sum(CASE WHEN p.es_devolucion THEN -p.monto_recibido ELSE p.monto_recibido END)
  FROM pagos p LEFT JOIN cat_metodos_pago m ON m.id=p.metodo_id
  WHERE (m.cuenta_en_caja IS NULL OR m.cuenta_en_caja)
    AND p.fecha_pago >= (CASE WHEN cc.turno=1 THEN cc.fecha::timestamp ELSE cc.fecha + time '13:00' END)
    AND p.fecha_pago <  (CASE WHEN cc.turno=1 THEN cc.fecha + time '13:00' ELSE (cc.fecha+1)::timestamp END)),0)) > 0.01;

\echo ''
\echo '=== Q. Sesiones de paquete: lo repartido vs lo cobrado del paquete ==='
SELECT count(*) AS paquetes_con_reparto_descuadrado FROM tratamientos t
WHERE abs(COALESCE(t.total_cobrado,0) - COALESCE((
  SELECT sum(COALESCE(c.monto_pagado,0)) FROM sesiones s JOIN citas c ON c.sesion_id=s.id
  WHERE s.tratamiento_id=t.id AND c.eliminado=false),0)) > 0.01;

\echo ''
\echo '=== R. Adelantos (pagos sin cita ni paquete) sin movimiento de saldo que los acompañe ==='
SELECT count(*) AS adelantos_sin_movimiento FROM pagos p
WHERE p.cita_id IS NULL AND p.tratamiento_id IS NULL AND NOT COALESCE(p.es_devolucion,false)
  AND NOT COALESCE(p.es_adicional,false) AND p.monto_recibido > 0
  AND NOT EXISTS (SELECT 1 FROM saldo_movimientos m WHERE m.pago_id = p.id);

\echo ''
\echo '=== S. Citas PAGADA con precio 0 (cobradas sin importe) ==='
SELECT count(*) AS pagadas_sin_precio FROM citas c
JOIN cat_estados_pago_cita ep ON ep.id=c.estado_pago_id
WHERE ep.key='PAGADA' AND COALESCE(c.precio,0) <= 0 AND c.eliminado=false;

\echo ''
\echo '=== T. Stock negativo en productos ==='
SELECT count(*) AS productos_con_stock_negativo FROM productos WHERE COALESCE(stock,0) < 0;

\echo ''
\echo '=== U. Citas donde lo pagado supera al precio (precio bajado despues de cobrar) ==='
\echo '    No es fuga ni error: es dinero del paciente que deberia pasar a su saldo a favor.'
\echo '    No se pone como restriccion de la base porque rebajar un precio ya cobrado es legitimo.'
SELECT c.id AS cita, p.nombre || ' ' || p.apellido AS paciente, c.precio, c.monto_pagado,
       c.monto_pagado - c.precio AS de_mas
FROM citas c JOIN pacientes p ON p.id = c.paciente_id
JOIN cat_estados_cita e ON e.id = c.estado_id
WHERE c.eliminado = false
  AND COALESCE(c.monto_pagado,0) > COALESCE(c.precio,0)
  AND e.key NOT IN ('ANULADA','NO_ASISTIO','REPROGRAMADA')
ORDER BY 5 DESC;
