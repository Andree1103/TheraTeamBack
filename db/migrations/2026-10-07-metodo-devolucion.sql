-- Las devoluciones dejan de disfrazarse del metodo por el que entro el dinero.
--
-- Hasta ahora una devolucion copiaba el metodo del pago original: devolver un cobro hecho en
-- Efectivo grababa otra linea "Efectivo". En el listado de pagos y en el cierre, la salida y la
-- entrada se veian iguales y solo el signo las distinguia. Ademas, al heredar un metodo que
-- cuenta en caja, la devolucion entraba en el arqueo del dia restando — y el negocio no quiere
-- eso: el arqueo del dia es lo que se cobro ese dia, no se mezcla con reintegros de cobros
-- viejos.
--
-- Con un metodo propio marcado cuenta_en_caja = false las dos cosas se resuelven de una: la
-- devolucion queda nombrada por lo que es, y sale del arqueo sin desaparecer — el cierre ya
-- lista aparte todo lo que no cuenta en caja, asi que se sigue viendo.
--
-- El metodo original no se pierde: se anota en las notas de la propia devolucion.

INSERT INTO cat_metodos_pago (key, nombre, activo, cuenta_en_caja)
SELECT 'DEVOLUCION', 'Devolución', true, false
 WHERE NOT EXISTS (SELECT 1 FROM cat_metodos_pago WHERE key = 'DEVOLUCION');

-- Las devoluciones que ya existen pasan al metodo nuevo, dejando dicho por donde salio el dinero.
UPDATE pagos p
   SET notas = COALESCE(NULLIF(btrim(p.notas), '') || ' — ', '')
               || 'Salió por: ' || COALESCE(m.nombre, 'sin método registrado'),
       metodo_id = (SELECT id FROM cat_metodos_pago WHERE key = 'DEVOLUCION'),
       trajo_dinero = false
  FROM cat_metodos_pago m
 WHERE p.metodo_id = m.id
   AND COALESCE(p.es_devolucion, false)
   AND m.key <> 'DEVOLUCION';

-- Las que no tenian metodo tambien quedan nombradas.
UPDATE pagos
   SET metodo_id = (SELECT id FROM cat_metodos_pago WHERE key = 'DEVOLUCION'),
       trajo_dinero = false
 WHERE COALESCE(es_devolucion, false) AND metodo_id IS NULL;

SELECT m.nombre, count(*) AS devoluciones, sum(p.monto_recibido) AS importe
  FROM pagos p LEFT JOIN cat_metodos_pago m ON m.id = p.metodo_id
 WHERE COALESCE(p.es_devolucion, false)
 GROUP BY m.nombre ORDER BY 1;
