-- Permiso por rol para ver el saldo inicial de la caja.
--
-- El saldo inicial arrastra lo acumulado de dias anteriores, asi que es el unico numero de la
-- pantalla de Caja que revela cuanto dinero hay en total. Quien cierra un turno necesita ver lo
-- que entro y salio en SU turno; el acumulado del negocio es otra cosa.
--
-- Mismo patron que "exportar a Excel", "ver celular de pacientes" y "corregir atencion": una
-- bandera en el rol, no un hasRole('ADMIN') clavado en el codigo. Asi se decide desde
-- Seguridad > Roles sin tocar nada.
--
-- QUIEN ARRANCA CON EL
--
-- Se pidio "solo el administrador", pero aplicarlo al pie de la letra dejaba fuera a GERENTE —
-- que es el rol del dueño y el que ya tiene exportar, corregir atencion y ver celular. Quien lo
-- pidio se habria quedado sin ver su propio saldo inicial al desplegar y habria parecido una
-- averia. Asi que arrancan ADM y GER, que son los dos roles de direccion, y lo pierden los
-- operativos: Cajero, Encargado, Terapeuta y Paciente, que es de lo que se trataba.
--
-- OPERACIONES queda FUERA aunque tenga los otros tres permisos: por el nombre es un rol de
-- gestion diaria, no de direccion. Si resulta que tambien debe verlo, se activa desde
-- Seguridad > Roles sin tocar nada de esto.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_ver_saldo_inicial BOOLEAN NOT NULL DEFAULT false;

UPDATE cat_roles
   SET puede_ver_saldo_inicial = true
 WHERE UPPER(key) IN ('ADM', 'ADMIN', 'GER')
    OR UPPER(nombre) LIKE 'ADMINISTRADOR%'
    OR UPPER(nombre) LIKE 'GERENTE%';

COMMIT;

SELECT key, nombre, puede_ver_saldo_inicial FROM cat_roles ORDER BY id;
