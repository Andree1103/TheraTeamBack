-- Permiso propio para cambiar la hora de corte de la caja.
--
-- Hasta ahora bastaba con "editar" en el modulo Caja. El problema es que ese permiso da varias
-- cosas a la vez —registrar egresos, reabrir un turno cerrado— y la hora de corte no es una de
-- ellas: no es una operacion del dia, es la REGLA que parte el dia en dos. Moverla recalcula
-- que entro en cada turno, incluidos los que ya estan cerrados.
--
-- Quien maneja la caja necesita lo primero y no deberia poder lo segundo. Separandolos, a
-- Encargado y Operaciones se les puede dejar operar sin que puedan mover la linea.
--
-- Arranca en true solo para ADM y GER, los dos roles de direccion. Cajero nunca lo tuvo (no
-- tiene "editar" en Caja) y sigue sin tenerlo.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_editar_hora_corte BOOLEAN NOT NULL DEFAULT false;

UPDATE cat_roles
   SET puede_editar_hora_corte = true
 WHERE UPPER(key) IN ('ADM', 'ADMIN', 'GER')
    OR UPPER(nombre) LIKE 'ADMINISTRADOR%'
    OR UPPER(nombre) LIKE 'GERENTE%';

COMMIT;

SELECT key, nombre, puede_editar_hora_corte FROM cat_roles ORDER BY id;
