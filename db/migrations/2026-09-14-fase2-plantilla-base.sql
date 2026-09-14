-- FASE 2 — Plantilla de historia clinica inicial.
--
-- Una plantilla generica (sin area) para que la ficha no arranque en blanco el primer dia.
-- Desde Configuraciones > Historia clinica se puede editar, duplicar por area o desactivar.
--
-- Idempotente: si ya existe una plantilla con ese nombre no vuelve a crearla, asi reaplicar
-- la migracion no duplica secciones ni campos.

BEGIN;

DO $$
DECLARE
    v_plantilla integer;
    v_seccion   integer;
BEGIN
    SELECT id INTO v_plantilla FROM hc_plantillas WHERE nombre = 'Historia clínica general';
    IF v_plantilla IS NOT NULL THEN
        RAISE NOTICE 'La plantilla base ya existe (id=%), no se toca.', v_plantilla;
        RETURN;
    END IF;

    INSERT INTO hc_plantillas (nombre, descripcion, activo, orden)
    VALUES ('Historia clínica general',
            'Ficha base para cualquier área. Duplícala y ajústala si un área necesita otros campos.',
            true, 0)
    RETURNING id INTO v_plantilla;

    -- ── Antecedentes ─────────────────────────────────────────────────────────
    INSERT INTO hc_secciones (plantilla_id, nombre, orden)
    VALUES (v_plantilla, 'Antecedentes', 0) RETURNING id INTO v_seccion;

    INSERT INTO hc_campos (seccion_id, clave, etiqueta, tipo, opciones, requerido, ayuda, orden) VALUES
      (v_seccion, 'motivo_consulta',    'Motivo de consulta',        'TEXTO_LARGO', NULL, true,
       'Por qué llega el paciente, en sus propias palabras', 0),
      (v_seccion, 'antecedentes_medicos','Antecedentes médicos',     'TEXTO_LARGO', NULL, false,
       'Cirugías, enfermedades previas, hospitalizaciones', 1),
      (v_seccion, 'medicacion_actual',  'Medicación actual',         'TEXTO_LARGO', NULL, false, NULL, 2),
      (v_seccion, 'alergias',           'Alergias',                  'TEXTO',       NULL, false,
       'Medicamentos, alimentos, materiales', 3),
      (v_seccion, 'cirugias_previas',   'Cirugías previas',          'BOOLEANO',    NULL, false, NULL, 4);

    -- ── Evaluación inicial ───────────────────────────────────────────────────
    INSERT INTO hc_secciones (plantilla_id, nombre, orden)
    VALUES (v_plantilla, 'Evaluación inicial', 1) RETURNING id INTO v_seccion;

    INSERT INTO hc_campos (seccion_id, clave, etiqueta, tipo, opciones, requerido, ayuda, orden) VALUES
      (v_seccion, 'fecha_evaluacion',   'Fecha de evaluación',       'FECHA',       NULL, false, NULL, 0),
      (v_seccion, 'diagnostico',        'Diagnóstico / impresión',   'TEXTO_LARGO', NULL, false, NULL, 1),
      (v_seccion, 'dolor_inicial',      'Dolor inicial (0-10)',      'NUMERO',      NULL, false,
       '0 = sin dolor, 10 = el peor imaginable', 2),
      (v_seccion, 'objetivos',          'Objetivos del tratamiento', 'TEXTO_LARGO', NULL, false, NULL, 3),
      (v_seccion, 'derivado_por',       'Derivado por',              'SELECT',
       ARRAY['Médico tratante','Otro terapeuta','Familiar o conocido','Viene por su cuenta'], false, NULL, 4);

    -- ── Datos relevantes ─────────────────────────────────────────────────────
    INSERT INTO hc_secciones (plantilla_id, nombre, orden)
    VALUES (v_plantilla, 'Datos relevantes', 2) RETURNING id INTO v_seccion;

    INSERT INTO hc_campos (seccion_id, clave, etiqueta, tipo, opciones, requerido, ayuda, orden) VALUES
      (v_seccion, 'ocupacion',          'Ocupación',                 'TEXTO',       NULL, false, NULL, 0),
      (v_seccion, 'actividad_fisica',   'Actividad física',          'SELECT',
       ARRAY['Sedentario','Ocasional','Regular','Deportista'], false, NULL, 1),
      (v_seccion, 'habitos',            'Hábitos a considerar',      'MULTISELECT',
       ARRAY['Fuma','Consume alcohol','Mala postura sostenida','Trabajo de pie','Trabajo sentado'], false,
       'Se pueden marcar varios', 2),
      (v_seccion, 'contacto_emergencia','Contacto de emergencia',    'TEXTO',       NULL, false,
       'Nombre y teléfono', 3),
      (v_seccion, 'observaciones',      'Observaciones generales',   'TEXTO_LARGO', NULL, false, NULL, 4);

    RAISE NOTICE 'Plantilla base creada (id=%).', v_plantilla;
END $$;

COMMIT;

-- Verificacion
SELECT p.nombre AS plantilla,
       s.nombre AS seccion,
       count(c.id) AS campos
  FROM hc_plantillas p
  JOIN hc_secciones s ON s.plantilla_id = p.id
  LEFT JOIN hc_campos c ON c.seccion_id = s.id
 GROUP BY p.nombre, s.nombre, s.orden
 ORDER BY s.orden;
