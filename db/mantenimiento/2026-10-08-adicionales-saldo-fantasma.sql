-- Endereza los cobros adicionales que quedaron diciendo que consumieron saldo.
--
-- El cobro adicional no toca el saldo, pero se grababa saldo_generado = 0 con saldo_previo = lo
-- que el paciente tuviera. Como el consumo de credito se lee restando los dos, esas filas dicen
-- que se comieron todo su saldo. El saldo real nunca cambio; lo que estaba mal era el registro.
--
-- Importa porque borrar uno de esos pagos deshace ese consumo inventado y le REGALA al paciente
-- ese importe. En el volcado del 06/10 no hay ninguno —hace falta tener saldo justo al recibir
-- un cobro adicional— pero el script queda por si alguno entro despues.

UPDATE pagos
   SET saldo_generado = saldo_previo
 WHERE COALESCE(es_adicional,false)
   AND COALESCE(saldo_previo,0) <> COALESCE(saldo_generado,0);

SELECT count(*) AS quedan_mal FROM pagos
 WHERE COALESCE(es_adicional,false) AND COALESCE(saldo_previo,0) <> COALESCE(saldo_generado,0);
