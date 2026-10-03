-- Un metodo de pago que NO es dinero en caja.
--
-- El cierre suma todo pago registrado en el dia, sea cual sea el metodo, porque hasta ahora
-- todos los metodos eran dinero: efectivo, Yape, tarjeta. Pero en la practica se usan metodos
-- que no mueven caja — "Sin pago" para dejar constancia de una sesion cuyo dinero entro
-- semanas antes, o un seguro que liquida aparte. Esos pagos inflaban el arqueo del dia: la
-- caja decia S/ 5,978 cuando en el cajon habia S/ 688.
--
-- No se borran ni se prohiben: el pago sigue existiendo y la cita sigue saldada. Solo dejan de
-- sumar al arqueo y se muestran aparte, para que el cierre cuadre contra el efectivo real.
--
-- Por omision TRUE: los metodos que ya existen son dinero de verdad y nada cambia para ellos.

ALTER TABLE cat_metodos_pago
  ADD COLUMN IF NOT EXISTS cuenta_en_caja boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN cat_metodos_pago.cuenta_en_caja IS
  'false = los pagos con este metodo no suman al arqueo del cierre de caja (no es dinero que entre al cajon ese dia).';

-- Los que ya se sabe que no son dinero se marcan solos: un metodo llamado "Sin pago" no es
-- un cobro por definicion, y "Paquete" significa que la sesion la cubre un paquete cobrado
-- aparte. Hacerlo aqui evita que el arreglo dependa de que alguien se acuerde de configurarlo
-- despues de desplegar — que es justo el rato en que la caja seguiria descuadrada.
--
-- Es solo el valor inicial: si alguno de los dos si fuera dinero, se vuelve a poner en "Si"
-- desde Configuraciones > Metodos de pago y manda eso.
UPDATE cat_metodos_pago
   SET cuenta_en_caja = false
 WHERE lower(btrim(nombre)) IN ('sin pago', 'paquete')
   AND cuenta_en_caja = true;

SELECT id, nombre, activo, cuenta_en_caja FROM cat_metodos_pago ORDER BY id;
