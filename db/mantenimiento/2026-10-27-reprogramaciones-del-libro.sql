-- Pone al dia el libro con las reprogramaciones que ya ocurrieron.
--
-- QUE PASO
--
-- Al reprogramar una cita, los pagos pasan a la cita nueva y la vieja queda en cero. El libro no
-- se enteraba: sus lineas seguian nombrando a la cita vieja. Resultado, por cada reprogramacion
-- cobrada el reconciliador cantaba DOS diferencias — la vieja "cobrada" sin dinero y la nueva con
-- dinero "sin cobrar".
--
-- No falta un sol. Lo que estaba mal es de quien se decia que era.
--
-- Esto ya NO vuelve a pasar: CitaService.reprogramar anota el movimiento en el momento (ver
-- LibroService.reprogramacion). Este script es solo para las que ocurrieron antes del arreglo.
--
-- QUE HACE
--
-- Por cada par (cita vieja REPROGRAMADA que el libro sigue dando por cobrada, cita nueva que la
-- sustituye), escribe UN asiento de CORRECCION con dos lineas: se libera la deuda de la vieja y
-- se cubre la de la nueva, por el mismo importe. Es el mismo asiento que escribe hoy el codigo.
--
-- NO reescribe ninguna linea existente. La atribucion se corrige como todo en este libro: con un
-- movimiento nuevo que retira de un lado y pone en el otro, para que el cambio quede contado en
-- vez de desaparecer.
--
-- SE SALTA SOLO lo que no le toca:
--   * las citas de paquete (el libro anota contra el tratamiento, no contra la cita);
--   * las reprogramaciones donde el libro ya dice cero (nada que mover);
--   * las que ya tienen su correccion escrita, asi que se puede reaplicar sin duplicar nada.
--
-- Idempotente. Primero imprime lo que va a hacer, luego lo hace, y al final vuelve a contar.

BEGIN;

-- ── Lo que se va a corregir, antes de tocar nada ────────────────────────────
WITH pares AS (
  SELECT vieja.id AS cita_vieja, nueva.id AS cita_nueva, vieja.paciente_id,
         (SELECT COALESCE(SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                                   WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe
                                   ELSE 0 END), 0)
            FROM asiento_lineas l WHERE l.cita_id = vieja.id) AS en_el_libro,
         COALESCE(nueva.monto_pagado, 0) AS en_la_cita_nueva
    FROM citas vieja
    JOIN cat_estados_cita ec ON ec.id = vieja.estado_id AND ec.key = 'REPROGRAMADA'
    JOIN citas nueva         ON nueva.reprogramacion_de = vieja.id AND nueva.eliminado = false
   WHERE vieja.sesion_id IS NULL
)
SELECT cita_vieja, cita_nueva, paciente_id, en_el_libro, en_la_cita_nueva
  FROM pares
 WHERE en_el_libro > 0.005
   AND NOT EXISTS (SELECT 1 FROM asientos a
                    WHERE a.tipo = 'CORRECCION'
                      AND a.nota = 'Reprogramación de la cita #' || cita_vieja
                                   || ' a la #' || cita_nueva || ' — al día con el libro')
 ORDER BY cita_vieja;

-- ── Y se corrige ────────────────────────────────────────────────────────────
DO $$
DECLARE
  r        RECORD;
  v_as     BIGINT;
  v_hechos INT := 0;
BEGIN
  FOR r IN
    WITH pares AS (
      SELECT vieja.id AS cita_vieja, nueva.id AS cita_nueva, vieja.paciente_id,
             (SELECT COALESCE(SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                                       WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe
                                       ELSE 0 END), 0)
                FROM asiento_lineas l WHERE l.cita_id = vieja.id) AS importe
        FROM citas vieja
        JOIN cat_estados_cita ec ON ec.id = vieja.estado_id AND ec.key = 'REPROGRAMADA'
        JOIN citas nueva         ON nueva.reprogramacion_de = vieja.id AND nueva.eliminado = false
       WHERE vieja.sesion_id IS NULL
    )
    SELECT * FROM pares
     WHERE importe > 0.005
       AND NOT EXISTS (SELECT 1 FROM asientos a
                        WHERE a.tipo = 'CORRECCION'
                          AND a.nota = 'Reprogramación de la cita #' || cita_vieja
                                       || ' a la #' || cita_nueva || ' — al día con el libro')
     ORDER BY cita_vieja
  LOOP
    INSERT INTO asientos (tipo, fecha, paciente_id, pago_id, usuario_id, nota, created_at)
    VALUES ('CORRECCION', NOW(), r.paciente_id, NULL, NULL,
            'Reprogramación de la cita #' || r.cita_vieja || ' a la #' || r.cita_nueva
            || ' — al día con el libro', NOW())
    RETURNING id INTO v_as;

    -- Las dos lineas cuadran por construccion: DEUDA_LIBERA esta a la izquierda de la ecuacion
    -- y DEUDA_CUBRE a la derecha, con el mismo importe.
    INSERT INTO asiento_lineas (asiento_id, concepto, importe, cita_id)
    VALUES (v_as, 'DEUDA_LIBERA', r.importe, r.cita_vieja),
           (v_as, 'DEUDA_CUBRE',  r.importe, r.cita_nueva);

    v_hechos := v_hechos + 1;
  END LOOP;

  RAISE NOTICE 'Reprogramaciones puestas al dia: %', v_hechos;
END $$;

COMMIT;

-- ── Verificacion: ninguna de las dos mitades deberia quedar descuadrada ──────
SELECT c.id AS cita, ec.key AS estado, COALESCE(c.monto_pagado,0) AS guardado,
       (SELECT COALESCE(SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                                 WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe
                                 ELSE 0 END), 0)
          FROM asiento_lineas l WHERE l.cita_id = c.id) AS segun_el_libro
  FROM citas c
  JOIN cat_estados_cita ec ON ec.id = c.estado_id
 WHERE c.eliminado = false AND c.sesion_id IS NULL
   AND (ec.key = 'REPROGRAMADA' OR c.reprogramacion_de IS NOT NULL)
   AND abs(COALESCE(c.monto_pagado,0) -
           (SELECT COALESCE(SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                                     WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe
                                     ELSE 0 END), 0)
              FROM asiento_lineas l WHERE l.cita_id = c.id)) > 0.005
 ORDER BY c.id;
