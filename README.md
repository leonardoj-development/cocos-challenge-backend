# Cocos challenge — API de inversiones

## Índice

- [Requisitos](#requisitos)
- [Cómo levantar la aplicación](#cómo-levantar-la-aplicación)
- [Configuración](#configuración)
- [Pruebas](#pruebas)
- [Colección de Postman](#colección-de-postman)
- [Documentación](#documentación)

API REST para gestionar el portfolio de inversiones de un usuario. Permite:

- **Consultar el portfolio:** valor total de la cuenta, pesos disponibles y posiciones con su rendimiento.
- **Buscar instrumentos** por ticker y/o nombre.
- **Enviar órdenes** de compra o venta (`MARKET` y `LIMIT`) y **cancelar** las que están pendientes.

**Stack:** Java 21, Spring Boot 3, Spring Data JPA, MapStruct, PostgreSQL 16 y Flyway. Arquitectura
hexagonal (Ports & Adapters) con patrón Strategy para las reglas de negocio.

## Requisitos

| Herramienta | Para qué |
|-------------|----------|
| Docker y Docker Compose | Levantar la aplicación y la base de datos con los scripts, y proveer la base efímera de los tests de integración. |
| Java 21 | Compilar y ejecutar la aplicación con Maven o IntelliJ. |
| Maven 3.9+ | Compilar, ejecutar y correr los tests desde la terminal. |
| PostgreSQL 16 | Base de datos de la aplicación; se puede usar la del contenedor o una propia. |

## Cómo levantar la aplicación

### Con los scripts (Docker)

Los scripts usan **Docker Compose**: levantan PostgreSQL y la API en contenedores, usando la configuración
de `.env`. No hace falta instalar Java ni PostgreSQL.

```bash
./scripts/start.sh    # Levanta PostgreSQL y la API (construye la imagen) y espera a que respondan
./scripts/stop.sh     # Detiene los servicios y conserva los datos
./scripts/clean.sh    # Elimina contenedores y volumen de datos, previa confirmación (--force la omite)
docker compose logs -f api
```

- La API queda en `http://localhost:8080` y PostgreSQL en `localhost:5432`, ambos accesibles solo desde la
  máquina local. Entre contenedores, la API se conecta a `postgres:5432`.
- Al arrancar sobre una base vacía, Flyway ejecuta `V1` (esquema), `V2` (datos de referencia) y `V3`
  (restricciones e índices).
- `start.sh` reconstruye la imagen de la API en cada ejecución, para incluir cambios de código y migraciones.
- El volumen `postgres_data` persiste al detener los servicios y Flyway no repite migraciones ya aplicadas.
  PostgreSQL toma las credenciales de `.env` solo al crear el volumen: para cambiarlas hay que recrearlo
  con `clean.sh`, lo que **elimina todos los datos**.

### Con Maven

Requiere una base PostgreSQL disponible. La forma más simple es levantar solo la base con Docker:

```bash
docker compose up -d --wait postgres
```

Luego, cargar las variables y ejecutar la API:

```bash
set -a; source .env; set +a
mvn spring-boot:run
```

Para generar y ejecutar el jar:

```bash
mvn -DskipTests package
java -jar target/challenge-1.0.0-SNAPSHOT.jar
```

Para usar la base externa del challenge, cargar `.env.external-db` en lugar de `.env`
(`set -a; source .env.external-db; set +a`). En ese caso Flyway queda deshabilitado.

### Con IntelliJ IDEA

1. Abrir el proyecto como proyecto Maven y configurar el SDK en Java 21.
2. Tener una base PostgreSQL disponible (por ejemplo `docker compose up -d --wait postgres`).
3. Crear una configuración de ejecución de Spring Boot sobre `ChallengeApplication`.
4. Cargar las variables de `.env` en **Environment variables** (IntelliJ no lee el archivo
   automáticamente) y verificar que `SPRING_FLYWAY_ENABLED=true` si la base está vacía.

## Configuración

La conexión a la base de datos se configura con variables de entorno, definidas en el archivo `.env` de la
raíz del proyecto. Este archivo está versionado para poder compartirlo y contiene credenciales solo para desarrollo local.

| Variable | Valor local | Descripción |
|----------|-------------|-------------|
| `DB_HOST` | `localhost` | Host de PostgreSQL. |
| `DB_PORT` | `5432` | Puerto de PostgreSQL. |
| `DB_NAME` | `challenge` | Nombre de la base. |
| `DB_USERNAME` | `challenge` | Usuario. |
| `DB_PASSWORD` | `challenge` | Contraseña. |
| `DB_PARAMS` | `?connectTimeout=5&socketTimeout=10` | Sufijo opcional de la URL JDBC (por ejemplo `?sslmode=require`). |
| `API_PORT` | `8080` | Puerto publicado de la API (solo Compose; dentro del contenedor escucha en `8080`). |
| `SPRING_FLYWAY_ENABLED` | `true` | Habilita las migraciones de Flyway al iniciar. |
| `TZ` | `UTC` | Zona horaria. |

### Base de datos local y base del challenge

| Archivo | Base de datos | Flyway |
|---------|---------------|--------|
| `.env` | Base **local** (PostgreSQL en Docker). | Habilitado: crea el esquema y carga los datos. |
| `.env.external-db` | Base **externa propuesta en el challenge**. | **Deshabilitado**, para no modificar una base que puede estar compartida. |

> **Importante:** los valores sensibles de `.env.external-db` (host, base, usuario y contraseña) son
> placeholders. Los datos reales de la base del challenge se compartieron en el mail de contacto; hay que
> reemplazarlos antes de usar ese archivo.

`.env.external-db` no lo usan los scripts ni Docker Compose (que solo leen `.env`). Para ejecutar la API
contra la base externa, ver [Con Maven](#con-maven) y cargar ese archivo en lugar de `.env`. Detalle en
[docs/notas-para-comunicar.md](docs/notas-para-comunicar.md).

## Pruebas

```bash
mvn verify    # unitarias (*Test) e integración (*IT); genera el reporte de cobertura de JaCoCo
```

- Usar **Java 21**: con JDK más nuevos (por ejemplo 27) Mockito falla al crear los mocks.
- Las pruebas unitarias no necesitan infraestructura. `OrderApiIT` (test funcional de punta a punta) usa
  PostgreSQL real mediante Testcontainers y requiere Docker; no se sustituye por H2.
- El reporte de cobertura queda en `target/site/jacoco/index.html`.

## Colección de Postman

Colección con todos los endpoints de la API: [cocos-challenge-local.postman_collection.json](postman/cocos-challenge-local.postman_collection.json).

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [link](https://github.com/cocoscap/cocos-challenge/blob/main/backend-challenge.md) | Enunciado del challenge. |
| [docs/fundamentos.md](docs/fundamentos.md) | Suposiciones y decisiones de diseño (arquitectura, Strategy, concurrencia, migraciones, testing). |
| [docs/notas-para-comunicar.md](docs/notas-para-comunicar.md) | Observaciones y casos límite a comunicar. |
