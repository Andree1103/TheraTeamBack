-- ╔══════════════════════════════════════════════════════════════════════════════════════╗
-- ║  BORRADO MASIVO — DEJA SOLO PACIENTES Y SUS HORARIOS FIJOS                            ║
-- ║  Esto NO es una migración. No se aplica sola: se ejecuta a mano, una vez, a sabiendas.║
-- ╚══════════════════════════════════════════════════════════════════════════════════════╝
--
-- QUÉ BORRA: citas, pagos, paquetes (tratamientos y sus sesiones), atenciones y cierres de caja,
-- con todo lo que cuelga de ellos: métricas de atención, historial de citas, movimientos de saldo,
-- líneas de venta y el reparto de pagos por sesión.
--
-- QUÉ CONSERVA: pacientes, sus horarios fijos, terapeutas y sus horarios, usuarios, roles,
-- permisos, catálogos, productos, plantillas de paquete, sedes y la configuración del negocio.
--
-- POR QUÉ ESTE ORDEN: hay un ciclo entre citas y sesiones (citas.sesion_id → sesiones y
-- sesiones.cita_activa_id → citas). Si se intenta borrar cualquiera de las dos primero, la otra
-- la retiene. Se rompe el ciclo poniendo a NULL las dos referencias antes de borrar nada.
-- citas.reprogramacion_de apunta a la propia tabla, y también hay que soltarlo.
--
-- EL SALDO A FAVOR SE PONE A CERO: es dinero que existía porque existían unos pagos. Si se
-- borran los pagos y se deja el saldo, los pacientes quedan con crédito que no respalda nada —
-- y al cobrarles se les descontaría solo. Por eso va en la misma transacción, no después.
--
-- ES UNA SOLA TRANSACCIÓN: o se hace todo o no se hace nada. A medias dejaría la base peor que
-- antes de empezar.
--
-- ANTES DE EJECUTAR, HAZ EL BACKUP. No hay deshacer.

BEGIN;

-- 1. Lo que cuelga de las atenciones.
DELETE FROM atencion_metricas;
DELETE FROM atencion_clinica;

-- 2. Bitácora de cambios de estado de las citas.
DELETE FROM cita_historial;

-- 3. Movimientos de saldo: se van con los pagos que los produjeron.
DELETE FROM saldo_movimientos;

-- 4. Lo que cuelga de los pagos.
--    OJO: venta_items guarda qué productos se vendieron. Borrarlas NO devuelve el stock al
--    almacén; si quieres recomponerlo, ajústalo a mano en Productos después.
DELETE FROM venta_items;
DELETE FROM pago_sesiones;

-- 5. Los pagos.
DELETE FROM pagos;

-- 6. Se rompe el ciclo citas ↔ sesiones antes de borrar ninguna de las dos.
UPDATE sesiones SET cita_activa_id = NULL;
UPDATE citas    SET sesion_id = NULL, reprogramacion_de = NULL;

-- 7. Citas, y después sesiones y paquetes.
DELETE FROM citas;
DELETE FROM sesiones;
DELETE FROM tratamientos;

-- 8. Caja: los arqueos cerrados ya no cuadran contra nada.
DELETE FROM cierres_caja;

-- 9. El saldo a favor deja de existir junto con los pagos que lo crearon.
UPDATE pacientes SET saldo_a_favor = 0 WHERE COALESCE(saldo_a_favor, 0) <> 0;

COMMIT;

-- ── Comprobación: lo de arriba debe quedar en 0; lo de abajo, intacto ────────────────────
SELECT 'citas'              AS tabla, count(*) FROM citas
UNION ALL SELECT 'pagos',              count(*) FROM pagos
UNION ALL SELECT 'tratamientos',       count(*) FROM tratamientos
UNION ALL SELECT 'sesiones',           count(*) FROM sesiones
UNION ALL SELECT 'atencion_clinica',   count(*) FROM atencion_clinica
UNION ALL SELECT 'atencion_metricas',  count(*) FROM atencion_metricas
UNION ALL SELECT 'cita_historial',     count(*) FROM cita_historial
UNION ALL SELECT 'saldo_movimientos',  count(*) FROM saldo_movimientos
UNION ALL SELECT 'pago_sesiones',      count(*) FROM pago_sesiones
UNION ALL SELECT 'venta_items',        count(*) FROM venta_items
UNION ALL SELECT 'cierres_caja',       count(*) FROM cierres_caja
UNION ALL SELECT '--- se conservan ---', NULL
UNION ALL SELECT 'pacientes',          count(*) FROM pacientes
UNION ALL SELECT 'paciente_horario_fijo', count(*) FROM paciente_horario_fijo
UNION ALL SELECT 'pacientes con saldo <> 0', count(*) FROM pacientes WHERE COALESCE(saldo_a_favor,0) <> 0
UNION ALL SELECT 'terapeutas',         count(*) FROM terapeutas
UNION ALL SELECT 'terapeuta_horario',  count(*) FROM terapeuta_horario
UNION ALL SELECT 'usuarios',           count(*) FROM usuarios
UNION ALL SELECT 'productos',          count(*) FROM productos
UNION ALL SELECT 'tipos_terapia',      count(*) FROM tipos_terapia;
