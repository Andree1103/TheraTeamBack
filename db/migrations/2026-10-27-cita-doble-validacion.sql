-- Doble validacion de una cita.
--
-- POR QUE: la cita la carga quien atiende o recepcion, y hasta ahora nadie confirmaba que lo
-- cargado fuera correcto. El pedido es que una segunda persona —con permiso— de el visto bueno,
-- y que si despues alguien cambia la cita, ese visto bueno caduque y haya que repetirlo. Un
-- sello que sobrevive al cambio de lo que sellaba no vale nada.
--
-- NO se reutiliza tipo_recurrencia para esto. Ese campo es la clasificacion de la cita (FIJO /
-- EVENTUAL / SOLO_HOY) y aunque hoy no se use, darle encima otro significado deja una columna
-- que dice una cosa y guarda otra. La validacion va en sus propios campos.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

ALTER TABLE citas
    ADD COLUMN IF NOT EXISTS validada      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS validada_por  BIGINT,
    ADD COLUMN IF NOT EXISTS validada_en   TIMESTAMP;

-- Si se borra el usuario, la cita no se va con el: queda validada y sin nombre, que es mejor
-- que perder la cita o perder el sello entero.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'citas_validada_por_fkey') THEN
        ALTER TABLE citas
            ADD CONSTRAINT citas_validada_por_fkey
            FOREIGN KEY (validada_por) REFERENCES usuarios(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Para que el listado de "sin validar" no barra la tabla entera: solo indexa las que faltan,
-- que son las que se consultan.
CREATE INDEX IF NOT EXISTS idx_citas_sin_validar ON citas (fecha_inicio) WHERE validada = false;

-- Permiso por rol. Arranca solo en Administrador: si naciera en true para todos, la "doble"
-- validacion la haria la misma persona que cargo la cita y no validaria nada.
ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_validar_cita BOOLEAN NOT NULL DEFAULT false;

UPDATE cat_roles
   SET puede_validar_cita = true
 WHERE UPPER(key) = 'ADMIN' OR UPPER(nombre) LIKE 'ADMINISTRADOR%';

COMMIT;

-- Verificacion
SELECT key, nombre, puede_validar_cita FROM cat_roles ORDER BY id;
SELECT count(*) AS citas_totales, count(*) FILTER (WHERE validada) AS ya_validadas FROM citas;
