-- Credito: una cita atendida que se paga despues, con alguien que responde por ella.
--
-- EL PROBLEMA. Hoy no se puede marcar una cita como Asistida sin un pago registrado
-- (CitaService, "No se puede marcar la cita como Asistida sin un pago registrado"). La regla es
-- buena --evita que quede Asistida una sesion que nunca se cobro-- pero no contempla el caso que
-- la clinica tiene todas las semanas: el familiar de un terapeuta que se atiende hoy y se paga a
-- fin de mes. Sin salida legitima, el mostrador usa dos atajos: cobrar un efectivo que no entro
-- (y descuadra la caja: de ahi salen varias de las diferencias del cuadre) o dejar la cita en
-- Programada para siempre (hay 21 citas ya pasadas atascadas asi). En los dos casos el dato se
-- pierde: nadie sabe cuanto hay por cobrar ni a quien cobrarselo.
--
-- LA FORMA. El credito no se cuelga del paciente, se cuelga de QUIEN RESPONDE. Es muy comun que
-- un terapeuta haga atender a su madre, a su padre y a su hijo, y lo pague junto a fin de mes:
-- son tres pacientes y una sola deuda, con un solo dueño. Si la deuda viviera en cada paciente
-- habria que reconstruir a mano de quien cobrar.
--
-- El titular es un USUARIO, no un terapeuta ni un paciente. Los cuatro terapeutas tienen
-- usuario_id, y cada paciente tiene el suyo (lo crea PacienteService). O sea que "usuario" ya es
-- la tabla de personas del sistema, y asi el mismo mecanismo sirve para los dos casos que pidio
-- la clinica: creditos de trabajadores y creditos de pacientes. La distincion es el tipo de la
-- cuenta, que es lo que filtra el reporte.
--
-- SIN SALDO GUARDADO. La cuenta no tiene columna de saldo: se suma de sus citas. Un total
-- cacheado es justo lo que obligo a escribir 2026-10-03-corregir-saldos-inflados.sql; un numero
-- que se deriva no se puede desincronizar.
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

-- ---------------------------------------------------------------------------------------------
-- 1. La cuenta de credito
-- ---------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS cuentas_credito (
    id                     bigserial PRIMARY KEY,

    -- Quien debe. UNIQUE: una cuenta por persona, a proposito. Con dos cuentas para el mismo
    -- titular, el mostrador tendria que elegir a cual cargar cada cita y el reporte de cobranza
    -- dejaria de responder "cuanto debe Rocio" con una sola fila.
    titular_usuario_id     bigint NOT NULL UNIQUE REFERENCES usuarios(id),

    -- Para que sirve la cuenta. No se deriva del rol del titular porque CONVENIO no es un rol:
    -- es un acuerdo con una empresa o colegio, y su titular puede ser cualquiera.
    tipo                   text NOT NULL,

    -- Tope de deuda acumulada. NULL = sin tope.
    --
    -- AVISA, NO BLOQUEA. Pasarse del tope no impide cargar la cita; hace que la pantalla lo
    -- advierta. Bloquear en el mostrador, con el paciente delante, obliga a la recepcionista a
    -- discutir con el familiar de una terapeuta -- y el atajo de siempre (cobrar un efectivo que
    -- no entro) vuelve a ser el camino facil. El control es que alguien lo vea, no que alguien
    -- pelee.
    limite                 numeric(10,2),

    activo                 boolean NOT NULL DEFAULT true,
    notas                  text,

    created_at             timestamp NOT NULL DEFAULT now(),
    updated_at             timestamp NOT NULL DEFAULT now(),
    idusuario_creacion     bigint,
    idusuario_modificacion bigint,

    CONSTRAINT ck_cuentas_credito_tipo
        CHECK (tipo IN ('TRABAJADOR', 'PACIENTE', 'CONVENIO')),
    CONSTRAINT ck_cuentas_credito_limite
        CHECK (limite IS NULL OR limite >= 0)
);

COMMENT ON TABLE cuentas_credito IS
  'Quien responde por las citas que se atienden hoy y se pagan despues. El saldo NO se guarda: se suma de las citas con estado de pago A_CREDITO cargadas a la cuenta.';
COMMENT ON COLUMN cuentas_credito.tipo IS
  'TRABAJADOR (personal de la clinica y sus familiares), PACIENTE (paga a fin de mes), CONVENIO (acuerdo con empresa o colegio).';
COMMENT ON COLUMN cuentas_credito.limite IS
  'Tope de deuda acumulada. NULL = sin tope. Pasarse AVISA, no bloquea.';

-- ---------------------------------------------------------------------------------------------
-- 2. A quienes cubre cada cuenta
-- ---------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS cuenta_credito_pacientes (
    -- Id propio, como rol_modulo_permisos: en el proyecto no hay ninguna entidad con clave
    -- compuesta, y mapear una obligaria a una @IdClass aparte para no ganar nada. La unicidad
    -- del par la garantizan las restricciones de abajo igual.
    id                 bigserial PRIMARY KEY,

    cuenta_id          bigint  NOT NULL REFERENCES cuentas_credito(id) ON DELETE CASCADE,
    paciente_id        integer NOT NULL REFERENCES pacientes(id),

    -- Desamarrar NO borra la fila, la desactiva.
    --
    -- Dos razones. Una: las citas ya cargadas apuntan a este par (ver la FK compuesta de abajo),
    -- asi que borrarlo fallaria mientras exista historia -- y si se pudiera borrar, se perderia
    -- el dato de quien respondia cuando se atendio. Dos: desamarrar significa "no cargues mas
    -- citas de este paciente aqui", no "esto nunca paso".
    activo             boolean NOT NULL DEFAULT true,

    created_at         timestamp NOT NULL DEFAULT now(),
    idusuario_creacion bigint,

    -- Un paciente no se amarra dos veces a la misma cuenta.
    CONSTRAINT uq_ccp_cuenta_paciente UNIQUE (cuenta_id, paciente_id),

    -- Objetivo de la FK compuesta de citas. Tiene que existir con las columnas en este orden.
    CONSTRAINT uq_ccp_paciente_cuenta UNIQUE (paciente_id, cuenta_id)
);

COMMENT ON TABLE cuenta_credito_pacientes IS
  'Que pacientes puede cargar cada cuenta de credito. Varios por cuenta: un terapeuta suele traer a su madre, su padre y su hijo, y lo paga junto.';

-- Un paciente activo en UNA sola cuenta.
--
-- Si estuviera en dos, al marcar la cita habria que preguntar a cual se carga, y el reporte de
-- "a quien esta amarrado este paciente" tendria dos respuestas. Parcial sobre activo: los
-- amarres viejos quedan, y un paciente puede cambiar de cuenta desactivando el anterior.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ccp_un_paciente_una_cuenta
    ON cuenta_credito_pacientes (paciente_id) WHERE activo;

-- ---------------------------------------------------------------------------------------------
-- 3. La cita cargada a una cuenta
-- ---------------------------------------------------------------------------------------------

ALTER TABLE citas
    ADD COLUMN IF NOT EXISTS cuenta_credito_id bigint REFERENCES cuentas_credito(id);

COMMENT ON COLUMN citas.cuenta_credito_id IS
  'Cuenta que responde por esta cita cuando se atendio sin pagar. NULL = cita normal.';

-- La base garantiza que la cita solo se cargue a una cuenta que cubra a ESE paciente.
--
-- Es la regla que mas facil se rompe desde codigo: cualquier camino nuevo que escriba
-- cuenta_credito_id sin revisar el amarre cargaria la deuda al titular equivocado, y eso recien
-- se nota cuando a alguien le cobran de mas a fin de mes. MATCH SIMPLE (el de omision) deja
-- pasar la fila si cuenta_credito_id es NULL, que es el caso de todas las citas normales.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_citas_credito_cubre_al_paciente') THEN
        ALTER TABLE citas
            ADD CONSTRAINT fk_citas_credito_cubre_al_paciente
            FOREIGN KEY (paciente_id, cuenta_credito_id)
            REFERENCES cuenta_credito_pacientes (paciente_id, cuenta_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_citas_cuenta_credito
    ON citas (cuenta_credito_id) WHERE cuenta_credito_id IS NOT NULL;

-- Una cita DE PAQUETE no se carga aqui: se carga el paquete (seccion 3b).
--
-- No es una limitacion, es donde vive el dinero. Una cita de paquete lleva precio y monto_pagado
-- propios (364 de las 726 en local), pero el paquete lleva ademas su monto_total y su
-- total_cobrado, y es contra el tratamiento que se cobra. Si el credito se pudiera poner en las
-- dos puntas, la deuda de la cuenta sumaria dos veces el mismo paquete.
--
-- Lo que debe es el paquete, no cada una de sus sesiones. Las citas sueltas y las masivas si se
-- cargan aqui: son independientes en precio y pago, y no hay tratamiento detras.
ALTER TABLE citas DROP CONSTRAINT IF EXISTS ck_citas_credito_solo_sueltas;
ALTER TABLE citas
    ADD CONSTRAINT ck_citas_credito_solo_sueltas
    CHECK (cuenta_credito_id IS NULL OR sesion_id IS NULL);

-- ---------------------------------------------------------------------------------------------
-- 3b. El paquete cargado a una cuenta
-- ---------------------------------------------------------------------------------------------
--
-- La clinica pidio poder dar credito al crear: una cita, un lote de citas masivas o un paquete.
-- Las dos primeras son citas sueltas y las cubre la seccion 3. El paquete se cubre aqui, en el
-- tratamiento, porque es ahi donde esta su deuda: monto_total - total_cobrado.

ALTER TABLE tratamientos
    ADD COLUMN IF NOT EXISTS cuenta_credito_id bigint REFERENCES cuentas_credito(id);

COMMENT ON COLUMN tratamientos.cuenta_credito_id IS
  'Cuenta que responde por este paquete cuando se vendio sin cobrar. NULL = paquete normal. La deuda es monto_total - total_cobrado.';

-- Mismo candado compuesto que en citas: el paquete solo se carga a una cuenta que cubra a ESE
-- paciente. MATCH SIMPLE deja pasar la fila cuando cuenta_credito_id es NULL.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tratamientos_credito_cubre_al_paciente') THEN
        ALTER TABLE tratamientos
            ADD CONSTRAINT fk_tratamientos_credito_cubre_al_paciente
            FOREIGN KEY (paciente_id, cuenta_credito_id)
            REFERENCES cuenta_credito_pacientes (paciente_id, cuenta_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_tratamientos_cuenta_credito
    ON tratamientos (cuenta_credito_id) WHERE cuenta_credito_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- 4. El estado de pago A_CREDITO
-- ---------------------------------------------------------------------------------------------
--
-- Un estado propio y no una bandera sobre SIN_PAGO. Hoy SIN_PAGO mezcla 642 citas en produccion
-- --247 anuladas, 170 inasistencias, 50 reprogramadas y 205 citas futuras-- y de ellas ninguna
-- es una deuda real: Asistida x SIN_PAGO da 0 sobre 1,898 atendidas. Si el credito cae ahi
-- dentro vuelve a ser imposible distinguir "lo debe a proposito" de "nadie lo cobro", que es
-- exactamente el dato que se pidio. Mismo criterio que DESCONTADA y DEVUELTO: cuando dos hechos
-- distintos comparten estado, el estado deja de servir para contarlos.

SELECT setval(pg_get_serial_sequence('cat_estados_pago_cita', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM cat_estados_pago_cita), 1));

-- Rojo vinoso: es deuda, y tiene que leerse como algo por cobrar. No de la familia ambar
-- (PARCIAL, DESCONTADA), que es dinero ya cobrado, ni parecido al violeta de A_FAVOR, que es su
-- opuesto exacto -- ahi el credito lo tiene el paciente, aqui lo tiene la clinica.
INSERT INTO cat_estados_pago_cita (key, nombre, color)
SELECT 'A_CREDITO', 'A credito', '#be123c'
WHERE NOT EXISTS (SELECT 1 FROM cat_estados_pago_cita WHERE key = 'A_CREDITO');

-- ---------------------------------------------------------------------------------------------
-- 5. Descuento de planilla como metodo de pago
-- ---------------------------------------------------------------------------------------------
--
-- La clinica pidio poder cobrar el credito "por descuento de sueldo o por el metodo de pago
-- seleccionado". Son la misma cosa: el descuento de planilla es un metodo mas, marcado como que
-- NO es dinero en caja (ver 2026-10-02-metodo-pago-cuenta-en-caja).
--
-- Asi no hay un segundo camino que programar. El pago se registra, la cita queda saldada, el
-- libro escribe su linea CONSTANCIA ("dados por pagados sin movimiento de caja") y el arqueo del
-- cierre no lo suma, porque no entro plata al cajon. Toda esa plomeria ya existe y funciona.
--
-- OJO con el nombre: DESCONTADA, el estado de pago, NO es esto. Ese significa que el paciente no
-- vino y la clinica se quedo con el dinero como penalidad. Son dos "descuentos" sin relacion.

SELECT setval(pg_get_serial_sequence('cat_metodos_pago', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 0) FROM cat_metodos_pago), 1));

INSERT INTO cat_metodos_pago (key, nombre, activo, cuenta_en_caja)
SELECT 'DESCUENTO_PLANILLA', 'Descuento de planilla', true, false
WHERE NOT EXISTS (SELECT 1 FROM cat_metodos_pago WHERE key = 'DESCUENTO_PLANILLA');

-- ---------------------------------------------------------------------------------------------
-- 6. El permiso de dar credito
-- ---------------------------------------------------------------------------------------------
--
-- Aparte del "crear" y del "editar" de Citas a proposito: dar credito no es agendar ni cobrar,
-- es decidir que la clinica se queda esperando el dinero. Mismo criterio que puede_marcar_cita
-- (2026-11-05).
--
-- Arranca solo en ADMIN y Encargado, que son los dos roles de supervision. Es una capacidad
-- NUEVA --nadie la tenia antes-- asi que no hay trabajo que se rompa por no darla de entrada, y
-- abrirla a Recepcionista desde Seguridad > Roles es un clic cuando la clinica lo decida.
--
-- NADIE SE DA CREDITO A SI MISMO. El permiso no alcanza para eso: un usuario no puede cargar una
-- cita contra su propia cuenta, lo tenga o no. Es la deuda de su familia, y quien se beneficia
-- no puede ser quien la autoriza. Esa regla va en el service, no aqui -- depende de quien esta
-- logueado, que la base no sabe.

ALTER TABLE cat_roles
    ADD COLUMN IF NOT EXISTS puede_dar_credito boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN cat_roles.puede_dar_credito IS
  'Si el rol puede atender una cita a credito (sin cobrarla) y administrar las cuentas de credito.';

UPDATE cat_roles
   SET puede_dar_credito = true
 WHERE UPPER(key) IN ('ADMIN', 'ENC')
   AND puede_dar_credito = false;

-- ---------------------------------------------------------------------------------------------
-- 7. El libro, por cuenta
-- ---------------------------------------------------------------------------------------------
--
-- asientos.paciente_id es NOT NULL y se queda asi: el asiento sigue siendo del paciente que se
-- atendio, para que su estado de cuenta se lea bien ("esta cita quedo a cargo de la cuenta de
-- Rocio"). Esta columna es lo que permite la otra vista -- sumar las tres deudas de los tres
-- familiares en el estado de cuenta de la cuenta -- sin reconstruirla pasando por los amarres.

ALTER TABLE asientos
    ADD COLUMN IF NOT EXISTS cuenta_credito_id bigint REFERENCES cuentas_credito(id);

COMMENT ON COLUMN asientos.cuenta_credito_id IS
  'Cuenta de credito a la que pertenece este movimiento, cuando lo hay. Permite el estado de cuenta de la cuenta sin dejar de ser un asiento del paciente.';

CREATE INDEX IF NOT EXISTS ix_asientos_cuenta_credito
    ON asientos (cuenta_credito_id) WHERE cuenta_credito_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Las citas que ya existen NO se tocan.
-- ---------------------------------------------------------------------------------------------
--
-- Se podria intentar adivinar cuales de las 642 SIN_PAGO eran en realidad un credito, pero no
-- hay con que: las que se cobraron con un efectivo que no entro figuran PAGADA y son
-- indistinguibles de un cobro real, y las que se dejaron en Programada no dicen a quien se le
-- iba a cobrar. Repartir eso a posteriori seria inventar la mitad. El dato nuevo empieza aqui.

COMMIT;

-- Verificacion.
SELECT 'estados de pago' AS que, key, nombre, color FROM cat_estados_pago_cita ORDER BY id;
SELECT 'metodos de pago' AS que, key, nombre, activo, cuenta_en_caja FROM cat_metodos_pago ORDER BY id;
SELECT 'roles' AS que, key, nombre, puede_dar_credito FROM cat_roles ORDER BY id;
SELECT 'columnas nuevas' AS que, table_name, column_name
  FROM information_schema.columns
 WHERE (table_name IN ('citas', 'asientos', 'tratamientos') AND column_name = 'cuenta_credito_id')
    OR (table_name = 'cat_roles' AND column_name = 'puede_dar_credito')
 ORDER BY table_name;
