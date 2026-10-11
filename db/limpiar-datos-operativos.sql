-- ═══════════════════════════════════════════════════════════════════════════
--  LIMPIEZA DE DATOS OPERATIVOS — dejar la base lista para arrancar en real.
--
--  BORRA:      citas, paquetes (tratamientos), sesiones, atenciones y sus
--              metricas, historial de citas, pagos y sus detalles, historial
--              de saldo a favor, lineas de venta, cierres de caja, el libro de
--              movimientos (asientos y sus lineas) y el historial de revisiones.
--
--  CONSERVA:   pacientes (con su ficha completa y sus HORARIOS FIJOS), terapeutas
--              con sus horarios y excepciones, usuarios y roles, productos,
--              plantillas de paquete, sedes, configuracion y los catalogos (cat_*).
--
--  IRREVERSIBLE. Hacer backup antes:
--     docker compose exec -T db pg_dump -U postgres -d BDClinicaSAAS -F c > ~/backups/antes-limpieza.dump
--
--  Se ejecuta dentro de una transaccion: si algo falla, no se borra nada.
-- ═══════════════════════════════════════════════════════════════════════════

BEGIN;

-- Antes: cuanto hay.
SELECT 'ANTES' AS momento,
       (SELECT count(*) FROM citas)             AS citas,
       (SELECT count(*) FROM tratamientos)      AS paquetes,
       (SELECT count(*) FROM pagos)             AS pagos,
       (SELECT count(*) FROM atencion_clinica)  AS atenciones,
       (SELECT count(*) FROM pacientes)         AS pacientes,
       (SELECT count(*) FROM terapeutas)        AS terapeutas;

-- Un solo TRUNCATE con todas las tablas involucradas: resuelve por si mismo el
-- orden de las llaves foraneas, incluido el ciclo citas.sesion_id <-> sesiones.cita_activa_id
-- y la autorreferencia citas.reprogramacion_de, que a punta de DELETE obligan a
-- soltar las puntas con UPDATE antes de poder borrar nada.
--
-- RESTART IDENTITY reinicia los contadores de id: la primera cita real vuelve a ser la 1.
--
-- CASCADE es red de seguridad, NO la forma de barrer el libro. Las tres tablas nuevas van
-- nombradas a proposito: asiento_lineas apunta a citas y tratamientos, y asientos a pagos, asi
-- que CASCADE las habria vaciado igual — en silencio. Un borrado destructivo que depende de un
-- efecto colateral es un borrado que nadie puede revisar leyendolo.
--
-- El libro TIENE que irse con los pagos. Es el registro de ese dinero: dejarlo seria quedarse
-- con los asientos de cobros que ya no existen, y el reconciliador de cada noche cantaria
-- descuadres de un mundo borrado.
--
-- reconciliaciones tambien: su historial dice "cuadraba el 9 de octubre" sobre datos que ya no
-- estan. La racha arranca de cero con la base nueva, que es lo honesto.
TRUNCATE TABLE
    atencion_metricas,
    atencion_clinica,
    cita_historial,
    venta_items,
    saldo_movimientos,
    pago_sesiones,
    pagos,
    citas,
    sesiones,
    tratamientos,
    cierres_caja,
    asiento_lineas,
    asientos,
    reconciliaciones
RESTART IDENTITY CASCADE;

-- El saldo a favor vive como un numero en el paciente, no como suma de los pagos:
-- si no se pone en cero, quedan pacientes con credito que ya no respalda ningun pago.
UPDATE pacientes SET saldo_a_favor = 0 WHERE COALESCE(saldo_a_favor, 0) <> 0;

-- Despues: comprobar que quedo en cero lo que debia, y que lo demas sigue ahi.
SELECT 'DESPUES' AS momento,
       (SELECT count(*) FROM citas)             AS citas,
       (SELECT count(*) FROM tratamientos)      AS paquetes,
       (SELECT count(*) FROM pagos)             AS pagos,
       (SELECT count(*) FROM atencion_clinica)  AS atenciones,
       (SELECT count(*) FROM pacientes)         AS pacientes,
       (SELECT count(*) FROM terapeutas)        AS terapeutas;

SELECT 'saldo a favor pendiente' AS control,
       COALESCE(SUM(saldo_a_favor), 0) AS debe_ser_cero
FROM pacientes;

-- Lo que TIENE que seguir ahi. Si alguno sale en cero, algo se llevo por delante lo que no era.
SELECT 'lo que se conserva' AS control,
       (SELECT count(*) FROM pacientes)             AS pacientes,
       (SELECT count(*) FROM paciente_horario_fijo) AS horarios_fijos_de_pacientes,
       (SELECT count(*) FROM terapeutas)            AS terapeutas,
       (SELECT count(*) FROM terapeuta_horario)     AS horarios_de_terapeutas,
       (SELECT count(*) FROM plantillas_paquete)    AS plantillas,
       (SELECT count(*) FROM productos)             AS productos;

-- El libro vacio: asi la primera revision nocturna sobre la base nueva arranca cuadrando.
SELECT 'libro' AS control,
       (SELECT count(*) FROM asientos)       AS asientos,
       (SELECT count(*) FROM asiento_lineas) AS lineas;

COMMIT;
