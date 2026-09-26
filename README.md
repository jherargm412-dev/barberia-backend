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
   git clone <URL-del-repo>
   cd <carpeta-del-repo>
   ```

2. Crear la base de datos vacía en PostgreSQL (las tablas las crea Flyway solo):

   ```sql
   CREATE DATABASE barber;
   ```

3. Copiar el archivo de ejemplo de variables y poner **tus** valores:

   ```bash
   cp .env.example .env
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
./gradlew test
```

Las pruebas usan la misma base `barber` pero en un esquema aislado `cu_test`
(nunca tocan los datos de `public`).

## Base de datos (Flyway)

Los cambios de esquema van **solo** como migraciones nuevas en
`src/main/resources/db/migration`, con el siguiente número libre:

```
V3__descripcion_corta.sql
```

- **Nunca** edites una migración que ya está en `main`: crea una nueva.
- Antes de crear una migración, haz `git pull` para no repetir el número de versión
  de un compañero.

## Estructura

Cada caso de uso / módulo va en su paquete dentro de `com.example.backend`
(por ejemplo `modulo_seguridad_usuarios`), con sus subcarpetas
`controller`, `service`, `repository`, etc.
La documentación de cada caso de uso está en `funcionalidades/`.

## Flujo de trabajo con Git

1. Actualiza `main`: `git checkout main && git pull`
2. Crea una rama para tu caso de uso: `git checkout -b feature/CU05-reservas`
3. Haz commits pequeños y sube tu rama: `git push -u origin feature/CU05-reservas`
4. Abre un **Pull Request** hacia `main` en GitHub para que otro lo revise.

No hagas push directo a `main`.
