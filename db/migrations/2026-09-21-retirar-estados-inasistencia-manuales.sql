-- Retira los estados "INASISTENCIA CON/SIN DESCUENTO" creados a mano en el catálogo.
--
-- QUÉ SON: filas de cat_estados_cita añadidas desde Configuraciones en producción, antes de que
-- existiera el flujo de inasistencia. No las creó ninguna migración ni las conoce el código: para
-- el backend son estados cualesquiera, así que elegir uno solo cambiaba la etiqueta de la cita —
-- no registraba atención, no anotaba motivo y no movía un sol.
--
-- POR QUÉ SE RETIRAN: lo que decían ahora lo dicen los datos. Se marca "No asistió" y el sistema
-- pregunta si hubo devolución; la respuesta queda en atencion_clinica.con_devolucion y
-- monto_devuelto, que se pueden filtrar, sumar y exportar. Dos estados que solo se distinguían
-- por el texto de su nombre sobran, y mientras sigan en el desplegable se van a seguir eligiendo
-- en vez del flujo que sí hace el trabajo.
--
-- POR QUÉ NO SE BORRAN: citas.estado_id y cita_historial (estado anterior y nuevo) los apuntan por
-- FK. Se quedan en la tabla como inactivos — el endpoint de estados solo lista los activos, así
-- que dejan de ofrecerse — y cualquier fila que aún los referencie sigue teniendo a qué apuntar.
--
-- LO QUE NO HACE: NO mueve dinero. Una cita que hoy está en "con descuento" pasa a "No asistió"
-- con esa etiqueta escrita en el motivo, pero si correspondía una devolución que nunca se hizo,
-- este script no la inventa: no hay forma de saber desde la base si el dinero se devolvió de
-- verdad o solo se cambió la etiqueta. Esas citas hay que revisarlas a mano — la consulta del
-- final las lista.
--
-- SE IDENTIFICAN POR NOMBRE, no por key: se crearon desde la UI y su key depende de lo que se
-- tecleara. El patrón exige las dos palabras, así que no puede alcanzar a NO_ASISTIO ni a ningún
-- estado del catálogo original.
--
-- Idempotente, y no hace nada en una base donde esos estados no existan (p.ej. la local).

BEGIN;

-- Los ids afectados, una sola vez, para no repetir el LIKE en cada paso.
CREATE TEMP TABLE _estados_a_retirar ON COMMIT DROP AS
SELECT id,
       nombre,
       -- "con descuento" fue siempre la forma de decir "el dinero vuelve al paciente".
       (nombre ILIKE '%CON%DESCUENTO%' OR nombre ILIKE '%CON%DEVOLUCI%') AS con_devolucion
FROM cat_estados_cita
WHERE nombre ILIKE '%INASISTENCIA%'
  AND (nombre ILIKE '%DESCUENTO%' OR nombre ILIKE '%DEVOLUCI%');

-- 1. El motivo de la cita conserva la etiqueta que tenía, que es el único dato que aportaban.
--    Se respeta lo que ya hubiera escrito: el motivo a mano vale más que la etiqueta.
UPDATE citas c
SET motivo_estado = COALESCE(NULLIF(btrim(c.motivo_estado), ''), e.nombre)
FROM _estados_a_retirar e
WHERE c.estado_id = e.id;

-- 2. Donde ya hay atención registrada, se marca como inasistencia y se recoge el flag. Solo se
--    toca lo que sigue en el valor por defecto, para no pisar una corrección hecha después.
UPDATE atencion_clinica a
SET tipo = 'INASISTENCIA',
    motivo = COALESCE(NULLIF(btrim(a.motivo), ''), e.nombre),
    con_devolucion = a.con_devolucion OR e.con_devolucion
FROM citas c
JOIN _estados_a_retirar e ON e.id = c.estado_id
WHERE a.cita_id = c.id;

-- 3. Las citas pasan al estado real. Va después de los dos pasos anteriores, que se apoyan en
--    estado_id para saber cuáles eran.
UPDATE citas c
SET estado_id = (SELECT id FROM cat_estados_cita WHERE key = 'NO_ASISTIO')
WHERE c.estado_id IN (SELECT id FROM _estados_a_retirar);

-- 4. La bitácora apunta al mismo sitio, o el historial mostraría una etiqueta que ya no existe.
UPDATE cita_historial h
SET estado_anterior_id = (SELECT id FROM cat_estados_cita WHERE key = 'NO_ASISTIO')
WHERE h.estado_anterior_id IN (SELECT id FROM _estados_a_retirar);

UPDATE cita_historial h
SET estado_nuevo_id = (SELECT id FROM cat_estados_cita WHERE key = 'NO_ASISTIO')
WHERE h.estado_nuevo_id IN (SELECT id FROM _estados_a_retirar);

-- 5. Fuera del desplegable. La fila se queda por las FKs.
UPDATE cat_estados_cita
SET activo = false
WHERE id IN (SELECT id FROM _estados_a_retirar);

COMMIT;

-- Para revisar a mano después de aplicarla: las citas que venían de "con descuento" y sobre las
-- que habría que comprobar si la devolución llegó a hacerse.
--
--   SELECT c.id, c.fecha_inicio, c.motivo_estado, c.monto_pagado,
--          a.con_devolucion, a.monto_devuelto
--   FROM citas c
--   LEFT JOIN atencion_clinica a ON a.cita_id = c.id
--   WHERE c.motivo_estado ILIKE '%INASISTENCIA%'
--     AND (c.motivo_estado ILIKE '%CON%DESCUENTO%' OR c.motivo_estado ILIKE '%CON%DEVOLUCI%')
--   ORDER BY c.fecha_inicio DESC;
