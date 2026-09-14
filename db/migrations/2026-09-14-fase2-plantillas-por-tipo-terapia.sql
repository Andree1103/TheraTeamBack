-- FASE 2 — Las plantillas pasan a ser por TIPO DE TERAPIA, y tambien aplican a la atencion.
--
-- Dos cambios de diseño:
--
--   1. Antes la plantilla se ataba al AREA (solo 3: Kids, Física, Consultas Médicas), que es
--      demasiado grueso: una Evaluación Psicológica y una Terapia de Lenguaje son las dos del
--      área Kids y no preguntan lo mismo. Ahora se ata al TIPO DE TERAPIA (17), que ademas es
--      lo que ya lleva la cita — asi la plantilla de una atencion se resuelve sola.
--      Si un tipo no tiene plantilla propia se usa la generica (tipo_terapia_id nulo), para no
--      obligar a crear 17 fichas el primer dia.
--
--   2. La ficha de ATENCION tambien se configura. Antes el SOAP estaba fijo en el codigo.
--      Las columnas subjetivo/objetivo/analisis/plan se conservan (tienen datos cargados) y la
--      plantilla base de atencion reproduce exactamente esos cuatro campos.
--
-- Idempotente y aditiva salvo area_id, que se elimina: es de fase 2, no esta publicada y la
-- unica plantilla que existe no la usa.

BEGIN;

-- ── 1. La plantilla dice para QUE ficha es y a que tipo de terapia aplica ─────
ALTER TABLE hc_plantillas ADD COLUMN IF NOT EXISTS tipo varchar(20) NOT NULL DEFAULT 'HISTORIA';
ALTER TABLE hc_plantillas ADD COLUMN IF NOT EXISTS tipo_terapia_id integer REFERENCES tipos_terapia(id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage
                    WHERE constraint_name = 'hc_plantillas_tipo_valido') THEN
        ALTER TABLE hc_plantillas
            ADD CONSTRAINT hc_plantillas_tipo_valido CHECK (tipo IN ('HISTORIA','ATENCION'));
    END IF;
END $$;

-- El area se reemplaza por el tipo de terapia (ver cabecera).
ALTER TABLE hc_plantillas DROP COLUMN IF EXISTS area_id;

CREATE INDEX IF NOT EXISTS ix_hc_plantillas_tipo ON hc_plantillas (tipo, tipo_terapia_id);

-- ── 2. La atencion guarda sus valores igual que la historia ──────────────────
-- Los cuatro campos SOAP se conservan: son los que cargaron las atenciones anteriores.
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS plantilla_id integer REFERENCES hc_plantillas(id);
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS datos jsonb NOT NULL DEFAULT '{}'::jsonb;

-- ── 3. Plantilla base de ATENCION: el mismo SOAP, pero ya configurable ───────
DO $$
DECLARE
    v_plantilla integer;
    v_seccion   integer;
BEGIN
    SELECT id INTO v_plantilla FROM hc_plantillas WHERE nombre = 'Atención clínica (SOAP)';
    IF v_plantilla IS NOT NULL THEN
        RAISE NOTICE 'La plantilla base de atencion ya existe (id=%), no se toca.', v_plantilla;
        RETURN;
    END IF;

    INSERT INTO hc_plantillas (nombre, tipo, descripcion, activo, orden)
    VALUES ('Atención clínica (SOAP)', 'ATENCION',
            'Formato estándar para documentar una sesión. Duplícala y ajústala si un tipo de terapia necesita otros campos.',
            true, 0)
    RETURNING id INTO v_plantilla;

    INSERT INTO hc_secciones (plantilla_id, nombre, orden)
    VALUES (v_plantilla, 'Nota de la sesión', 0) RETURNING id INTO v_seccion;

    INSERT INTO hc_campos (seccion_id, clave, etiqueta, tipo, opciones, requerido, ayuda, orden) VALUES
      (v_seccion, 'subjetivo', 'S · Subjetivo', 'TEXTO_LARGO', NULL, false,
       'Lo que refiere el paciente: cómo llegó, qué molestias, qué cambió', 0),
      (v_seccion, 'objetivo',  'O · Objetivo',  'TEXTO_LARGO', NULL, false,
       'Lo que observas y mides: hallazgos, pruebas, qué se trabajó', 1),
      (v_seccion, 'analisis',  'A · Análisis',  'TEXTO_LARGO', NULL, false,
       'Tu interpretación clínica de lo anterior', 2),
      (v_seccion, 'plan',      'P · Plan',      'TEXTO_LARGO', NULL, false,
       'Qué sigue: objetivos de la próxima sesión, indicaciones para casa', 3);

    RAISE NOTICE 'Plantilla base de atencion creada (id=%).', v_plantilla;
END $$;

COMMIT;

-- Verificacion
SELECT p.tipo,
       p.nombre,
       COALESCE(tt.nombre, 'Todos los tipos') AS aplica_a,
       (SELECT count(*) FROM hc_secciones s WHERE s.plantilla_id = p.id) AS secciones,
       (SELECT count(*) FROM hc_campos c JOIN hc_secciones s2 ON s2.id = c.seccion_id
         WHERE s2.plantilla_id = p.id) AS campos
  FROM hc_plantillas p
  LEFT JOIN tipos_terapia tt ON tt.id = p.tipo_terapia_id
 ORDER BY p.tipo, p.orden, p.id;
