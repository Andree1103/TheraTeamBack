-- Horario fijo del paciente: "este paciente viene los lunes a las 9 con Carla, terapia KIDS".
--
-- QUÉ ES Y QUÉ NO ES: es una anotación de REFERENCIA, no una reserva ni un generador de citas.
-- Sirve para responder "¿este paciente tiene horarios fijos, en qué terapias y con qué
-- terapeutas?" sin tener que deducirlo de sus citas. No bloquea el espacio en la agenda ni crea
-- nada automáticamente: agendar sigue siendo manual, igual que hoy.
--
-- POR QUÉ UNA TABLA Y NO COLUMNAS EN pacientes: un paciente de terapia física suele venir dos o
-- tres veces por semana, y cada día puede ser con otro terapeuta o para otra terapia. Con
-- columnas habría que inventar horario_fijo_1, horario_fijo_2... y rehacerlo al aparecer el cuarto.
--
-- POR QUÉ NO SE DEDUCE DE citas.tipo_recurrencia = 'FIJO': esa marca vive en cada cita suelta y
-- solo existe mientras existan citas creadas. Las citas se generan hasta una fecha; pasada esa
-- fecha el paciente se quedaría sin horario fijo aunque en la clínica siga teniéndolo.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

CREATE TABLE IF NOT EXISTS paciente_horario_fijo (
    id                     SERIAL PRIMARY KEY,
    paciente_id            INTEGER  NOT NULL REFERENCES pacientes(id)  ON DELETE CASCADE,
    terapeuta_id           INTEGER  NOT NULL REFERENCES terapeutas(id) ON DELETE CASCADE,
    -- Opcional: se puede anotar el horario sin precisar la terapia todavía.
    tipo_terapia_id        INTEGER           REFERENCES tipos_terapia(id),
    dia_semana             SMALLINT NOT NULL,
    hora_inicio            TIME     NOT NULL,
    -- Se guarda para poder mostrar "09:00 - 10:00". Si no se sabe, queda en null.
    hora_fin               TIME,
    activo                 BOOLEAN  NOT NULL DEFAULT true,
    notas                  VARCHAR(255),
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    idusuario_creacion     BIGINT,
    idusuario_modificacion BIGINT,
    CONSTRAINT paciente_horario_fijo_dia_check  CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT paciente_horario_fijo_hora_check CHECK (hora_fin IS NULL OR hora_fin > hora_inicio),
    -- El mismo paciente no puede tener dos veces la misma casilla con el mismo terapeuta.
    CONSTRAINT paciente_horario_fijo_unico UNIQUE (paciente_id, terapeuta_id, dia_semana, hora_inicio)
);

-- La consulta normal es "los horarios de ESTE paciente" (su ficha).
CREATE INDEX IF NOT EXISTS ix_phf_paciente  ON paciente_horario_fijo (paciente_id);
-- La otra es "quién tiene fijo con ESTE terapeuta tal día" (para mirar su semana).
CREATE INDEX IF NOT EXISTS ix_phf_terapeuta ON paciente_horario_fijo (terapeuta_id, dia_semana);

COMMIT;

-- Verificación
SELECT count(*) AS horarios_fijos FROM paciente_horario_fijo;
