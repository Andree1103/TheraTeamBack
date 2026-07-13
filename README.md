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

Aplica el mismo cambio también en producción (contra la base de Railway) antes o después de desplegar el código que lo usa, según corresponda.

## Despliegue (Railway)

El backend está desplegado en Railway: `https://therateamback-production.up.railway.app`.

- Railway detecta el proyecto Maven automáticamente y lo compila con Nixpacks (no requiere Dockerfile).
- Deploy automático en cada push a `main`.
- Variables de entorno configuradas en el servicio (pestaña **Variables**): `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (referenciando el servicio de Postgres con `${{Postgres.PGHOST}}` etc.), y `CORS_ALLOWED_ORIGINS` apuntando al dominio de Netlify del frontend.
- **Importante**: en Railway, después de agregar/editar variables hay que confirmar el botón **"Deploy"** ("Apply changes") — guardarlas solas no redeploya el servicio.

### Backup / restore de la base de datos

Exportar local:
```bash
pg_dump -h localhost -U postgres -d BDClinicaSAAS -F c -f backup.dump
```

Restaurar en Railway (usa el host/puerto del **TCP Proxy público**, en Postgres → Settings → Networking):
```bash
pg_restore -h <host-proxy-railway> -p <puerto-proxy> -U <PGUSER> -d <PGDATABASE> \
  --no-owner --no-privileges -v backup.dump
```
`--no-owner --no-privileges` evita errores porque el usuario dueño de las tablas en local no existe igual en Railway.

## Estructura relevante

- `src/main/java/com/therateam/therateam/config/CorsConfig.java` — configuración de CORS (lee `CORS_ALLOWED_ORIGINS`).
- `src/main/resources/application.properties` — configuración de datasource (parametrizada por variables de entorno).
- `RESUMEN_EJECUTIVO.md` — resumen de todo lo implementado y desplegado en este proyecto.
