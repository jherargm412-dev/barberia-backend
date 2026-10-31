# Prueba de HOUSE of CUT en Render

`render.yaml` crea un backend Docker y una base PostgreSQL 17 independiente,
ambos en plan Free y en la misma región, más un sitio estático React gratuito.
No importa los datos de Railway.

## Crear la prueba

1. Publicar esta configuración en una rama propia del repositorio backend.
2. En Render, elegir New > Blueprint y seleccionar esa rama y `render.yaml`.
3. Ingresar `APP_SEED_ADMIN_EMAIL` y una contraseña de prueba propia en
   `APP_SEED_ADMIN_PASSWORD`. No guardar contraseñas en Git.
4. Comprobar que ambos recursos indiquen Free antes de desplegar.
5. Esperar el arranque: Flyway crea el esquema y el seeder crea el administrador.
6. Comprobar `<URL-del-backend>/actuator/health` y
   `<URL-del-backend>/api/v1/publico/servicios`.
7. Abrir el sitio estático `houseofcut-test-web`. Se compila desde la rama
   `codex/deploy-render-test` del repositorio frontend, con `VITE_API_URL`
   apuntando a la API Render. La regla `/*` a `/index.html` permite recargar
   rutas de React como `/agenda` o `/login`.
8. Verificar la URL asignada por Render. Si difiere de
   `https://houseofcut-test-web.onrender.com`, actualizar `CORS_ORIGIN` y
   `APP_FRONTEND_URL` en este Blueprint y sincronizarlo.
9. Probar login, usuarios/barberos, catálogo, horarios, reservas y caja.
   Una base nueva no contiene los clientes, servicios ni citas de Railway.

## Alcance de esta prueba

- El backend Free se suspende tras 15 minutos sin tráfico y puede tardar
  alrededor de un minuto en despertar.
- PostgreSQL Free vence a los 30 días. Exportar los datos antes de su vencimiento
  si se quieren conservar o pasar a un plan de pago.
- Sin configurar correo, los mensajes se escriben en los logs: no se envían.
  Render Free bloquea SMTP; para correo real configurar el proveedor Brevo por
  HTTPS con `APP_CORREO_PROVEEDOR=brevo`, `BREVO_API_KEY` y `MAIL_FROM`.
- Los pagos con tarjeta requieren claves Stripe de prueba, agregadas directamente
  como `STRIPE_SECRET_KEY` y `STRIPE_PUBLISHABLE_KEY` en Render.
- No definir `DB_URL` salvo que se quiera reemplazar la conexión derivada de
  `DB_HOST`, `DB_PORT` y `DB_NAME`. Las credenciales de base las genera Render.

Referencia: https://render.com/docs/blueprint-spec y https://render.com/docs/free.
