-- Estado de pago DESCONTADA: la cita se cobro y el paciente no vino, y la clinica se quedo
-- con el dinero como penalidad.
--
-- Hasta ahora esa cita quedaba en PAGADA, igual que una sesion que si se dio. En la ficha del
-- paciente se leia "No asistio / Pagada", que suena a error, y en cualquier recuento las dos
-- cosas se sumaban juntas: no habia forma de saber cuanto de lo cobrado fue trabajo hecho y
-- cuanto fue inasistencia descontada. El dinero no se mueve — sigue siendo ingreso de la
-- clinica — solo se nombra distinto.
--
-- Ambar como PARCIAL, no verde: es dinero cobrado, pero no por una sesion.

-- La secuencia de este catalogo quedo en 1 porque las tres filas iniciales se insertaron con
-- id explicito en el seed. Sin realinearla, cualquier INSERT futuro choca contra la PK.
SELECT setval(pg_get_serial_sequence('cat_estados_pago_cita', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM cat_estados_pago_cita), 1));

INSERT INTO cat_estados_pago_cita (key, nombre, color)
SELECT 'DESCONTADA', 'Descontada', '#d97706'
WHERE NOT EXISTS (SELECT 1 FROM cat_estados_pago_cita WHERE key = 'DESCONTADA');

-- Las inasistencias que ya existen y se quedaron con el dinero pasan al estado nuevo: la cita
-- esta en NO_ASISTIO y figura PAGADA. Se excluyen las que si se devolvieron, que son las que
-- tienen una atencion con con_devolucion = true.
--
-- No se exige que exista la atencion: las inasistencias anteriores a 2026-09-21 se registraban
-- con un estado manual ("INASISTENCIA SIN DESCUENTO") y la migracion que los retiro las paso a
-- NO_ASISTIO sin crear atencion. Son el mismo hecho y les toca el mismo estado de pago.
UPDATE citas c
   SET estado_pago_id = (SELECT id FROM cat_estados_pago_cita WHERE key = 'DESCONTADA')
  FROM cat_estados_cita e, cat_estados_pago_cita ep
 WHERE c.estado_id = e.id
   AND c.estado_pago_id = ep.id
   AND e.key = 'NO_ASISTIO'
   AND ep.key = 'PAGADA'
   AND c.eliminado = false
   AND NOT EXISTS (SELECT 1 FROM atencion_clinica ac
                    WHERE ac.cita_id = c.id AND COALESCE(ac.con_devolucion, false) = true);

SELECT ep.key, count(*) AS citas
  FROM citas c JOIN cat_estados_pago_cita ep ON ep.id = c.estado_pago_id
  JOIN cat_estados_cita e ON e.id = c.estado_id
 WHERE e.key = 'NO_ASISTIO' AND c.eliminado = false
 GROUP BY ep.key ORDER BY ep.key;
