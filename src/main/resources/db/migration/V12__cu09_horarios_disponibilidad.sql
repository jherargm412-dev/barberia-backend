-- CU09 Gestionar Horarios de Disponibilidad.
-- Fuente: funcionalidades/CU01-CU02/CU09/03-modelo-de-datos.md §3, §4 y §5.

-- 1) HORARIO y RESERVA, copiadas del script oficial (Base de Datos/Script.sql §6 y §22).

-- Horario de atención de la barbería: franjas por día de la semana (no se relaciona con empleado).
-- La crea este CU porque es el primero que la usa.
CREATE TABLE horario (
    id_horario          SERIAL          PRIMARY KEY,
    dia_semana          VARCHAR(10)     NOT NULL
                        CHECK (dia_semana IN ('LUNES','MARTES','MIERCOLES','JUEVES',
                                              'VIERNES','SABADO','DOMINGO')),
    hora_inicio         TIME            NOT NULL,
    hora_fin            TIME            NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO'
                        CHECK (estado IN ('ACTIVO','INACTIVO')),
    CONSTRAINT chk_horario_horas CHECK (hora_fin > hora_inicio)
);

-- Semilla [PROPUESTO]: lunes a sábado 09:00–21:00 y domingo 09:00–15:00 (paso 7 del CU), en franjas contiguas.
INSERT INTO horario (dia_semana, hora_inicio, hora_fin) VALUES
('LUNES',     '09:00', '15:00'), ('LUNES',     '15:00', '21:00'),
('MARTES',    '09:00', '15:00'), ('MARTES',    '15:00', '21:00'),
('MIERCOLES', '09:00', '15:00'), ('MIERCOLES', '15:00', '21:00'),
('JUEVES',    '09:00', '15:00'), ('JUEVES',    '15:00', '21:00'),
('VIERNES',   '09:00', '15:00'), ('VIERNES',   '15:00', '21:00'),
('SABADO',    '09:00', '15:00'), ('SABADO',    '15:00', '21:00'),
('DOMINGO',   '09:00', '15:00');

-- Reserva: la crea CU11 Gestionar Cita (V11__cu11_reserva.sql). Aquí solo "si no existe", con la
-- misma definición del script, para que esta rama funcione sola antes de juntarse con CU11.
-- El CU09 solo LEE reservas para detectar citas afectadas.
CREATE TABLE IF NOT EXISTS reserva (
    id_reserva      SERIAL          PRIMARY KEY,
    cliente_id      INTEGER         NOT NULL REFERENCES cliente(id_cliente),
    empleado_id     INTEGER         REFERENCES empleado(id_empleado),
    servicio_id     INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    fecha           DATE            NOT NULL,
    hora_inicio     TIME            NOT NULL,
    hora_fin        TIME            NOT NULL,
    canal           VARCHAR(20)     NOT NULL
                    CHECK (canal IN ('PRESENCIAL', 'WHATSAPP', 'TELEFONO', 'WEB')),
    monto_anticipo  NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (monto_anticipo >= 0),
    estado          VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE'
                    CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO')),
    CONSTRAINT chk_reserva_horas CHECK (hora_fin > hora_inicio)
);

-- 2) Horario semanal por barbero [PROPUESTO]. Un registro por día que trabaja; sin registro = no trabaja.
CREATE TABLE horario_barbero (
    id_horario_barbero  SERIAL       PRIMARY KEY,
    empleado_id         INTEGER      NOT NULL REFERENCES empleado(id_empleado) ON DELETE CASCADE,
    dia_semana          VARCHAR(10)  NOT NULL
                        CHECK (dia_semana IN ('LUNES','MARTES','MIERCOLES','JUEVES',
                                              'VIERNES','SABADO','DOMINGO')),
    hora_entrada        TIME         NOT NULL,
    hora_salida         TIME         NOT NULL,
    CONSTRAINT chk_horario_barbero_horas CHECK (hora_salida > hora_entrada),
    CONSTRAINT uq_horario_barbero_dia UNIQUE (empleado_id, dia_semana)
);

-- 3) Excepciones: vacaciones, permisos, días libres, horarios especiales [PROPUESTO].
--    El cruce entre excepciones del mismo barbero (4d) se valida en la aplicación.
CREATE TABLE excepcion_horario (
    id_excepcion        SERIAL       PRIMARY KEY,
    empleado_id         INTEGER      NOT NULL REFERENCES empleado(id_empleado) ON DELETE CASCADE,
    fecha_inicio        DATE         NOT NULL,
    fecha_fin           DATE         NOT NULL,
    tipo                VARCHAR(20)  NOT NULL
                        CHECK (tipo IN ('VACACIONES','PERMISO','DIA_LIBRE','HORARIO_ESPECIAL')),
    motivo              VARCHAR(200),
    hora_entrada        TIME,
    hora_salida         TIME,
    fecha_registro      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_excepcion_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT chk_excepcion_horas CHECK (
        (tipo = 'HORARIO_ESPECIAL' AND hora_entrada IS NOT NULL
                                   AND hora_salida  IS NOT NULL
                                   AND hora_salida > hora_entrada)
     OR (tipo <> 'HORARIO_ESPECIAL' AND hora_entrada IS NULL AND hora_salida IS NULL)
    )
);

CREATE INDEX idx_excepcion_empleado_fechas ON excepcion_horario (empleado_id, fecha_inicio, fecha_fin);
-- Mismo índice que crea CU11; "si no existe" por la misma razón que la tabla.
CREATE INDEX IF NOT EXISTS idx_reserva_empleado_fecha ON reserva (empleado_id, fecha);

-- 4) Semilla [PROPUESTO]: horario semanal de cada barbero desde su turno base, lunes a sábado;
--    domingo sin trabajar (Entrevista #1, pregunta 4.3). Sin excepciones.
INSERT INTO horario_barbero (empleado_id, dia_semana, hora_entrada, hora_salida)
SELECT e.id_empleado, d.dia, t.hora_entrada, t.hora_salida
FROM empleado e
JOIN turno t        ON t.id_turno = e.turno_id
JOIN usuario u      ON u.id_usuario = e.usuario_id
JOIN rol_usuario ru ON ru.usuario_id = u.id_usuario
JOIN rol r          ON r.id_rol = ru.rol_id AND r.nombre = 'Barbero'
CROSS JOIN (VALUES ('LUNES'),('MARTES'),('MIERCOLES'),('JUEVES'),('VIERNES'),('SABADO')) AS d(dia)
ON CONFLICT (empleado_id, dia_semana) DO NOTHING;
