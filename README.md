# Barbería House of Cut — Backend

API REST en Spring Boot 4 + PostgreSQL + Flyway + JWT.

## Requisitos

- **Java 21** o superior
- **PostgreSQL** (local, puerto 5432)
- Git

No hace falta instalar Gradle: se usa el wrapper (`./gradlew`).

## Primera vez (configuración local)

1. Clonar el repositorio:

   ```bash
   git clone https://github.com/jherargm412-dev/barberia-backend.git
   cd barberia-backend
   ```

2. Crear la base de datos vacía en PostgreSQL (las tablas las crea Flyway solo):

   ```sql
   CREATE DATABASE barber;
   ```

3. Copiar el archivo de ejemplo de variables y poner **tus** valores:

   ```bash
   cp .env.example .env       # macOS / Linux / Git Bash / PowerShell
   copy .env.example .env     # Windows (CMD)
   ```

   Como mínimo cambia `DB_PASSWORD` (la contraseña de tu PostgreSQL) y `JWT_SECRET`
   (cualquier texto de 32 caracteres o más). El `.env` **nunca** se sube a Git.

4. Levantar el backend:

   ```bash
   ./gradlew bootRun        # macOS / Linux
   gradlew.bat bootRun      # Windows
   ```

   La API queda en `http://localhost:8080`.

## Pruebas

```bash
./gradlew test          # macOS / Linux
gradlew.bat test        # Windows
```

Las pruebas usan la misma base `barber` pero en un esquema aislado `cu_test`
(nunca tocan los datos de `public`).

## Base de datos (Flyway)

¿Qué es Flyway y por qué no usamos `ddl-auto=update`? Lee [docs/flyway.md](docs/flyway.md).

Los cambios de esquema van **solo** como migraciones nuevas en
`src/main/resources/db/migration`, con el siguiente número libre:

```
V3__descripcion_corta.sql
```

- **Nunca** edites una migración que ya está en `main`: crea una nueva.
- Antes de crear una migración, haz `git pull` para no repetir el número de versión
  de un compañero.

## Despliegue (Railway)

Railway construye la imagen con el `Dockerfile` cada vez que se hace merge a `main`.
**El `Dockerfile` no corre las pruebas**: corre `./gradlew test` antes de aprobar un Pull Request.

### Probar el despliegue en tu máquina (Docker Desktop)

```bash
docker compose up --build     # PostgreSQL + backend en perfil prod
docker compose down -v        # apagar y borrar la base de prueba
```

Usa `JWT_SECRET` y `APP_SEED_ADMIN_*` de tu `.env` y una base propia dentro de Docker
(no toca tu PostgreSQL local). Comprueba que `http://localhost:8080/actuator/health` responda `UP`.

### Variables del servicio en Railway

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_SECRET` | Uno nuevo, solo para producción (`openssl rand -base64 48`) |
| `APP_SEED_ADMIN_EMAIL` / `APP_SEED_ADMIN_PASSWORD` / `APP_SEED_ADMIN_NOMBRE` | El administrador real |
| `CORS_ORIGIN` | URL del frontend, sin `/` al final |
| `APP_FRONTEND_URL` | URL del frontend (enlaces de invitación) |

`PORT` lo pone Railway solo. En **Settings → Healthcheck Path** va `/actuator/health`.

**Correo:** Railway (planes Free y Hobby) bloquea SMTP, así que por ahora `MAIL_USERNAME` va
**vacía**: los correos se escriben en el log del servicio. Se reemplazará por una API de correo (Brevo).

## 📁 Estructura del Repositorio

```
barberia-backend/
│
├── src/main/java/.../backend/
│   ├── security/               # JWT, login y permisos
│   ├── exception/              # Errores y manejador global
│   ├── comun/                  # Clases compartidas (PaginaRespuesta)
│   └── modulos/
│       ├── seguridad_usuarios/ # CU01, CU02, CU05 (Usuarios, Login, Bitácora)
│       ├── gestion_clientes/   # CU06 (Clientes)
│       ├── gestion_empleados/  # Por implementar
│       ├── servicios_reservas/ # CU08 (Catálogo de servicios, Reservas)
│       ├── ventas_caja/        # Por implementar
│       ├── inventario_compras/ # Por implementar
│       └── reportes/           # Por implementar
│
├── src/main/resources/
│   ├── application.properties  # Configuración (lee el .env)
│   └── db/migration/           # Migraciones de BD con Flyway
│
├── src/test/                   # Pruebas
├── funcionalidades/            # Documentación de cada caso de uso
├── docs/                       # Guías (Flyway)
├── Dockerfile                  # Imagen para Railway
├── docker-compose.yml          # Prueba local del despliegue
├── .env.example                # Plantilla de variables de entorno
└── build.gradle                # Dependencias del proyecto
```

Lo **global** (fuera de los módulos) sirve a toda la app: cualquier módulo puede lanzar
`RecursoNoEncontradoException`, usar `UsuarioAutenticado` o devolver `PaginaRespuesta`.
Avisa al equipo antes de cambiar algo ahí.

Dentro de cada **módulo**, primero va la **capa** (`controller`, `service`, `dto`…) y dentro
de cada capa, **una carpeta por caso de uso**:

```
modulos/seguridad_usuarios/
  controller/
    iniciar_sesion/        AuthController                    (CU02)
    gestionar_usuarios/    UsuarioController, RolController  (CU01)
  service/
    iniciar_sesion/        AuthService
    gestionar_usuarios/    UsuarioService, RolService
  mapper/
    gestionar_usuarios/    UsuarioMapper   (convierte entidad → DTO)
  dto/
    iniciar_sesion/        LoginRequest, LoginResponse, UsuarioSesion
    gestionar_usuarios/    CrearUsuarioRequest, UsuarioDetalle…
  entity/                  compartidas por todos los casos de uso del módulo
  repository/              compartidos por todos los casos de uso del módulo
  seed/                    AdminSeeder (datos iniciales de este módulo)
  audit/                   BitacoraService (otros módulos pueden usarlo para registrar acciones)
  util/
```

- **`controller`, `service`, `mapper` y `dto`**: tu clase va en la subcarpeta de **tu caso de uso**
  (créala si no existe, ej. `service/configurar_perfil/`).
- **`entity` y `repository` no llevan subcarpetas**: una misma tabla (por ejemplo `usuario`)
  la usan varios casos de uso.
- Un caso de uso **no importa a otro caso de uso**. Si dos necesitan lo mismo, va en la raíz
  de la capa (sin subcarpeta) o en `util/`.

La documentación de cada caso de uso está en `funcionalidades/`.

## Flujo de trabajo con Git

1. Actualiza `main`: `git checkout main && git pull`
2. Crea una rama para tu caso de uso: `git checkout -b feature/CU05-reservas`
3. Haz commits pequeños y sube tu rama: `git push -u origin feature/CU05-reservas`
4. Abre un **Pull Request** hacia `main` en GitHub para que otro lo revise.

No hagas push directo a `main`.
