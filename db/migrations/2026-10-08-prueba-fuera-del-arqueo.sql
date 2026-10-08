-- El metodo "Prueba" deja de contar en el arqueo del dia.
--
-- Se uso 6 veces en produccion por S/ 1.435 —pruebas del sistema, no dinero del mostrador— y
-- al estar marcado como dinero inflaba el cierre. Sale del arqueo pero no se borra: el cierre
-- sigue listando aparte todo lo que no cuenta en caja, igual que "Sin pago" y "Paquete".
--
-- Hay que tocar TRES sitios, y ahi esta la gracia:
--
--   1. El catalogo, para que los proximos no cuenten.
--   2. pagos.trajo_dinero, porque esa respuesta se congela al grabar el pago (a proposito: lo
--      que entro tiene que medirse con la vara del dia en que entro, no con la de hoy). Sin
--      esto, cambiar el catalogo no sacaria del arqueo ni uno de los seis ya grabados.
--   3. El libro, donde esos seis dejaron una linea CAJA_ENTRA. Si no se cambian a CONSTANCIA,
--      el libro seguiria diciendo que entraron S/ 1.435 por el cajon y se separaria de la caja
--      — que es justo la clase de divergencia que el libro viene a eliminar.
--
-- Las dos lineas estan del mismo lado de la ecuacion, asi que los asientos siguen cuadrando.
-- Y el CHECK obliga a soltar el metodo: una constancia no entro por ningun sitio.
--
-- LO QUE ESTO NO ARREGLA, a proposito: tres de los seis eran adelantos y generaron saldo real.
-- JOSE CARLOS AMADO RAMOS recibio S/ 850 asi y hoy tiene S/ 453 a favor; ALEXANDER ARANGO,
-- S/ 495 con S/ 550; CINTHIA NINAHUANCA, S/ 40 con S/ 111. Ese credito lo pueden gastar en
-- servicios de verdad. Quitarselo es una decision del negocio, no de una migracion.

UPDATE cat_metodos_pago SET cuenta_en_caja = false WHERE key = 'PRUEB';

UPDATE pagos SET trajo_dinero = false
 WHERE metodo_id = (SELECT id FROM cat_metodos_pago WHERE key = 'PRUEB');

UPDATE asiento_lineas l
   SET concepto = 'CONSTANCIA', metodo_id = NULL
  FROM asientos a, pagos p, cat_metodos_pago m
 WHERE l.asiento_id = a.id AND a.pago_id = p.id AND p.metodo_id = m.id
   AND m.key = 'PRUEB' AND l.concepto = 'CAJA_ENTRA';

SELECT (SELECT count(*) FROM pagos p JOIN cat_metodos_pago m ON m.id=p.metodo_id
         WHERE m.key='PRUEB' AND p.trajo_dinero) AS pagos_que_siguen_en_caja,
       (SELECT count(*) FROM asiento_lineas l JOIN asientos a ON a.id=l.asiento_id
          JOIN pagos p ON p.id=a.pago_id JOIN cat_metodos_pago m ON m.id=p.metodo_id
         WHERE m.key='PRUEB' AND l.concepto='CAJA_ENTRA') AS lineas_que_siguen_en_caja;
