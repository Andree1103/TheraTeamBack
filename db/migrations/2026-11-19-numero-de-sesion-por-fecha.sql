-- El N° de sesión de un paquete pasa a seguir al calendario.
--
-- QUE ES: renumera las sesiones de cada paquete de 1 a N en el orden en que van a ocurrir — por
-- la fecha de su cita, y las que todavía no tienen cita al final, en el orden en que se crearon.
--
-- POR QUE: el número se escribía una sola vez, al crear la sesión, y no se volvía a tocar. En
-- cuanto un paquete se modificaba —una cita reprogramada, una sesión creada después, citas
-- sueltas absorbidas al abrirlo— la pantalla del paquete quedaba ordenada por fecha pero
-- numerada por antigüedad: 1, 2, 5, 3, 4. El número es como se llama a la sesión en la cita
-- ("sesión 3 de 5") y en el historial, así que leer que la quinta sesión va tercera no lo
-- entiende nadie.
--
-- De aquí en adelante lo mantiene el backend: SesionRepository#renumerarPorFechaDeCita corre al
-- crear una cita dentro de un paquete, al reprogramarla, al editarle la hora y al absorber citas
-- sueltas. Esta migración es solo para los paquetes que ya estaban mal.
--
-- EFECTO SECUNDARIO BUSCADO: el reparto de un pago entre las sesiones de un paquete
-- (PagoService#repartirEntreSesiones) recorre las sesiones por número. Al quedar el número en
-- orden cronológico, un adelanto cubre primero las sesiones más próximas, que es lo que se
-- espera — antes cubría las que se habían creado primero, que podían ser de dentro de un mes.
--
-- VA EN DOS PASOS porque (tratamiento_id, numero) es UNIQUE y no es diferible: asignar 1..N de
-- una vez haría que a media sentencia dos filas compartieran número y Postgres abortaría. Pasar
-- antes todo a negativo deja libre el rango positivo.
--
-- Idempotente: se puede reaplicar sin romper nada (la segunda vez no cambia ninguna fila).

BEGIN;

-- Paso 1: apartar los números al rango negativo.
UPDATE sesiones s
   SET numero = -numero
 WHERE numero > 0
   AND s.tratamiento_id IN (
       SELECT o.tratamiento_id
         FROM (SELECT s2.tratamiento_id, s2.numero,
                      ROW_NUMBER() OVER (
                          PARTITION BY s2.tratamiento_id
                          ORDER BY CASE WHEN c.fecha_inicio IS NULL THEN 1 ELSE 0 END,
                                   c.fecha_inicio ASC,
                                   s2.id ASC) AS rn
                 FROM sesiones s2
                 LEFT JOIN citas c ON c.id = s2.cita_activa_id) o
        WHERE o.numero <> o.rn
        GROUP BY o.tratamiento_id);

-- Paso 2: escribir el número definitivo, 1..N por fecha.
UPDATE sesiones s
   SET numero = o.rn
  FROM (SELECT s2.id,
               ROW_NUMBER() OVER (
                   PARTITION BY s2.tratamiento_id
                   ORDER BY CASE WHEN c.fecha_inicio IS NULL THEN 1 ELSE 0 END,
                            c.fecha_inicio ASC,
                            s2.id ASC) AS rn
          FROM sesiones s2
          LEFT JOIN citas c ON c.id = s2.cita_activa_id) o
 WHERE s.id = o.id
   AND s.numero < 0;

COMMIT;

-- Comprobacion: debe devolver cero filas.
-- SELECT tratamiento_id, count(*) FROM (
--   SELECT s.tratamiento_id, s.numero,
--          ROW_NUMBER() OVER (PARTITION BY s.tratamiento_id
--            ORDER BY CASE WHEN c.fecha_inicio IS NULL THEN 1 ELSE 0 END, c.fecha_inicio, s.id) rn
--     FROM sesiones s LEFT JOIN citas c ON c.id = s.cita_activa_id) o
--  WHERE numero <> rn GROUP BY tratamiento_id;
