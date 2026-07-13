# Resumen ejecutivo — TheraTeam

Documento de referencia de lo implementado y desplegado. Fecha: 2026-07-12.

## 1. Alcance funcional entregado

### Módulo de citas
- Calendario semanal por terapeuta con chequeo real de disponibilidad (horarios y excepciones del terapeuta).
- Al haber más de una cita en la misma franja horaria (aunque sean de terapeutas distintos), se colapsan en un badge "N citas / Ver detalle" con modal de selección, en vez de amontonar chips ilegibles.
- Hover con tarjeta de detalle posicionada dinámicamente según el viewport (sin recortes ni desfases).
- Edición de citas: el terapeuta asignado siempre aparece disponible en el selector aunque la cita sea legacy (tipo/área inconsistente con los datos actuales).

### Catálogo de terapias
- Se eliminó el catálogo "Tipo de terapeuta" (redundante con Área + Especialidades).
- "Tipo de terapia" ahora depende de **Área** (relación `área → tipo de terapia`), con campos nuevos: especialidad, sesiones sugeridas, comentario.
- Se cargaron los 17 tipos de terapia del catálogo del cliente (Kids, Física, Consultas Médicas) con sus reglas de duración, capacidad y sesiones.
- Modal "Nueva cita": selección en cascada Área → Tipo de terapia (reemplaza las 3 pestañas fijas anteriores, que no escalaban).
- Configuraciones → "Tipos de terapia": filtro por área + tabla con scroll/paginado (antes solo mostraba 16 filas sin forma de ver el resto).

## 2. Despliegue a producción

| Componente | Plataforma | URL |
|---|---|---|
| Backend (Spring Boot + Postgres) | Railway | `https://therateamback-production.up.railway.app` |
| Base de datos | Railway (Postgres administrado) | red privada `postgres.railway.internal` |
| Frontend (Angular) | Netlify | según dominio configurado en Netlify |

Pasos realizados:
1. Parametrización de configuración sensible (antes hardcodeada a `localhost`): `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `PORT`, `CORS_ALLOWED_ORIGINS` ahora se leen de variables de entorno.
2. Repo backend conectado a Railway (deploy automático por push a `main`).
3. Provisión de Postgres administrado en Railway.
4. Exportación (`pg_dump`) de la base de datos local y restauración (`pg_restore`) en Railway — se migró el 100% del esquema y los datos (76 citas, 17 tipos de terapia, 3 terapeutas, 4 usuarios, verificado por conteo).
5. Generación de dominio público del backend en Railway y actualización de `environment.prod.ts` del frontend para apuntar ahí.
6. Fix de un error de build en Netlify: el budget de tamaño de CSS por componente (`angular.json`) cortaba el build porque `lista-citas.component.css` creció con los nuevos estilos de hover/multi-cita; se subió el límite de error de 16kb a 35kb.

## 3. Decisiones técnicas relevantes

- **No se usa Flyway/Liquibase** (`ddl-auto=none`): cualquier cambio de esquema futuro debe aplicarse manualmente contra la base de producción (documentado en el README del backend).
- La tabla/entidad de "tipo de terapeuta" se dejó intacta en la base de datos (no se borró), solo se removió su uso en el frontend — decisión de bajo riesgo por si se necesita revertir.
- CORS restringido por variable de entorno a los orígenes explícitos del frontend (Netlify), no abierto (`*`).

## 4. Pendientes / recomendaciones a futuro

- Configurar dominio propio (`api.therateam.com` u otro) en vez del dominio autogenerado de Railway, si se desea marca propia.
- Evaluar mover a Flyway/Liquibase para versionar cambios de esquema y evitar el proceso manual actual.
- Configurar backups automáticos periódicos de la base de producción (Railway lo ofrece en la pestaña **Backups** del servicio Postgres).
