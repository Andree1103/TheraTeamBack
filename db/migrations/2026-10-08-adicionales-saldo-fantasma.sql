-- Endereza los cobros adicionales que quedaron diciendo que consumieron saldo.
--
-- El cobro adicional no toca el saldo, pero se grababa saldo_generado = 0 con saldo_previo = lo
-- que el paciente tuviera. Como el consumo de credito se lee restando los dos, esas filas dicen
-- que se comieron todo su saldo. El saldo real nunca cambio; lo que estaba mal era el registro.
--
-- Importa porque borrar uno de esos pagos deshace ese consumo inventado y le REGALA al paciente
-- ese importe.
--
-- Va como migracion y no como script suelto porque el libro de movimientos NO PUEDE rellenarse
-- hasta que esto este arreglado: el asiento de una de esas ventas no cuadra —"de donde sale
-- S/ 473, a donde va S/ 20"— y la ecuacion lo rechaza, como debe. El nombre la deja antes de
-- 2026-10-08-libro-* en el orden alfabetico, que es el que sigue el aplicador.
--
-- En el volcado del 06/10 no habia ninguno. En el del 07/10 hay TRES, del mismo dia y del mismo
-- paciente (JOSE CARLOS AMADO RAMOS, S/ 453 a favor): dos ventas de productos y un cobro por
-- zona adicional. Ninguno le quito el saldo de verdad, pero borrar cualquiera de los tres se lo
-- habria duplicado a S/ 906.

UPDATE pagos
   SET saldo_generado = saldo_previo
 WHERE COALESCE(es_adicional,false)
   AND COALESCE(saldo_previo,0) <> COALESCE(saldo_generado,0);

SELECT count(*) AS quedan_mal FROM pagos
 WHERE COALESCE(es_adicional,false) AND COALESCE(saldo_previo,0) <> COALESCE(saldo_generado,0);
