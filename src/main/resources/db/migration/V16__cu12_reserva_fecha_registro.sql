-- CU12 Solicitar Cita: una cita web sin pagar no puede ocupar el horario del barbero para siempre.
-- Se guarda cuándo se registró la cita; pasado el plazo para pagar el anticipo (CU14), un proceso
-- automático la cancela (VencimientoSolicitudesService). Las citas existentes toman la hora actual.
ALTER TABLE reserva
    ADD COLUMN fecha_registro TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'America/La_Paz');

-- Solo las citas web pendientes son candidatas a vencer: índice parcial para que el proceso sea liviano.
CREATE INDEX idx_reserva_web_pendiente ON reserva (fecha_registro)
    WHERE canal = 'WEB' AND estado = 'PENDIENTE';
