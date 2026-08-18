# TheraTeam — Backend

API REST en Spring Boot para el sistema de gestión de citas y terapias de TheraTeam.

## Stack

- Java 21
- Spring Boot 4.0.6 (Web, Data JPA)
- PostgreSQL
- Maven (con wrapper `mvnw`)

## Requisitos previos

- JDK 21
- PostgreSQL corriendo localmente (o acceso a una instancia remota)
- No hace falta instalar Maven: usa `./mvnw` (Linux/Mac) o `mvnw.cmd` (Windows), incluido en el repo.

## Configuración local

1. Crea la base de datos en tu Postgres local:
   ```sql
   CREATE DATABASE "BDClinicaSAAS";
   ```
2. El proyecto usa `spring.jpa.hibernate.ddl-auto=none` — **no genera el esquema automáticamente ni usa Flyway/Liquibase**. El esquema debe existir de antemano (pide un dump al equipo, o restaura desde un backup de producción — ver sección de Base de datos más abajo).
3. Variables de entorno (todas tienen un valor por defecto pensado para desarrollo local, así que sin configurar nada ya apunta a `localhost:5432/BDClinicaSAAS` con usuario `postgres`):

   | Variable | Default local | Descripción |
   |---|---|---|
   | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/BDClinicaSAAS` | URL JDBC completa |
   | `DATABASE_USERNAME` | `postgres` | usuario de Postgres |
   | `DATABASE_PASSWORD` | (ver `application.properties`) | password de Postgres |
   | `PORT` | `8080` | puerto en el que levanta el backend |
   | `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | orígenes permitidos, separados por coma si son varios |

   Para sobreescribirlas localmente sin tocar `application.properties`, exporta las variables antes de levantar el server, ej. (PowerShell):
   ```powershell
   $env:DATABASE_PASSWORD = "tu-password"
   ```

## Levantar el proyecto

```bash
./mvnw spring-boot:run
```

El backend queda escuchando en `http://localhost:8080`. Los endpoints están bajo `/api/**` (ej. `/api/cat-areas`, `/api/citas`, `/api/tipos-terapia`).

## Build

```bash
./mvnw clean package
java -jar target/therateam-0.0.1-SNAPSHOT.jar
```

## Cambios de esquema (no hay migraciones automáticas)

Como no hay Flyway/Liquibase, cualquier `ALTER TABLE`/nueva columna/tabla se aplica manualmente. Patrón usado en este proyecto:

1. Crea una clase temporal en `src/main/java/com/therateam/therateam/tmp/` con un `main()` que abra conexión JDBC y ejecute el DDL (`ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`).
2. Compila: `./mvnw -q compile`
3. Ejecuta:
   ```bash
   ./mvnw -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
     -Dexec.mainClass=com.therateam.therateam.tmp.TuClase \
     -Dexec.classpathScope=runtime
   ```
   (usa `classpathScope=runtime`, no `compile` — si no, no encuentra el driver de Postgres).
4. Borra la clase temporal y recompila.
5. Reinicia el backend para que tome el nuevo esquema.

El mismo cambio hay que aplicarlo también en producción. Como `ddl-auto=none`, Hibernate **no** crea columnas solo: si el código nuevo llega antes que la migración, el backend arranca pero falla en runtime al tocar esa tabla. Ver "Migraciones de esquema" más abajo.

## Despliegue (VPS por SSH)

El backend corre en un VPS (`49.13.196.23`) con Docker Compose: Postgres + backend + Caddy (TLS automático vía `49.13.196.23.sslip.io`). El frontend va aparte, en Cloudflare Pages, con deploy automático en cada push a `main` de su repo.

El despliegue **no** es automático: se hace por SSH.

```bash
ssh root@49.13.196.23
cd /ruta/del/repo && ./deploy.sh
```

`deploy.sh` hace, en orden: backup de la BD → `git pull` → aplica las migraciones de `db/migrations/` → `docker compose up -d --build backend` → espera a que la API responda. Aborta si el backup sale vacío, y si el backend no levanta imprime el comando de restore.

Variables de entorno: en el archivo `.env` del servidor, junto al `docker-compose.yml` (ver `.env.example`). No está en git.

### Migraciones de esquema

Cada cambio de esquema va como un `.sql` nuevo en `db/migrations/`, con fecha en el nombre. Se escriben **idempotentes** (`ADD COLUMN IF NOT EXISTS`) porque `deploy.sh` reaplica todos los archivos del directorio en cada despliegue.

Las columnas aditivas (nullable o con `DEFAULT`) se pueden aplicar con el backend viejo todavía corriendo — las ignora. Un cambio destructivo (borrar/renombrar columna) sí necesita coordinarse con el reinicio.

### Backup / restore de la base de datos

`deploy.sh` ya hace un backup antes de cada despliegue, en `~/backups/` del servidor. Manualmente:

```bash
docker compose exec -T db pg_dump -U postgres -d BDClinicaSAAS -F c > backup.dump
```

Restaurar:
```bash
docker compose exec -T db pg_restore -U postgres -d BDClinicaSAAS --clean --no-owner < backup.dump
```
`--no-owner` evita errores cuando el usuario dueño de las tablas en el dump no existe igual en el destino.

## Estructura relevante

- `src/main/java/com/therateam/therateam/config/CorsConfig.java` — configuración de CORS (lee `CORS_ALLOWED_ORIGINS`).
- `src/main/resources/application.properties` — configuración de datasource (parametrizada por variables de entorno).
- `RESUMEN_EJECUTIVO.md` — resumen de todo lo implementado y desplegado en este proyecto.
