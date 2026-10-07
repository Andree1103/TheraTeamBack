-- Cada pago recuerda si trajo dinero, en vez de preguntarselo a su metodo.
--
-- Hoy "¿este pago fue dinero?" se responde mirando cat_metodos_pago.cuenta_en_caja. Eso tiene
-- un problema serio: es un dato de HOY aplicado al PASADO. Si manana alguien marca "Efectivo"
-- como que no cuenta en caja —o al reves, "Sin pago" como que si— todos los arqueos y todas las
-- reversiones de meses anteriores cambian de significado de golpe, en silencio. La historia no
-- puede depender de una casilla que se puede cambiar.
--
-- A partir de aqui el pago se graba con la respuesta puesta y ya no cambia. El catalogo sigue
-- decidiendo el valor inicial de los pagos NUEVOS; lo que deja de hacer es reescribir el pasado.

ALTER TABLE pagos ADD COLUMN IF NOT EXISTS trajo_dinero boolean;

COMMENT ON COLUMN pagos.trajo_dinero IS
  'Si este cobro movio dinero de verdad. Se fija al crearlo segun el metodo y NO cambia despues: el arqueo y las reversiones leen esto, no la configuracion actual del catalogo.';

-- Relleno del historico con el mismo criterio que se venia usando: manda el metodo, y un pago
-- sin metodo es saldo a favor (no entro dinero).
UPDATE pagos p
   SET trajo_dinero = CASE
         WHEN p.metodo_id IS NULL THEN false
         ELSE COALESCE((SELECT m.cuenta_en_caja FROM cat_metodos_pago m WHERE m.id = p.metodo_id), true)
       END
 WHERE p.trajo_dinero IS NULL;

ALTER TABLE pagos ALTER COLUMN trajo_dinero SET DEFAULT true;
ALTER TABLE pagos ALTER COLUMN trajo_dinero SET NOT NULL;

SELECT trajo_dinero, count(*) AS pagos, sum(monto_recibido) AS importe
  FROM pagos GROUP BY 1 ORDER BY 1;
