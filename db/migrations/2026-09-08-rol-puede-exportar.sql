-- Permiso por rol para exportar a Excel.
--
-- Los botones "Exportar Excel" sacan la base entera (pacientes, pagos, citas...) en un
-- archivo. Hasta ahora los veia cualquiera con acceso al modulo; esto permite dejarlos
-- solo para los roles que corresponda, igual que ya se hace con "ver celular de pacientes".
--
-- Arranca en true para Administrador (para no dejar a nadie sin poder exportar el dia del
-- despliegue) y en false para el resto — se activa desde Seguridad > Roles.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_exportar BOOLEAN NOT NULL DEFAULT false;

UPDATE cat_roles
   SET puede_exportar = true
 WHERE UPPER(key) = 'ADMIN' OR UPPER(nombre) LIKE 'ADMINISTRADOR%';

COMMIT;

-- Verificacion
SELECT key, nombre, puede_exportar FROM cat_roles ORDER BY id;
