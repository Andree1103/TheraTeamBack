-- Un paciente puede estar en varias cuentas de credito. Se elige al fiar, cita por cita.
--
-- LO QUE SE ASUMIO MAL AYER. La migracion 2026-11-16-creditos.sql creo el indice parcial
-- uq_ccp_un_paciente_una_cuenta: "un paciente activo en UNA sola cuenta". El argumento era que
-- si estuviera en dos, al fiar habria que preguntar a cual se carga.
--
-- Resulta que hay que preguntarlo. El caso que lo rompe: un terapeuta atiende y el credito es
-- para OTRO terapeuta. La misma paciente la paga su hija una semana y su hijo la siguiente; o la
-- madre de una terapeuta se atiende con una companera y lo paga la hija de todas formas. Con el
-- indice puesto, la segunda cita se cae con "ya esta amarrado a la cuenta de X: desamarralo
-- antes de cargarlo a esta" — y desamarrar para volver a amarrar borra de quien era la deuda
-- vieja. El mostrador, con el paciente delante, vuelve al atajo del efectivo que no entro, que
-- es justo lo que esto venia a evitar.
--
-- LO QUE NO CAMBIA. El amarre sigue siendo lo que autoriza: la FK compuesta de citas y
-- tratamientos (paciente_id, cuenta_credito_id) exige que el par exista, asi que ninguna cita se
-- cuelga de una cuenta que no cubra a ese paciente. Lo unico que se levanta es la exclusividad.
--
-- Y uq_ccp_cuenta_paciente sigue: una fila por par. Dos filas del mismo par si serian un error,
-- porque entonces "desde cuando lo cubre" tendria dos respuestas.
--
-- A QUE CUENTA SE CARGA, ENTONCES. A la que elija quien crea la cita. El formulario sugiere la
-- ultima que le pago algo a ese paciente (CuentaCreditoService.cuentaDePaciente), porque lo
-- normal es que se repita, pero es una sugerencia: el desplegable las lista todas.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

DROP INDEX IF EXISTS uq_ccp_un_paciente_una_cuenta;

COMMENT ON TABLE cuenta_credito_pacientes IS
  'Que pacientes puede cargar cada cuenta de credito. Varios por cuenta: un terapeuta suele traer a su madre, su padre y su hijo, y lo paga junto. Y varias cuentas por paciente: a la misma persona la puede pagar su hija una semana y su hijo la otra. Cual paga cada atencion lo dice la cita, no esta tabla.';

COMMIT;
