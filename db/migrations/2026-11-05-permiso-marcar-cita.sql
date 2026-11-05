-- El permiso del visto bueno pasa a ser el permiso de marcar que paso con la cita.
--
-- POR QUE: el visto bueno se pidio para asegurar si el paciente vino o no. Eso ahora lo dicen las
-- tres respuestas de la cita —asistio, no asistio y "nadie la marco"—, y la tercera cubre el caso
-- que el sello no sabia decir: que nadie llego a marcarla. El sello salio de las pantallas, pero
-- su permiso es justo el que les hacia falta a las tres: hasta ahora marcar iba con MODULO_CITAS
-- _CREAR, el mismo permiso de cargar una cita, asi que no habia forma de dejar que alguien
-- agendara sin dejarle ademas decir que paso.
--
-- La columna se RENOMBRA en vez de crear otra: es el mismo permiso con el nombre correcto, y
-- renombrarla conserva quien lo tenia. Dejar "puede_validar_cita" guardando "puede marcar" seria
-- una columna que dice una cosa y guarda otra.
--
-- NADIE PIERDE LO QUE YA PODIA HACER. Marcar iba con "crear citas", y hay roles que crean citas
-- sin tener el visto bueno —Recepcionista es el caso— que de otro modo se quedarian el lunes sin
-- poder marcar el dia. Asi que el permiso se le da a todo rol que hoy puede crear citas, y desde
-- ahi la clinica se lo quita a quien quiera en Seguridad > Roles.
--
-- Los sellos ya dados (citas.validada / validada_por / validada_en) NO se tocan: son historia de
-- quien aprobo que, y siguen en la tabla aunque la pantalla ya no los enseñe.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'cat_roles' AND column_name = 'puede_validar_cita')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'cat_roles' AND column_name = 'puede_marcar_cita') THEN
        ALTER TABLE cat_roles RENAME COLUMN puede_validar_cita TO puede_marcar_cita;
    END IF;
END $$;

-- Por si la base viniera de antes de la doble validacion.
ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_marcar_cita BOOLEAN NOT NULL DEFAULT false;

-- Quien hoy puede crear citas, hoy puede marcarlas: que el permiso cambie de nombre no es motivo
-- para que alguien se quede sin poder hacer su trabajo.
UPDATE cat_roles r
   SET puede_marcar_cita = true
 WHERE r.puede_marcar_cita = false
   AND EXISTS (SELECT 1
                 FROM rol_modulo_permisos p
                 JOIN modulos m ON m.id = p.modulo_id
                WHERE p.rol_id = r.id AND UPPER(m.key) = 'CITAS' AND p.crear);

COMMIT;

-- Verificacion: los roles que marcan, y que nadie que creaba citas se quedo fuera.
SELECT r.id, r.key, r.nombre, r.puede_marcar_cita,
       COALESCE(bool_or(UPPER(m.key) = 'CITAS' AND p.crear), false) AS crea_citas
  FROM cat_roles r
  LEFT JOIN rol_modulo_permisos p ON p.rol_id = r.id
  LEFT JOIN modulos m ON m.id = p.modulo_id
 GROUP BY r.id, r.key, r.nombre, r.puede_marcar_cita
 ORDER BY r.id;
