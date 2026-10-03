-- Un pago hecho solo con el saldo a favor no tiene metodo de pago.
--
-- Ese dinero ya entro el dia del adelanto, con el medio que tuviera entonces. Obligar a elegir
-- uno otra vez al usarlo era pedir una mentira: el pago quedaba con "Efectivo" sin que nadie
-- entregara efectivo. Con monto recibido 0 no afecta al arqueo, pero si ensuciaba el rastro.
--
-- Solo se afloja la restriccion de la columna. Cuando SI entra dinero (monto recibido > 0) el
-- metodo sigue siendo obligatorio, ahora validado en la entidad (tieneMetodoCuandoEntraDinero).

ALTER TABLE pagos ALTER COLUMN metodo_id DROP NOT NULL;

COMMENT ON COLUMN pagos.metodo_id IS
  'Como entro el dinero. NULL solo cuando no entro ninguno: el pago se cubrio con el saldo a favor del paciente.';

SELECT is_nullable, column_name FROM information_schema.columns
 WHERE table_name = 'pagos' AND column_name = 'metodo_id';
