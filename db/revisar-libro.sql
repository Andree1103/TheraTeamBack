-- Salud del libro de movimientos. Solo lee; no cambia nada.
--
-- Pensado para lanzarse contra produccion sin riesgo, desde el directorio del repo:
--
--   ssh root@SERVIDOR "docker exec -i therateamback-db-1 psql -U postgres -d BDClinicaSAAS" < db/revisar-libro.sql
--
-- Se manda por la entrada estandar a proposito: asi las comillas del SQL no tienen que sobrevivir
-- al shell de quien lo lanza, que en PowerShell se las come.

\echo '== 1. Cobertura: cuantos pagos tienen su asiento'
SELECT (SELECT count(*) FROM pagos)    AS pagos,
       (SELECT count(*) FROM asientos) AS asientos,
       (SELECT count(*) FROM asiento_lineas) AS lineas,
       -- Las devoluciones nacidas de devolver() quedan fuera a proposito: su asiento es el
       -- REVERSO del cobro original y cuelga de ese pago, no de la fila de la devolucion. Un
       -- solo asiento cubre las dos mitades —lo que sale del cajon y lo que vuelve a su favor—
       -- y contarlas aqui daria un hueco que no existe.
       (SELECT count(*) FROM pagos p
         WHERE p.paciente_id IS NOT NULL
           AND NOT COALESCE(p.es_devolucion, false)
           AND NOT EXISTS (SELECT 1 FROM asientos a WHERE a.pago_id = p.id)) AS pagos_sin_asiento;

\echo ''
\echo '== 2. La ecuacion, en total'
SELECT sum(importe) FILTER (WHERE concepto IN ('CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA'))::numeric(12,2) AS izquierda,
       sum(importe) FILTER (WHERE concepto IN ('DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA'))::numeric(12,2) AS derecha
  FROM asiento_lineas;

\echo ''
\echo '== 3. Asientos descuadrados (debe salir vacio: la base no deja guardarlos)'
SELECT a.id, a.tipo, a.fecha::date, a.nota,
       (sum(l.importe) FILTER (WHERE l.concepto IN ('CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA'))
      - sum(l.importe) FILTER (WHERE l.concepto IN ('DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA')))::numeric(12,2) AS diferencia
  FROM asientos a JOIN asiento_lineas l ON l.asiento_id = a.id
 GROUP BY a.id, a.tipo, a.fecha, a.nota
HAVING abs(COALESCE(sum(l.importe) FILTER (WHERE l.concepto IN ('CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA')),0)
         - COALESCE(sum(l.importe) FILTER (WHERE l.concepto IN ('DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA')),0)) > 0.005;

\echo ''
\echo '== 4. Reparto por concepto'
SELECT concepto, count(*) AS lineas, sum(importe)::numeric(12,2) AS importe
  FROM asiento_lineas GROUP BY 1 ORDER BY 3 DESC;

\echo ''
\echo '== 5. Actividad del libro por dia (el relleno sale todo el mismo dia y a la misma hora)'
SELECT a.created_at::date AS dia, a.tipo, count(*) AS asientos
  FROM asientos a GROUP BY 1,2 ORDER BY 1 DESC, 2 LIMIT 12;

\echo ''

\echo ''
\echo '== 6. Devoluciones: toda devolucion tiene que estar cubierta, de una de las dos formas'
\echo '   (relleno historico: asiento DEVOLUCION propio · codigo vivo: ANULACION sobre el cobro original)'
SELECT count(*) AS devoluciones,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM asientos a WHERE a.pago_id = p.id)) AS con_asiento_propio,
       count(*) FILTER (WHERE EXISTS (
           SELECT 1 FROM asientos a WHERE a.tipo = 'ANULACION'
             AND a.pago_id = (substring(COALESCE(p.concepto,'') from '#([0-9]+)'))::bigint)) AS con_reverso,
       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM asientos a WHERE a.pago_id = p.id)
                          AND NOT EXISTS (SELECT 1 FROM asientos a WHERE a.tipo = 'ANULACION'
                              AND a.pago_id = (substring(COALESCE(p.concepto,'') from '#([0-9]+)'))::bigint)) AS SIN_CUBRIR
  FROM pagos p WHERE COALESCE(p.es_devolucion,false);
