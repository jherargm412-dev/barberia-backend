-- CU12 Solicitar Cita sin cuenta (invitado): quien no tiene cuenta reserva en línea con su nombre y teléfono.
-- El modelo ya lo admite (03-modelo-de-datos: cliente.usuario_id es opcional para clientes "online sin cuenta").

-- 1) Código secreto de la cita web de un invitado: es su enlace para ver la cita y pagar el anticipo (CU14)
--    sin iniciar sesión. Solo lo tienen las citas de invitados; UUID, imposible de adivinar.
ALTER TABLE reserva ADD COLUMN codigo_acceso VARCHAR(36) UNIQUE;

-- 2) Usuario técnico "Reservas en línea": la bitácora, el pago y el ingreso en caja exigen un usuario
--    responsable y el invitado no tiene. INACTIVO, sin roles y con una contraseña que no es un hash válido:
--    nadie puede iniciar sesión con él.
INSERT INTO usuario (nombre, correo, contrasena, estado)
VALUES ('Reservas en línea', 'reservas.web@sistema.houseofcut', '!sin-acceso', 'INACTIVO')
ON CONFLICT (correo) DO NOTHING;
