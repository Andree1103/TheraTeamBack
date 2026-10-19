-- El libro deja de borrarse solo cuando se borra un pago.
--
-- asientos.pago_id estaba con ON DELETE CASCADE. Eso convierte al libro en un espejo del pago en
-- vez de en su historia: al eliminar un cobro, la base se llevaba por delante su asiento, y con
-- el la unica constancia de que ese dinero entro alguna vez. Un libro que se puede borrar no
-- sirve para lo que existe, que es responder "quien toco esto y cuando" cuando ya nadie se
-- acuerda.
--
-- Ademas rompia la propia anotacion del borrado: el asiento que dice "se elimino el pago #897"
-- apunta a un pago que esta desapareciendo en esa misma operacion. O lo tumbaba la clave
-- foranea, o la cascada lo borraba acto seguido. En produccion el resultado fue que NINGUNA
-- anulacion, devolucion ni eliminacion de pago llego nunca al libro: solo habia asientos de
-- COBRO. Y un pago que se borro, devolvio su saldo al paciente y se quedo sin borrar.
--
-- Con SET NULL el asiento sobrevive y solo pierde el puntero. No se pierde nada que importe: la
-- nota del asiento ya lleva el numero del pago, y el paciente, la cita y el importe estan en sus
-- propias columnas.

ALTER TABLE asientos DROP CONSTRAINT IF EXISTS asientos_pago_id_fkey;
ALTER TABLE asientos ADD CONSTRAINT asientos_pago_id_fkey
  FOREIGN KEY (pago_id) REFERENCES pagos(id) ON DELETE SET NULL;
