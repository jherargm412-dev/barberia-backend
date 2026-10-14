-- CU02 (05 §5.2): bloqueo temporal de la cuenta tras intentos fallidos de inicio de sesión.
--   intentos_fallidos: contraseñas incorrectas seguidas; vuelve a 0 con un login correcto o al bloquear.
--   bloqueado_hasta:   mientras sea futuro, el login se rechaza aunque la contraseña sea correcta.
--                      Al pasar la hora, la cuenta se desbloquea sola (no usa el estado SUSPENDIDO).
ALTER TABLE usuario
    ADD COLUMN intentos_fallidos INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta   TIMESTAMP NULL;
