-- ═══════════════════════════════════════════════════════════════════════════
--  LIMPIEZA DE DATOS OPERATIVOS — dejar la base lista para arrancar en real.
--
--  BORRA:      citas, paquetes (tratamientos), sesiones, atenciones y sus
--              metricas, historial de citas, pagos y sus detalles, historial
--              de saldo a favor, lineas de venta y cierres de caja.
--
--  CONSERVA:   pacientes (con su ficha completa), terapeutas y sus horarios,
--              usuarios y roles, productos, plantillas de paquete, sedes,
--              configuracion y todos los catalogos (cat_*).
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
-- CASCADE es solo red de seguridad — ya se verifico que ninguna tabla fuera de esta
-- lista referencia a las de aca, asi que no arrastra nada inesperado.
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
    cierres_caja
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

COMMIT;
