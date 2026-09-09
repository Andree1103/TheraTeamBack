-- Permiso por rol para corregir una atencion.
--
-- "Corregir" cambia terapeuta, tipo de terapia, precio y metodo de pago de una cita YA
-- ATENDIDA, que es justo lo que la edicion normal prohibe. Hasta ahora estaba clavado a
-- hasRole('ADMIN') en el codigo; esto lo vuelve un permiso configurable por rol, igual que
-- "ver celular de pacientes" y "exportar a Excel".
--
-- Arranca en true para Administrador (mismo alcance que hoy, nadie pierde el permiso al
-- desplegar) y en false para el resto — se activa desde Seguridad > Roles.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_corregir_atencion BOOLEAN NOT NULL DEFAULT false;

UPDATE cat_roles
   SET puede_corregir_atencion = true
 WHERE UPPER(key) = 'ADMIN' OR UPPER(nombre) LIKE 'ADMINISTRADOR%';

COMMIT;

-- Verificacion
SELECT key, nombre, puede_corregir_atencion FROM cat_roles ORDER BY id;
