-- Un tipo de asiento mas: CORRECCION.
--
-- Corregir el metodo de un cobro no es un cobro, ni una devolucion, ni una anulacion: el dinero
-- sigue siendo el mismo y la deuda tambien, lo unico que cambia es por donde entro. Meterlo en
-- AJUSTE lo habria mezclado con la apertura de saldos historicos, que es otra cosa y se consulta
-- aparte.
--
-- Sin esto, el CHECK de la tabla rechaza la fila y el asiento se pierde en silencio (LibroService
-- se traga la excepcion a proposito para no tumbar el cobro) — el movimiento quedaria hecho en
-- pagos y ausente en el libro, que es justo lo que el reconciliador tendria que cazar despues.

ALTER TABLE asientos DROP CONSTRAINT IF EXISTS ck_asientos_tipo;
ALTER TABLE asientos ADD CONSTRAINT ck_asientos_tipo
  CHECK (tipo IN ('COBRO','DEVOLUCION','ANULACION','AJUSTE','CORRECCION'));
