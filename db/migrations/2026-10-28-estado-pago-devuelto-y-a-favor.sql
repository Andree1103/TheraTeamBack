-- Estados de pago DEVUELTO y A_FAVOR: que se hizo con el dinero de una cita que se anulo.
--
-- Al anular una cita cobrada hay que elegir entre devolverle el dinero al paciente o dejarselo
-- como saldo a favor. Las dos cosas acababan dejando la cita en "Sin pago", que es verdad a
-- medias —ya no tiene dinero aplicado— pero se come justo el dato que hace falta despues: si
-- aquello salio del cajon o si el paciente tiene un credito esperandolo. En el listado de Citas
-- las dos filas se leian igual, y para saber cual era cual habia que entrar a Pagos a buscar si
-- existia una devolucion.
--
-- Mismo criterio que DESCONTADA (ver 2026-10-02): cuando dos hechos distintos comparten estado,
-- el estado deja de servir para contarlos.

-- La secuencia del catalogo viene desalineada del seed inicial, que inserto los ids a mano.
-- Sin realinearla, cualquier INSERT choca contra la PK.
SELECT setval(pg_get_serial_sequence('cat_estados_pago_cita', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM cat_estados_pago_cita), 1));

INSERT INTO cat_estados_pago_cita (key, nombre, color)
SELECT 'DEVUELTO', 'Devuelto', '#0ea5e9'
WHERE NOT EXISTS (SELECT 1 FROM cat_estados_pago_cita WHERE key = 'DEVUELTO');

INSERT INTO cat_estados_pago_cita (key, nombre, color)
SELECT 'A_FAVOR', 'Saldo a favor', '#8b5cf6'
WHERE NOT EXISTS (SELECT 1 FROM cat_estados_pago_cita WHERE key = 'A_FAVOR');

-- Las citas que YA se anularon no se tocan.
--
-- Se podria intentar adivinarlo mirando si existe una devolucion contra esa cita, pero la
-- anulacion de un paquete no deja una devolucion por cita: deja una por sesion y otra por el
-- sobrante, y las que se fueron a saldo no dejan ninguna fila que las nombre. Repartir eso a
-- posteriori seria inventarse la mitad. Las viejas se quedan en "Sin pago", que es lo que el
-- sistema sabia cuando pasaron; el dato nuevo empieza a existir desde aqui.
