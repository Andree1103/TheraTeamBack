-- Quita de los paquetes las sesiones que ya se devolvieron y seguian cobrandose.
--
-- QUE PASO
--
-- La deuda de un paquete se lee como «lo que vale menos lo cobrado», y lo que vale son sus
-- sesiones por el precio. Al anular una cita de un paquete y devolver el dinero, bajaba lo
-- cobrado y el valor se quedaba igual: el paquete pasaba a reclamar justo el importe que se
-- acababa de devolver. Al paciente se le entregaban S/ 50 y la ficha decia que debia S/ 50.
--
-- Pasaba por los tres caminos por los que el dinero de una sesion sale del paquete: anular
-- devolviendo en efectivo, anular dejandolo a favor, y la inasistencia con devolucion.
--
-- Esto ya NO vuelve a pasar: la sesion cuyo dinero sale del paquete sale con el (ver
-- CitaService.elPaqueteDejaDeCobrarEsaSesion). Este script es para los paquetes a los que les
-- paso antes del arreglo — el primero que lo destapo fue el de LIAM MAURICIO VILLAVICENCIO
-- DELGADO, el 4 de noviembre.
--
-- QUE HACE
--
-- Por cada paquete con sesiones devueltas, le baja el total de sesiones en tantas como se
-- devolvieron. No toca ni un sol: no mueve lo cobrado, no crea devoluciones, no reparte saldo.
-- Lo unico que cambia es cuantas sesiones dice cobrar el paquete, que es el dato que estaba
-- mintiendo.
--
-- Una sesion cuenta como devuelta si su cita tiene una devolucion grabada (el camino «devolver
-- el dinero») o un asiento de ANULACION que le libera la deuda (el camino «queda a favor», que
-- es tambien por donde pasa la inasistencia con devolucion).
--
-- NO BAJA de ninguno de estos cuatro pisos, y se queda en el mas alto:
--   * las sesiones atendidas — «pendientes» se calcula restando las dos y quedaria en negativo;
--   * las sesiones ocupadas por una cita a la que NO se le devolvio nada — si el hueco que dejo
--     la anulada se volvio a agendar, esa sesion si se va a dar y el paquete tiene que cobrarla;
--   * lo que ya se cobro (redondeado a sesiones enteras) — bajar de ahi diria que el paquete
--     cobro mas de lo que vale, y taparia una deuda real como si no existiera;
--   * una sesion menos por cada devolucion, que es el arreglo en si.
--
-- SE SALTA los paquetes CANCELADOS: esos ya los cerro la anulacion del paquete entero, que
-- ajusta el total y devuelve el sobrante (CitaService.cerrarPaqueteAnulado). Volver a bajarlos
-- seria contar dos veces.
--
-- Idempotente por el piso de lo cobrado: pasarlo dos veces no mueve nada la segunda. Primero
-- imprime lo que va a hacer y lo que se salta, luego lo hace, y al final vuelve a contar.

BEGIN;

CREATE TEMP TABLE _arreglo ON COMMIT DROP AS
WITH dinero_salido AS (
  -- Citas de paquete cuyo dinero ya salio del paquete, por cualquiera de los dos caminos.
  SELECT c.id AS cita_id, s.id AS sesion_id, s.tratamiento_id
    FROM citas c
    JOIN sesiones s ON s.id = c.sesion_id
   WHERE EXISTS (SELECT 1 FROM pagos p
                  WHERE p.cita_id = c.id AND p.es_devolucion)
      OR EXISTS (SELECT 1 FROM asiento_lineas l
                   JOIN asientos a ON a.id = l.asiento_id
                  WHERE l.cita_id = c.id
                    AND a.tipo = 'ANULACION'
                    AND l.concepto = 'DEUDA_LIBERA')
),
por_paquete AS (
  SELECT t.id,
         t.total_sesiones,
         t.sesiones_atendidas,
         t.precio_por_sesion,
         t.total_cobrado,
         (SELECT count(DISTINCT d.sesion_id) FROM dinero_salido d
           WHERE d.tratamiento_id = t.id)                                       AS devueltas,
         (SELECT count(*) FROM sesiones s
           WHERE s.tratamiento_id = t.id
             AND s.cita_activa_id IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM dinero_salido d
                              WHERE d.cita_id = s.cita_activa_id))              AS ocupadas_vivas,
         CASE WHEN COALESCE(t.precio_por_sesion, 0) > 0
              THEN ceil(t.total_cobrado / t.precio_por_sesion)::int
              ELSE t.total_sesiones END                                         AS piso_cobrado
    FROM tratamientos t
   WHERE t.estado_id NOT IN (SELECT id FROM cat_estados_tratamiento WHERE key = 'CANCELADO')
)
SELECT p.id, p.total_sesiones, p.sesiones_atendidas, p.precio_por_sesion, p.total_cobrado,
       p.devueltas, p.ocupadas_vivas, p.piso_cobrado,
       GREATEST(p.total_sesiones - p.devueltas,
                p.sesiones_atendidas,
                p.ocupadas_vivas,
                p.piso_cobrado)::int AS nuevo_total
  FROM por_paquete p
 WHERE p.devueltas > 0;

-- ── Lo que se va a corregir, antes de tocar nada ────────────────────────────
SELECT a.id AS paquete, pa.nombre || ' ' || pa.apellido AS paciente, t.nombre AS paquete_nombre,
       a.total_sesiones AS sesiones_antes, a.nuevo_total AS sesiones_despues, a.devueltas,
       a.total_cobrado,
       (a.total_sesiones * a.precio_por_sesion - a.total_cobrado) AS deuda_antes,
       (a.nuevo_total    * a.precio_por_sesion - a.total_cobrado) AS deuda_despues
  FROM _arreglo a
  JOIN tratamientos t ON t.id = a.id
  JOIN pacientes pa   ON pa.id = t.paciente_id
 WHERE a.nuevo_total < a.total_sesiones
 ORDER BY deuda_antes DESC, a.id;

-- ── Y lo que tiene devoluciones pero NO se toca, con el piso que lo frena ───
-- Se imprime para que se pueda revisar a mano: aca el total que dice el paquete ya cuadra con
-- alguno de los pisos, asi que bajarlo mentiria en el otro sentido.
SELECT a.id AS paquete, pa.nombre || ' ' || pa.apellido AS paciente,
       a.total_sesiones, a.devueltas,
       CASE WHEN a.piso_cobrado   >= a.total_sesiones THEN 'lo cobrado ya ocupa todas las sesiones'
            WHEN a.ocupadas_vivas >= a.total_sesiones THEN 'todas sus sesiones estan agendadas sin devolucion'
            WHEN a.sesiones_atendidas >= a.total_sesiones THEN 'todas sus sesiones estan atendidas'
            ELSE 'ya estaba ajustado' END AS por_que_no_se_toca
  FROM _arreglo a
  JOIN tratamientos t ON t.id = a.id
  JOIN pacientes pa   ON pa.id = t.paciente_id
 WHERE a.nuevo_total >= a.total_sesiones
 ORDER BY a.id;

-- ── Se corrige ──────────────────────────────────────────────────────────────
UPDATE tratamientos t
   SET total_sesiones = a.nuevo_total,
       updated_at     = now()
  FROM _arreglo a
 WHERE a.id = t.id
   AND a.nuevo_total < a.total_sesiones;

-- ── Como quedo ──────────────────────────────────────────────────────────────
SELECT count(*) FILTER (WHERE a.nuevo_total < a.total_sesiones)                  AS paquetes_corregidos,
       COALESCE(sum((a.total_sesiones - a.nuevo_total) * a.precio_por_sesion)
                FILTER (WHERE a.nuevo_total < a.total_sesiones), 0)              AS deuda_falsa_retirada,
       count(*) FILTER (WHERE a.nuevo_total >= a.total_sesiones)                 AS paquetes_sin_tocar
  FROM _arreglo a;

-- Y la comprobacion de que cada paquete quedo en el total que le toca. El script solo baja
-- totales, nunca los sube, de ahi el LEAST: tiene que salir en cero.
SELECT count(*) AS paquetes_que_no_quedaron_en_su_total
  FROM _arreglo a
  JOIN tratamientos t ON t.id = a.id
 WHERE t.total_sesiones <> LEAST(a.nuevo_total, a.total_sesiones);

COMMIT;
