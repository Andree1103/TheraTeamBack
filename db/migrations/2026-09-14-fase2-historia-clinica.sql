-- FASE 2 — Historia clinica, atencion SOAP y archivos adjuntos.
--
-- Aditiva: no borra ni renombra nada de lo que ya usa la version publicada. Idempotente:
-- se puede reaplicar sin romper.
--
-- Que agrega:
--   1. Plantillas de historia clinica configurables (por area), con secciones y campos.
--   2. La historia clinica del paciente, cuyos valores viven en un JSONB gobernado por la plantilla.
--   3. Los cuatro campos SOAP en la atencion clinica.
--   4. Archivos adjuntos genericos (atencion, historia o paciente).
--   5. Dos permisos nuevos por rol: ver y editar historia clinica.

BEGIN;

-- ── 1. Plantillas ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS hc_plantillas (
    id                     serial PRIMARY KEY,
    nombre                 varchar(120) NOT NULL,
    -- null = la plantilla sirve para cualquier area
    area_id                integer REFERENCES cat_areas(id),
    descripcion            text,
    activo                 boolean NOT NULL DEFAULT true,
    orden                  integer NOT NULL DEFAULT 0,
    created_at             timestamp NOT NULL DEFAULT now(),
    updated_at             timestamp NOT NULL DEFAULT now(),
    idusuario_creacion     bigint,
    idusuario_modificacion bigint
);

CREATE TABLE IF NOT EXISTS hc_secciones (
    id           serial PRIMARY KEY,
    plantilla_id integer NOT NULL REFERENCES hc_plantillas(id) ON DELETE CASCADE,
    nombre       varchar(120) NOT NULL,
    orden        integer NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_hc_secciones_plantilla ON hc_secciones (plantilla_id, orden);

-- `clave` es la llave estable con la que el valor se guarda en historias_clinicas.datos:
-- cambiar la etiqueta no debe perder lo ya cargado, por eso son cosas distintas.
CREATE TABLE IF NOT EXISTS hc_campos (
    id         serial PRIMARY KEY,
    seccion_id integer NOT NULL REFERENCES hc_secciones(id) ON DELETE CASCADE,
    clave      varchar(60) NOT NULL,
    etiqueta   varchar(150) NOT NULL,
    tipo       varchar(20) NOT NULL,
    opciones   text[],
    requerido  boolean NOT NULL DEFAULT false,
    ayuda      varchar(255),
    orden      integer NOT NULL DEFAULT 0,
    CONSTRAINT hc_campos_clave_unica UNIQUE (seccion_id, clave),
    CONSTRAINT hc_campos_tipo_valido CHECK (tipo IN
        ('TEXTO','TEXTO_LARGO','NUMERO','FECHA','BOOLEANO','SELECT','MULTISELECT'))
);
CREATE INDEX IF NOT EXISTS ix_hc_campos_seccion ON hc_campos (seccion_id, orden);

-- ── 2. Historia clinica del paciente ─────────────────────────────────────────
-- Un paciente puede tener una historia por plantilla (ej. una de Fisica y otra de Psicologia).
-- Los valores van en JSONB y no en una tabla campo-valor: la plantilla ya define la forma,
-- y asi agregar un campo no obliga a migrar filas.
CREATE TABLE IF NOT EXISTS historias_clinicas (
    id                     serial PRIMARY KEY,
    paciente_id            integer NOT NULL REFERENCES pacientes(id),
    plantilla_id           integer NOT NULL REFERENCES hc_plantillas(id),
    datos                  jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at             timestamp NOT NULL DEFAULT now(),
    updated_at             timestamp NOT NULL DEFAULT now(),
    idusuario_creacion     bigint,
    idusuario_modificacion bigint,
    CONSTRAINT historias_paciente_plantilla_unica UNIQUE (paciente_id, plantilla_id)
);
CREATE INDEX IF NOT EXISTS ix_historias_paciente ON historias_clinicas (paciente_id);

-- ── 3. Atencion en formato SOAP ──────────────────────────────────────────────
-- notas_post se conserva: es lo que ya cargaron las atenciones existentes.
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS subjetivo text;
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS objetivo  text;
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS analisis  text;
ALTER TABLE atencion_clinica ADD COLUMN IF NOT EXISTS plan      text;

-- ── 4. Archivos adjuntos ─────────────────────────────────────────────────────
-- El binario NO va en la base: vive en el disco del servidor y aca queda solo el metadato.
-- nombre_guardado es un uuid + extension, generado por el backend: el nombre que subio el
-- usuario nunca toca el filesystem (evita colisiones y rutas maliciosas).
CREATE TABLE IF NOT EXISTS archivos (
    id                 serial PRIMARY KEY,
    entidad_tipo       varchar(20) NOT NULL,
    entidad_id         integer NOT NULL,
    nombre_original    varchar(255) NOT NULL,
    nombre_guardado    varchar(120) NOT NULL UNIQUE,
    mime               varchar(100) NOT NULL,
    tamano_bytes       bigint NOT NULL,
    descripcion        varchar(255),
    created_at         timestamp NOT NULL DEFAULT now(),
    idusuario_creacion bigint,
    CONSTRAINT archivos_entidad_valida CHECK (entidad_tipo IN ('ATENCION','HISTORIA','PACIENTE'))
);
CREATE INDEX IF NOT EXISTS ix_archivos_entidad ON archivos (entidad_tipo, entidad_id);

-- ── 5. Permisos por rol ──────────────────────────────────────────────────────
ALTER TABLE cat_roles ADD COLUMN IF NOT EXISTS puede_ver_historia    boolean NOT NULL DEFAULT false;
ALTER TABLE cat_roles ADD COLUMN IF NOT EXISTS puede_editar_historia boolean NOT NULL DEFAULT false;

-- Admin y terapeutas arrancan con acceso; el resto se habilita desde Seguridad > Roles.
UPDATE cat_roles SET puede_ver_historia = true, puede_editar_historia = true
 WHERE UPPER(key) IN ('ADMIN','TERAPEUTA') OR UPPER(nombre) LIKE 'ADMINISTRADOR%';

COMMIT;

-- Verificacion
SELECT 'tablas nuevas' AS que, string_agg(table_name, ', ' ORDER BY table_name) AS detalle
  FROM information_schema.tables
 WHERE table_schema='public' AND table_name IN ('hc_plantillas','hc_secciones','hc_campos','historias_clinicas','archivos')
UNION ALL
SELECT 'SOAP en atencion', string_agg(column_name, ', ' ORDER BY column_name)
  FROM information_schema.columns
 WHERE table_name='atencion_clinica' AND column_name IN ('subjetivo','objetivo','analisis','plan')
UNION ALL
SELECT 'permisos por rol', string_agg(nombre||'='||puede_ver_historia, ', ' ORDER BY id) FROM cat_roles;
