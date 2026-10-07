-- Lo que nunca debe poder escribirse, dicho en la base.
--
-- Hasta ahora todas las reglas del dinero vivian en Java, y esta semana se vio lo fragil que es
-- eso: una validacion que no se ejecutaba porque el metodo no se llamaba isXxx(), un metodo de
-- pago "obligatorio" que no lo era, una fecha sin tope. Cuando la unica barrera es el codigo,
-- basta un camino nuevo —o un script de carga— para saltarsela sin enterarse.
--
-- Lo de aqui abajo no se puede saltar. Son invariantes de verdad: cosas que no significan nada
-- en ningun escenario del negocio, no reglas de proceso.
--
-- Lo que NO se pone, a proposito:
--
--   monto_pagado <= precio. Parece invariante y no lo es: bajar el precio de una cita ya
--   cobrada es una accion legitima (un descuento acordado despues), y entonces lo pagado queda
--   por encima del precio hasta que alguien convierta el exceso en saldo. Hay un caso asi en
--   produccion ahora mismo (cita 1879: precio 40, pagado 50). Prohibirlo reventaria la
--   operacion en vez de proteger nada — va como comprobacion de auditoria, no como barrera.
--
--   El metodo obligatorio en las devoluciones: crearDevolucionManual admite metodo nulo cuando
--   no se sabe por donde salio el dinero, y ese dato tardio es mejor que un metodo inventado.
--
-- Todas se comprobaron contra el volcado de produccion del 06/10: cero violaciones salvo la
-- que queda fuera.

ALTER TABLE pacientes
  ADD CONSTRAINT ck_pacientes_saldo_no_negativo
  CHECK (saldo_a_favor IS NULL OR saldo_a_favor >= 0);

ALTER TABLE pagos
  ADD CONSTRAINT ck_pagos_importes_no_negativos
  CHECK (COALESCE(monto_recibido, 0) >= 0 AND COALESCE(monto_aplicado, 0) >= 0
         AND COALESCE(saldo_generado, 0) >= 0 AND COALESCE(saldo_previo, 0) >= 0);

-- Si entro dinero, hay que decir por donde. Las devoluciones quedan fuera: ahi el medio puede
-- no conocerse en el momento.
ALTER TABLE pagos
  ADD CONSTRAINT ck_pagos_dinero_lleva_metodo
  CHECK (COALESCE(monto_recibido, 0) <= 0 OR metodo_id IS NOT NULL OR COALESCE(es_devolucion, false));

ALTER TABLE citas
  ADD CONSTRAINT ck_citas_importes_no_negativos
  CHECK (COALESCE(precio, 0) >= 0 AND COALESCE(monto_pagado, 0) >= 0);

ALTER TABLE tratamientos
  ADD CONSTRAINT ck_tratamientos_cobrado_no_negativo
  CHECK (COALESCE(total_cobrado, 0) >= 0);

SELECT conrelid::regclass AS tabla, conname AS restriccion
  FROM pg_constraint
 WHERE conname LIKE 'ck_%'
 ORDER BY 1, 2;
