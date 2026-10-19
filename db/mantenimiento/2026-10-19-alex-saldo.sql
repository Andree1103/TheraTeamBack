-- Corrige el saldo a favor de ALEX LEONARDO YAMIR CARRILLO JIMENEZ (DNI 79014242).
--
-- QUE PASO
--
-- Adelanto S/ 185 para cinco sesiones de S/ 37. Cuatro descontaron de su saldo. La quinta —la
-- cita #1085— se cobro con el medio "Paquete", que no mueve dinero y no toca el saldo, asi que
-- quedo dada por pagada sin que saliera de ninguna parte. Le sobran S/ 37 que ya se gasto.
--
-- Aparte, al libro le falta un asiento: el pago #897 de la cita #1124 se borro, le devolvio sus
-- S/ 37 al paciente, y esa devolucion nunca llego a anotarse —la clave foranea estaba con
-- ON DELETE CASCADE y se llevaba el asiento por delante. Eso ya esta arreglado en el codigo
-- (f50786d); aqui se repone lo que se perdio en este caso concreto.
--
-- QUE HACE
--
-- 1. Repone el asiento que falta del borrado del pago #897: vuelve a deberse la cita #1124 y se
--    le reconocen los S/ 37. Despues de esto el libro y lo guardado dicen lo mismo: 37.
-- 2. Anota la correccion: se retira la constancia de la cita #1085 y en su lugar se usa su
--    credito, que es lo que tenia que haber pasado. El saldo cae a 0.
-- 3. Baja saldo_a_favor a 0 y deja el movimiento que lo explica en el historial del paciente.
--
-- NO reescribe el pago #891. Ese cobro se hizo como se hizo y el libro lo cuenta entero; pisarlo
-- dejaria el saldo bien y la historia mintiendo. La correccion es un movimiento de hoy, que es
-- como se corrige cualquier cosa de un dia ya pasado.
--
-- SE NIEGA A CORRER si el paciente no tiene exactamente los S/ 37, si la cita #1085 no esta como
-- la dejamos, o si ya se corrigio antes. Asi no hace nada dos veces ni sobre otro estado.

BEGIN;

DO $$
DECLARE
  v_pac   BIGINT;
  v_saldo NUMERIC;
  v_1085  NUMERIC;
  v_as    BIGINT;
BEGIN
  SELECT id, COALESCE(saldo_a_favor,0) INTO v_pac, v_saldo
    FROM pacientes WHERE dni = '79014242';
  IF v_pac IS NULL THEN
    RAISE EXCEPTION 'No existe el paciente con DNI 79014242.';
  END IF;

  IF v_saldo <> 37 THEN
    RAISE EXCEPTION 'Esperaba S/ 37 de saldo y hay S/ %. Alguien lo movio: revisalo antes de correr esto.', v_saldo;
  END IF;

  SELECT COALESCE(monto_pagado,0) INTO v_1085 FROM citas WHERE id = 1085;
  IF v_1085 IS DISTINCT FROM 37 THEN
    RAISE EXCEPTION 'La cita 1085 ya no figura pagada con S/ 37 (tiene %). Revisalo antes de correr esto.', v_1085;
  END IF;

  IF EXISTS (SELECT 1 FROM asientos WHERE paciente_id = v_pac AND tipo = 'CORRECCION') THEN
    RAISE EXCEPTION 'Este paciente ya tiene una correccion anotada. No se repite.';
  END IF;

  -- 1) El asiento que se perdio al borrar el pago #897.
  INSERT INTO asientos (tipo, fecha, paciente_id, pago_id, usuario_id, nota, created_at)
  VALUES ('ANULACION', now(), v_pac, NULL, NULL,
          'Se eliminó el pago #897 — asiento repuesto: se perdió por la cascada al borrar el pago',
          now())
  RETURNING id INTO v_as;
  INSERT INTO asiento_lineas (asiento_id, concepto, importe, cita_id)
  VALUES (v_as, 'DEUDA_LIBERA', 37, 1124);
  INSERT INTO asiento_lineas (asiento_id, concepto, importe)
  VALUES (v_as, 'CREDITO_GENERA', 37);

  -- 2) La correccion: la cita #1085 se cubre con su credito, no con una constancia.
  INSERT INTO asientos (tipo, fecha, paciente_id, pago_id, usuario_id, nota, created_at)
  VALUES ('CORRECCION', now(), v_pac, NULL, NULL,
          'Corrección — la cita #1085 se cobró con "Paquete" sin descontar del adelanto; se cubre con su saldo a favor',
          now())
  RETURNING id INTO v_as;
  INSERT INTO asiento_lineas (asiento_id, concepto, importe)
  VALUES (v_as, 'CREDITO_USA', 37);
  INSERT INTO asiento_lineas (asiento_id, concepto, importe)
  VALUES (v_as, 'CONSTANCIA_LIBERA', 37);

  -- 3) El saldo guardado y su historial.
  UPDATE pacientes SET saldo_a_favor = 0 WHERE id = v_pac;
  INSERT INTO saldo_movimientos (paciente_id, monto, saldo_resultante, motivo, cita_id, fecha)
  VALUES (v_pac, -37, 0,
          'Corrección: la sesión del 24/10 se había cobrado con "Paquete" y no descontó del adelanto',
          1085, now());

  RAISE NOTICE 'Listo. % queda con S/ 0 de saldo a favor.',
    (SELECT COALESCE(nombre,'')||' '||COALESCE(apellido,'') FROM pacientes WHERE id = v_pac);
END $$;

COMMIT;

-- Como quedo.
SELECT COALESCE(p.nombre,'')||' '||COALESCE(p.apellido,'') AS paciente,
       p.saldo_a_favor AS saldo_guardado,
       (SELECT COALESCE(sum(CASE WHEN l.concepto='CREDITO_GENERA' THEN l.importe
                                 WHEN l.concepto='CREDITO_USA'    THEN -l.importe ELSE 0 END),0)
          FROM asientos a JOIN asiento_lineas l ON l.asiento_id=a.id
         WHERE a.paciente_id = p.id) AS saldo_segun_el_libro
  FROM pacientes p WHERE p.dni = '79014242';
