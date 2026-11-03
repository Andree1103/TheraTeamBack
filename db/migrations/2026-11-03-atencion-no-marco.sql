-- Tercer resultado posible de una cita: que nadie la marcara.
--
-- QUÉ CAMBIA: atencion_clinica.tipo admitía dos valores —ATENDIDA e INASISTENCIA— y ahora admite
-- tres. El que faltaba es NO_MARCO: la cita que ya pasó y a la que nadie le dijo si el paciente
-- vino o no.
--
-- POR QUÉ HACÍA FALTA: con dos valores, "no se marcó" no era un resultado, era la AUSENCIA de
-- uno. Y una ausencia no se puede revisar. En la pantalla se veían igual la cita que nadie había
-- mirado nunca y la que ya se había revisado sin poder reconstruir qué pasó, así que cada repaso
-- del mes volvía a empezar por las mismas filas. Con una fila NO_MARCO queda firmado quién la
-- revisó y cuándo, y el repaso siguiente ya la puede dejar en paz.
--
-- Y ADEMÁS CAMBIA EL DEFAULT, que es la mitad importante: la columna nacía en 'ATENDIDA'. Una
-- fila a medio llenar afirmaba por su cuenta que la sesión se dio, y esa es justo la afirmación
-- que más cuesta desmentir un mes después. Hasta que alguien diga qué pasó, lo que pasó es que no
-- se marcó. Las dos rutas que sí saben la respuesta la escriben explícitamente
-- (AtencionClinicaService#registrar y #registrarInasistencia), así que el nuevo default solo cae
-- donde de verdad no se sabe.
--
-- LO QUE NO HACE: una fila NO_MARCO no mueve un sol, no cuenta como sesión dada, no suma al
-- contador del paquete y NO cambia el estado de la cita. Eso último es a propósito y es la
-- diferencia con marcar inasistencia: decir "no se marcó" no es decir que el paciente no vino. La
-- cita se queda Programada —que es la verdad— y sigue saliendo en el filtro de sin marca hasta
-- que alguien la resuelva.
--
-- LAS FILAS QUE YA EXISTEN NO SE TOCAN: todas son ATENDIDA o INASISTENCIA, y las dos siguen
-- significando lo mismo. Las citas sin marcar de hoy tampoco cambian: no tienen fila aquí, y no
-- se les inventa una. Tener fila dice que alguien la revisó, y nadie lo ha hecho todavía.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

-- El CHECK se reemplaza, no se amplía: no hay ALTER CONSTRAINT para un CHECK en Postgres. Se
-- borra por nombre (IF EXISTS, por si esta migración ya corrió) y se vuelve a crear con los tres.
ALTER TABLE atencion_clinica
    DROP CONSTRAINT IF EXISTS atencion_clinica_tipo_check;

ALTER TABLE atencion_clinica
    ADD CONSTRAINT atencion_clinica_tipo_check
    CHECK (tipo IN ('ATENDIDA', 'INASISTENCIA', 'NO_MARCO'));

ALTER TABLE atencion_clinica
    ALTER COLUMN tipo SET DEFAULT 'NO_MARCO';

COMMENT ON COLUMN atencion_clinica.tipo IS
    'Qué pasó con la cita: ATENDIDA = la sesión se dio; INASISTENCIA = el paciente no vino; '
    'NO_MARCO = nadie lo anotó. NO_MARCO es el default a propósito: hasta que alguien diga qué '
    'pasó, lo que pasó es que no se marcó. Ojo: la mayoría de las citas sin marcar no tienen fila '
    'aquí — tenerla significa que alguien ya la revisó y dejó constancia.';

COMMENT ON COLUMN atencion_clinica.motivo IS
    'Por qué no vino (tipo INASISTENCIA) o por qué nadie lo marcó (tipo NO_MARCO), tal como se '
    'anotó. En NO_MARCO es opcional: ahí el dato es que nadie marcó, y quien repasa el mes casi '
    'nunca sabe por qué.';

COMMIT;
