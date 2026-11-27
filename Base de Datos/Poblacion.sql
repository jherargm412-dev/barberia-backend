-- =============================================================================
-- POBLACIÓN DE DATOS - HOUSE OF CUT (barbería)
-- Archivo: Base de Datos/Poblacion.sql
--
-- Ejecutar (desde la carpeta barberia-backend):
--   $env:PGPASSWORD='<tu contraseña>'; $env:PGCLIENTENCODING='UTF8'
--   psql -h localhost -U postgres -d barber -v ON_ERROR_STOP=1 -f "Base de Datos\Poblacion.sql"
--
-- CREDENCIALES (BCrypt fuerza 12, mismo encoder del backend)
--
-- EMPLEADOS
-- Nombre: Ronald Manuel Vargas Quispe
-- Correo: ronald@gmail.com
-- Contra: Ronald123!
--
-- Nombre: Camila Andrea Rojas Mendoza
-- Correo: camila@gmail.com
-- Contra: Camila123!
--
-- Nombre: Dayana Nicole Flores Pérez
-- Correo: dayana@gmail.com
-- Contra: Dayana123!
--
-- Nombre: Jorge Luis Mamani Condori
-- Correo: jorge@gmail.com
-- Contra: Jorge123!
--
-- Nombre: Andrés Felipe Cruz Salazar
-- Correo: andres@gmail.com
-- Contra: Andres123!
--
-- Nombre: Mauricio Daniel Añez Vargas
-- Correo: mauricio@gmail.com
-- Contra: Mauricio123!
--
-- CLIENTES
-- Nombre: Nicolas Junior Verduguez Teran
-- Correo: nicolas@gmail.com
-- Contra: Nicolas123!
--
-- Nombre: Carlos Miguel Terrazas Ruiz
-- Correo: carlos.ruiz@gmail.com
-- Contra: Carlos123!
-- (carlos@gmail.com ya pertenece al barbero original, por eso se usa este)
--
-- Nombre: Ana María Pérez Gutiérrez
-- Correo: ana@gmail.com
-- Contra: Ana123!
--
-- Nombre: Diego Fernando Salazar Castro
-- Correo: diego@gmail.com
-- Contra: Diego123!
--
-- Nombre: María Elena Rojas Silva
-- Correo: maria@gmail.com
-- Contra: Maria123!
--
-- Nombre: Paola Patricia Mendoza Vega
-- Correo: paola@gmail.com
-- Contra: Paola123!
--
-- Nombre: Fernando Agustín Navarro Arce
-- Correo: fernando@gmail.com
-- Contra: Fernando123!
--
-- Nombre: Luisa Fernanda Castro Iriarte
-- Correo: luisa@gmail.com
-- Contra: Luisa123!
--
-- REGLAS RESPETADAS:
--   * NO se inserta en rol, permiso, rol_permiso ni bitacora.
--     Solo se asignan roles YA EXISTENTES mediante rol_usuario.
--   * El usuario con id 1010 se reutiliza (se renombra) porque la bitácora,
--     escrita por el backend, ya lo referencia y no se puede borrar.
--   * Idempotente: se puede ejecutar varias veces sin duplicar registros.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. USUARIOS (14: 6 empleados + 8 clientes)
--    ON CONFLICT DO UPDATE garantiza que siempre queden los nombres reales.
-- -----------------------------------------------------------------------------
INSERT INTO usuario (id_usuario, nombre, telefono, fecha_nacimiento, correo, contrasena, estado, activo)
VALUES
 (1001, 'Ronald Manuel Vargas Quispe',    '77700001', DATE '1990-01-15', 'ronald@gmail.com',    '$2a$12$Mrsh0M2CZqIXYSH9xVP8yucTfjJu4wYYL0y689c.yMsSuICcmEinO', 'ACTIVO', TRUE),
 (1002, 'Camila Andrea Rojas Mendoza',    '77700002', DATE '1995-03-22', 'camila@gmail.com',    '$2a$12$cDNJd4iN7BC6kzURsLlDZe3nkKl63tJdJd9beH6CtKQ3qHu42x7Ym', 'ACTIVO', TRUE),
 (1003, 'Dayana Nicole Flores Pérez',     '77700003', DATE '1997-07-11', 'dayana@gmail.com',    '$2a$12$WTybtQV2/gSgHHt89ugdhON0OrXhsgAVe6iJDTMgC/LK6rdg47gha', 'ACTIVO', TRUE),
 (1004, 'Jorge Luis Mamani Condori',      '77700004', DATE '1992-11-05', 'jorge@gmail.com',     '$2a$12$g53kE6xDtUjHnZm4VDhYDurVG3wFns2/zOrt2rWPC8OP/1Xt9R7k6', 'ACTIVO', TRUE),
 (1005, 'Andrés Felipe Cruz Salazar',     '77700005', DATE '1996-02-18', 'andres@gmail.com',    '$2a$12$g2pOXMqjf3Gq5LnE.I40VOmMLJl.bi5doCw5oPVl1gEvvLyZIEO9S', 'ACTIVO', TRUE),
 (1006, 'Mauricio Daniel Añez Vargas',    '77700006', DATE '1999-09-30', 'mauricio@gmail.com',  '$2a$12$oCuAvvZErWh8SfDlXlpHiu7GfD2uWIOCakHCX7ZcdVaT9z7ZK9qPe', 'ACTIVO', TRUE),
 (1007, 'Nicolas Junior Verduguez Teran', '77710001', DATE '1988-06-14', 'nicolas@gmail.com',   '$2a$12$4OVwNfArWa7fJGuUZOjtE.aiBYQg6Pa2UyKR3x8Rp0HHB9NqE7iK6', 'ACTIVO', TRUE),
 (1008, 'Carlos Miguel Terrazas Ruiz',    '77710002', DATE '1993-12-01', 'carlos.ruiz@gmail.com',    '$2a$12$LF1yNEy40/e/YUt3ty8j3Oo9szdeHIFhlUUxR/TfGmB/NsbaJMMKq', 'ACTIVO', TRUE),
 (1009, 'Ana María Pérez Gutiérrez',      '77710003', DATE '2000-04-27', 'ana@gmail.com',       '$2a$12$xwhz1ZzZDsUmfMXJs1UUjOjQin1CY353NmckWgU.ayZZBGOYWMrOG', 'ACTIVO', TRUE),
 (1010, 'Diego Fernando Salazar Castro',  '77710004', DATE '1991-05-19', 'diego@gmail.com',     '$2a$12$YGLFdfx10TXchyD0.H1OYeSi4UZspuYIgAzBS3dWTm5FZgx.T4be2', 'ACTIVO', TRUE),
 (1011, 'María Elena Rojas Silva',        '77710005', DATE '1987-10-08', 'maria@gmail.com',     '$2a$12$390aPPYtsA41DsRhgHR0aeL9LpZO.x9KFuXH5Ne.bS/8sSAKOPyg6', 'ACTIVO', TRUE),
 (1012, 'Paola Patricia Mendoza Vega',    '77710006', DATE '1994-01-25', 'paola@gmail.com',     '$2a$12$2YWxjxueO6JlAkGnJzbMdeS0eYjsgGJVepI960tK7TaYrd3yKNeGi', 'ACTIVO', TRUE),
 (1013, 'Fernando Agustín Navarro Arce',  '77710007', DATE '1983-08-12', 'fernando@gmail.com',  '$2a$12$6lLexoqxZdSV8iQL7faHq.0SV2275H.4IWTF6Tl4uVznRHJIDtWLO', 'ACTIVO', TRUE),
 (1014, 'Luisa Fernanda Castro Iriarte',  '77710008', DATE '1998-03-30', 'luisa@gmail.com',     '$2a$12$SOBUeYBF5svT3ioMzGTutuHrUVr0jGYcqwdXZFInWxKUjPS2Ejz6q', 'ACTIVO', TRUE)
ON CONFLICT (id_usuario) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    telefono = EXCLUDED.telefono,
    fecha_nacimiento = EXCLUDED.fecha_nacimiento,
    correo = EXCLUDED.correo,
    contrasena = EXCLUDED.contrasena,
    estado = EXCLUDED.estado,
    activo = EXCLUDED.activo;

-- 1b. Asignación de roles existentes (NO se crean roles nuevos)
INSERT INTO rol_usuario (usuario_id, rol_id, fecha_asignacion)
SELECT u.id_usuario, r.id_rol, CURRENT_DATE
FROM (VALUES
 ('ronald@gmail.com',    'Administrador'),
 ('camila@gmail.com',    'Recepcionista'),
 ('dayana@gmail.com',    'Recepcionista'),
 ('jorge@gmail.com',     'Barbero'),
 ('andres@gmail.com',    'Barbero'),
 ('mauricio@gmail.com',  'Barbero'),
 ('nicolas@gmail.com',   'Cliente'),
 ('carlos.ruiz@gmail.com',    'Cliente'),
 ('ana@gmail.com',       'Cliente'),
 ('diego@gmail.com',     'Cliente'),
 ('maria@gmail.com',     'Cliente'),
 ('paola@gmail.com',     'Cliente'),
 ('fernando@gmail.com',  'Cliente'),
 ('luisa@gmail.com',     'Cliente')
) AS v(correo, rol)
JOIN usuario u ON u.correo = v.correo
JOIN rol r ON r.nombre = v.rol
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. EMPLEADOS (6: los usuarios con rol de empleado)
-- -----------------------------------------------------------------------------
INSERT INTO empleado (id_empleado, usuario_id, turno_id, especialidad, tipo_contrato)
SELECT v.id, u.id_usuario, t.id_turno, v.especialidad, v.tipo_contrato
FROM (VALUES
 (1201, 'ronald@gmail.com',   'Completo', 'Gestión y administración general', 'ASALARIADO'),
 (1202, 'camila@gmail.com',   'Mañana',   'Recepción y atención al cliente',  'ASALARIADO'),
 (1203, 'dayana@gmail.com',   'Tarde',    'Recepción y caja',                 'ASALARIADO'),
 (1204, 'jorge@gmail.com',    'Mañana',   'Corte clásico y degradados',       'COMISIONISTA'),
 (1205, 'andres@gmail.com',   'Tarde',    'Barba y afeitado',                 'COMISIONISTA'),
 (1206, 'mauricio@gmail.com', 'Completo', 'Corte infantil y coloración',      'COMISIONISTA')
) AS v(id, correo, turno, especialidad, tipo_contrato)
JOIN usuario u ON u.correo = v.correo
JOIN turno t ON t.nombre = v.turno
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. CLIENTES (8, todos con usuario y credenciales reales)
-- -----------------------------------------------------------------------------
INSERT INTO cliente (id_cliente, usuario_id, nombre, telefono, fecha_registro, activo)
VALUES
 (1101, 1007, 'Nicolas Junior Verduguez Teran', '77710001', DATE '2026-10-02', TRUE),
 (1102, 1008, 'Carlos Miguel Terrazas Ruiz',    '77710002', DATE '2026-10-15', TRUE),
 (1103, 1009, 'Ana María Pérez Gutiérrez',      '77710003', DATE '2026-11-01', TRUE),
 (1104, 1010, 'Diego Fernando Salazar Castro',  '77710004', DATE '2026-11-10', TRUE),
 (1105, 1011, 'María Elena Rojas Silva',        '77710005', DATE '2026-11-12', TRUE),
 (1106, 1012, 'Paola Patricia Mendoza Vega',    '77710006', DATE '2026-11-15', TRUE),
 (1107, 1013, 'Fernando Agustín Navarro Arce',  '77710007', DATE '2026-11-18', TRUE),
 (1108, 1014, 'Luisa Fernanda Castro Iriarte',  '77710008', DATE '2026-11-20', TRUE)
ON CONFLICT (id_cliente) DO UPDATE SET
    usuario_id = EXCLUDED.usuario_id,
    nombre = EXCLUDED.nombre,
    telefono = EXCLUDED.telefono,
    fecha_registro = EXCLUDED.fecha_registro,
    activo = EXCLUDED.activo;

-- -----------------------------------------------------------------------------
-- 4. PROVEEDORES (el NIT solo acepta dígitos, según la constraint de la BD)
-- -----------------------------------------------------------------------------
INSERT INTO proveedor (id_proveedor, empresa, nit, nombre, telefono, correo, activo)
VALUES
 (1301, 'Distribuidora Barber Pro S.R.L.',  '1023456789', 'Roberto Mendoza',   '77730001', 'ventas@barberpro.bo',  TRUE),
 (1302, 'Insumos Capilares Bolivia Ltda.',  '2098765432', 'María Rojas',       '77730002', 'contacto@icb.bo',      TRUE),
 (1303, 'Casa del Peluquero',               '3045678912', 'Luis Fernández',    '77730003', 'pedidos@casapel.bo',   TRUE),
 (1304, 'Productos Grooming S.R.L.',        '4056789123', 'Ana Villarroel',    '77730004', 'hola@grooming.bo',     TRUE),
 (1305, 'Importadora Estilo Bolivia',       '5067891234', 'Carlos Vargas',     '77730005', 'importadora@estilo.bo',TRUE),
 (1306, 'Proveedora La Elegancia',          '6078912345', 'Patricia Suárez',   '77730006', 'ventas@elegancia.bo',  TRUE)
ON CONFLICT (id_proveedor) DO UPDATE SET
    empresa = EXCLUDED.empresa, nit = EXCLUDED.nit, nombre = EXCLUDED.nombre,
    telefono = EXCLUDED.telefono, correo = EXCLUDED.correo, activo = EXCLUDED.activo;

-- -----------------------------------------------------------------------------
-- 5. SERVICIOS Y PRODUCTOS
-- -----------------------------------------------------------------------------
INSERT INTO servicio (id_servicio, nombre, descripcion, precio, activo, porcentaje_comision)
VALUES
 (1401, 'Lavado y secado',         'Lavado con champú y secado con secador',        25.00, TRUE, 40.00),
 (1402, 'Corte + cejas',           'Corte clásico más perfilado de cejas',          45.00, TRUE, 42.00),
 (1403, 'Ritual de barba premium', 'Barba con aceite, toalla caliente y navaja',    70.00, TRUE, 50.00)
ON CONFLICT (id_servicio) DO UPDATE SET
    nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio, activo = EXCLUDED.activo,
    porcentaje_comision = EXCLUDED.porcentaje_comision;

INSERT INTO producto (id_producto, nombre, descripcion, tipo, precio_venta, costo, comision_unitaria, stock_actual, stock_minimo, activo, costo_promedio)
VALUES
 (1501, 'Cera depilatoria en rollo',        'Cera en rollo para depilación facial',      'INSUMO', 55.00, 38.00, 0.00, 25, 10, TRUE, 38.00),
 (1502, 'Gel fijador extra fuerte 250 ml',  'Gel fijador de cabello 250 ml',             'VENTA',  35.00, 18.00, 1.00, 40, 12, TRUE, 18.00),
 (1503, 'Champú hidratante 400 ml',         'Champú hidratante para cabello seco',       'VENTA',  40.00, 22.00, 2.00, 30,  8, TRUE, 22.00),
 (1504, 'Aceite para barba 30 ml',          'Aceite nutritivo para barba 30 ml',         'VENTA',  38.00, 20.00, 3.00, 20,  6, TRUE, 20.00),
 (1505, 'Toallas de microfibra',            'Toallas para servicio de barba',           'INSUMO', 25.00, 15.00, 0.00, 50, 20, TRUE, 15.00),
 (1506, 'Desinfectante de herramientas',    'Desinfectante para tijeras y navajas',      'INSUMO', 45.00, 30.00, 0.00, 18, 10, TRUE, 30.00),
 (1507, 'Espuma de afeitar 200 ml',         'Espuma de afeitar para uso profesional',    'VENTA',  28.00, 14.00, 1.00, 24,  8, TRUE, 14.00),
 (1508, 'Tijeras profesionales 6.5 pulg.',  'Tijeras de corte profesional 6.5 pulgadas', 'INSUMO', 180.00,120.00, 0.00, 12,  4, TRUE,120.00)
ON CONFLICT (id_producto) DO UPDATE SET
    nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion,
    tipo = EXCLUDED.tipo, precio_venta = EXCLUDED.precio_venta,
    costo = EXCLUDED.costo, comision_unitaria = EXCLUDED.comision_unitaria,
    activo = EXCLUDED.activo, costo_promedio = EXCLUDED.costo_promedio;

-- -----------------------------------------------------------------------------
-- 6. MOVIMIENTOS DE INVENTARIO Y AJUSTES DE STOCK (uno por producto)
-- -----------------------------------------------------------------------------
INSERT INTO movimiento_inventario (id_movimiento_inventario, producto_id, tipo, cantidad, stock_anterior, stock_nuevo, costo_unitario, costo_total, motivo, usuario_id, fecha)
SELECT (2300 + ROW_NUMBER() OVER (ORDER BY p.id_producto))::INTEGER,
       p.id_producto, 'ENTRADA_MANUAL', p.stock_actual, 0, p.stock_actual,
       p.costo, (p.stock_actual * p.costo)::NUMERIC(16,2),
       'Stock inicial de inventario', 1001, TIMESTAMP '2026-11-21 10:00:00'
FROM producto p
WHERE p.id_producto BETWEEN 1501 AND 1508
ON CONFLICT DO NOTHING;

INSERT INTO ajuste_stock (id_ajuste_stock, producto_id, tipo, cantidad, stock_anterior, stock_nuevo, observacion, usuario_id, fecha)
SELECT (2400 + ROW_NUMBER() OVER (ORDER BY p.id_producto))::INTEGER,
       p.id_producto, 'INVENTARIO_INICIAL', p.stock_actual, 0, p.stock_actual,
       'Inventario inicial registrado por administración', 1001, TIMESTAMP '2026-11-21 10:05:00'
FROM producto p
WHERE p.id_producto BETWEEN 1501 AND 1508
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. HABILIDADES Y HORARIOS DE LOS BARBEROS
-- -----------------------------------------------------------------------------
INSERT INTO empleado_servicio (empleado_id, servicio_id, estado)
SELECT e.id_empleado, s.id_servicio, 'HABILITADO'
FROM empleado e
JOIN servicio s ON s.activo
WHERE e.id_empleado IN (1204, 1205, 1206)
ON CONFLICT DO NOTHING;

INSERT INTO horario_barbero (id_horario_barbero, empleado_id, dia_semana, hora_entrada, hora_salida)
SELECT (3000 + ROW_NUMBER() OVER (ORDER BY b.id, d.orden))::INTEGER,
       e.id_empleado, d.dia, t.hora_entrada, t.hora_salida
FROM (VALUES (1204, 'Mañana'), (1205, 'Tarde'), (1206, 'Completo')) AS b(id, turno)
JOIN empleado e ON e.id_empleado = b.id
JOIN turno t ON t.nombre = b.turno
CROSS JOIN (VALUES (1,'LUNES'), (2,'MARTES'), (3,'MIERCOLES'), (4,'JUEVES'), (5,'VIERNES'), (6,'SABADO')) AS d(orden, dia)
ON CONFLICT DO NOTHING;

INSERT INTO excepcion_horario (id_excepcion, empleado_id, fecha_inicio, fecha_fin, tipo, motivo, hora_entrada, hora_salida, fecha_registro)
VALUES
 (2901, 1204, DATE '2026-12-24', DATE '2026-12-31', 'VACACIONES',       'Vacaciones de fin de año', NULL,     NULL,     TIMESTAMP '2026-11-21 12:00:00'),
 (2902, 1205, DATE '2026-11-28', DATE '2026-11-28', 'HORARIO_ESPECIAL', 'Horario especial por evento', TIME '10:00', TIME '16:00', TIMESTAMP '2026-11-21 12:10:00')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 8. CAJA Y MOVIMIENTOS
-- -----------------------------------------------------------------------------
INSERT INTO caja (id_caja, fecha_apertura, fecha_cierre, monto_inicial, saldo_esperado, efectivo_contado, diferencia, observacion_cierre, estado, usuario_apertura_id, usuario_cierre_id)
VALUES
 (1601, TIMESTAMP '2026-11-22 09:00:00', TIMESTAMP '2026-11-22 21:00:00', 200.00, 1000.00, 1000.00, 0.00, 'Cierre del día', 'CERRADA', 1003, 1003),
 (1602, TIMESTAMP '2026-11-23 09:00:00', NULL,                            250.00, NULL,     NULL,     NULL,  NULL,             'ABIERTA', 1002, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO movimiento_caja (id_movimiento_caja, caja_id, tipo, concepto, monto, medio_pago, fecha, observaciones, usuario_id)
VALUES
 (2501, 1601, 'INGRESO', 'Venta de servicios',        300.00, 'EFECTIVO', TIMESTAMP '2026-11-22 10:30:00', 'Venta de servicios de la mañana', 1003),
 (2502, 1601, 'INGRESO', 'Anticipo de reserva',       150.00, 'QR',       TIMESTAMP '2026-11-22 11:15:00', 'Anticipo cancelado con QR',       1003),
 (2503, 1601, 'EGRESO',  'Compra de insumos',          50.00, 'EFECTIVO', TIMESTAMP '2026-11-22 14:00:00', 'Pago a proveedor',                1003),
 (2504, 1601, 'INGRESO', 'Venta de productos',        600.00, 'EFECTIVO', TIMESTAMP '2026-11-22 18:40:00', 'Venta de vitrina',                1003),
 (2505, 1602, 'INGRESO', 'Venta de servicios',        250.00, 'EFECTIVO', TIMESTAMP '2026-11-23 10:20:00', 'Venta de servicios de la mañana', 1002),
 (2506, 1602, 'INGRESO', 'Anticipo de reserva web',   100.00, 'QR',       TIMESTAMP '2026-11-23 10:45:00', 'Anticipo de cita web',            1002),
 (2507, 1602, 'EGRESO',  'Pago de servicios básicos',  30.00, 'EFECTIVO', TIMESTAMP '2026-11-23 11:30:00', 'Luz y agua',                      1002),
 (2508, 1602, 'INGRESO', 'Venta de vitrina',           80.00, 'TARJETA',  TIMESTAMP '2026-11-23 12:10:00', 'Pago con tarjeta',                1002)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 9. RESERVAS Y ANTICIPOS
-- -----------------------------------------------------------------------------
INSERT INTO reserva (id_reserva, cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado, codigo_acceso)
VALUES
 (1701, 1101, 1204, 1401, DATE '2026-11-24', TIME '09:00', TIME '09:30', 'PRESENCIAL',  0.00, 'CONFIRMADA', 'HOC-RES-1701'),
 (1702, 1102, 1205,    5, DATE '2026-11-24', TIME '15:00', TIME '16:00', 'WHATSAPP',   12.00, 'CONFIRMADA', 'HOC-RES-1702'),
 (1703, 1105, 1206, 1402, DATE '2026-11-25', TIME '10:00', TIME '10:45', 'TELEFONO',    0.00, 'PENDIENTE',  'HOC-RES-1703'),
 (1704, 1103, 1204,   10, DATE '2026-11-26', TIME '11:00', TIME '12:30', 'WEB',        22.00, 'CONFIRMADA', 'HOC-RES-1704'),
 (1705, 1106, 1205,    3, DATE '2026-11-27', TIME '16:00', TIME '16:30', 'PRESENCIAL',  0.00, 'CANCELADA',  'HOC-RES-1705'),
 (1706, 1104, 1206,    6, DATE '2026-11-28', TIME '09:30', TIME '10:00', 'WEB',         5.00, 'PENDIENTE',  'HOC-RES-1706'),
 (1707, 1107, 1204,    5, DATE '2026-11-20', TIME '15:00', TIME '16:00', 'PRESENCIAL',  0.00, 'ATENDIDA',   'HOC-RES-1707'),
 (1708, 1108, 1205,    9, DATE '2026-11-29', TIME '11:00', TIME '11:45', 'WHATSAPP',    9.00, 'CONFIRMADA', 'HOC-RES-1708')
ON CONFLICT DO NOTHING;

INSERT INTO pago_anticipo (id_pago_anticipo, reserva_id, caja_id, metodo, monto_efectivo, monto_qr, monto_total, referencia_qr, fecha_pago, usuario_id, monto_tarjeta)
VALUES
 (2601, 1702, 1601, 'QR',       0.00, 12.00, 12.00, 'QR-HOC-001', TIMESTAMP '2026-11-23 09:30:00', 1003, 0.00),
 (2602, 1704, 1602, 'EFECTIVO',22.00,  0.00, 22.00, NULL,         TIMESTAMP '2026-11-23 10:00:00', 1003, 0.00),
 (2603, 1708, 1602, 'MIXTO',    4.00,  5.00,  9.00, 'QR-HOC-003', TIMESTAMP '2026-11-23 10:15:00', 1002, 0.00)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 10. NOTAS DE VENTA Y DETALLES
-- -----------------------------------------------------------------------------
INSERT INTO nota_venta (id_nota_venta, cliente_id, caja_id, reserva_id, usuario_id, fecha, sub_total, descuento, impuesto, total, anticipo_aplicado, estado)
VALUES
 (1801, 1107, 1601, 1707, 1003, TIMESTAMP '2026-11-20 16:15:00',  60.00,  0.00, 0.00,  60.00, 0.00, 'PAGADA'),
 (1802, 1101, 1601, NULL,  1003, TIMESTAMP '2026-11-22 10:45:00', 100.00, 10.00, 0.00,  90.00, 0.00, 'PAGADA'),
 (1803, 1105, 1601, NULL,  1002, TIMESTAMP '2026-11-22 17:20:00',  45.00,  0.00, 0.00,  45.00, 0.00, 'PAGADA'),
 (1804, 1103, 1602, NULL,  1003, TIMESTAMP '2026-11-23 11:00:00',  38.00,  0.00, 0.00,  38.00, 0.00, 'EMITIDA')
ON CONFLICT DO NOTHING;

INSERT INTO detalle_servicio (id_detalle_servicio, nota_venta_id, servicio_id, empleado_id, precio_unitario, porcentaje_comision, estado)
VALUES
 (1901, 1801,   5, 1204, 60.00, 45.00, 'REALIZADO'),
 (1902, 1802,   1, 1204, 30.00, 40.00, 'REALIZADO'),
 (1903, 1802,   3, 1205, 30.00, 40.00, 'REALIZADO'),
 (1904, 1803,   9, 1205, 45.00, 45.00, 'REALIZADO')
ON CONFLICT DO NOTHING;

INSERT INTO detalle_nota_venta (id_detalle_nota_venta, nota_venta_id, producto_id, cantidad, precio_unitario, sub_total, descripcion, empleado_id, comision_unitaria)
VALUES
 (2001, 1802, 1503, 1, 40.00, 40.00, 'Champú hidratante de vitrina', 1204, 2.00),
 (2002, 1804, 1504, 1, 38.00, 38.00, 'Aceite para barba de vitrina', 1206, 3.00)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 11. COMPRAS A PROVEEDORES Y DETALLES
-- -----------------------------------------------------------------------------
INSERT INTO nota_compra (id_nota_compra, proveedor_id, metodo_pago_id, nro_fact, fecha, fecha_entrega_programada, fecha_entrega_real, sub_total, usuario_id, fecha_registro)
VALUES
 (2101, 1301, 1, 'F-001-2026', DATE '2026-11-21', DATE '2026-11-24', DATE '2026-11-23', 1440.00, 1001, TIMESTAMP '2026-11-21 15:00:00'),
 (2102, 1302, 1, 'F-002-2026', DATE '2026-11-22', DATE '2026-11-25', NULL,              1260.00, 1001, TIMESTAMP '2026-11-22 15:00:00')
ON CONFLICT DO NOTHING;

INSERT INTO detalle_nota_compra (id_detalle_nota_compra, nota_compra_id, producto_id, cantidad, precio_unitario, descuento, sub_total)
VALUES
 (2201, 2101, 1501, 30, 38.00, 0.00, 1140.00),
 (2202, 2101, 1505, 20, 15.00, 0.00,  300.00),
 (2203, 2102, 1502, 40, 18.00, 0.00,  720.00),
 (2204, 2102, 1506, 18, 30.00, 0.00,  540.00)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 12. LIQUIDACIÓN DE COMISIONES
--     45% de 60 = 27 + 40% de 30 = 12 + 2 (producto) = 41.00
-- -----------------------------------------------------------------------------
INSERT INTO pago_empleado (id_pago, empleado_id, fecha, monto_total)
VALUES (2701, 1204, TIMESTAMP '2026-11-23 18:00:00', 41.00)
ON CONFLICT DO NOTHING;

INSERT INTO detalle_pago_empleado (id_detalle_pago, pago_id, detalle_servicio_id, detalle_nota_venta_id, monto_comision)
VALUES
 (2801, 2701, 1901, NULL, 27.00),
 (2802, 2701, 1902, NULL, 12.00),
 (2803, 2701, NULL, 2001,  2.00)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 13. Secuencias apuntando al máximo id existente
-- -----------------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('usuario', 'id_usuario'),                         GREATEST((SELECT MAX(id_usuario) FROM usuario), 1));
SELECT setval(pg_get_serial_sequence('cliente', 'id_cliente'),                         GREATEST((SELECT MAX(id_cliente) FROM cliente), 1));
SELECT setval(pg_get_serial_sequence('empleado', 'id_empleado'),                       GREATEST((SELECT MAX(id_empleado) FROM empleado), 1));
SELECT setval(pg_get_serial_sequence('proveedor', 'id_proveedor'),                     GREATEST((SELECT MAX(id_proveedor) FROM proveedor), 1));
SELECT setval(pg_get_serial_sequence('servicio', 'id_servicio'),                       GREATEST((SELECT MAX(id_servicio) FROM servicio), 1));
SELECT setval(pg_get_serial_sequence('producto', 'id_producto'),                       GREATEST((SELECT MAX(id_producto) FROM producto), 1));
SELECT setval(pg_get_serial_sequence('movimiento_inventario', 'id_movimiento_inventario'), GREATEST((SELECT MAX(id_movimiento_inventario) FROM movimiento_inventario), 1));
SELECT setval(pg_get_serial_sequence('ajuste_stock', 'id_ajuste_stock'),               GREATEST((SELECT MAX(id_ajuste_stock) FROM ajuste_stock), 1));
SELECT setval(pg_get_serial_sequence('horario_barbero', 'id_horario_barbero'),         GREATEST((SELECT MAX(id_horario_barbero) FROM horario_barbero), 1));
SELECT setval(pg_get_serial_sequence('excepcion_horario', 'id_excepcion'),             GREATEST((SELECT MAX(id_excepcion) FROM excepcion_horario), 1));
SELECT setval(pg_get_serial_sequence('caja', 'id_caja'),                               GREATEST((SELECT MAX(id_caja) FROM caja), 1));
SELECT setval(pg_get_serial_sequence('movimiento_caja', 'id_movimiento_caja'),         GREATEST((SELECT MAX(id_movimiento_caja) FROM movimiento_caja), 1));
SELECT setval(pg_get_serial_sequence('reserva', 'id_reserva'),                         GREATEST((SELECT MAX(id_reserva) FROM reserva), 1));
SELECT setval(pg_get_serial_sequence('pago_anticipo', 'id_pago_anticipo'),             GREATEST((SELECT MAX(id_pago_anticipo) FROM pago_anticipo), 1));
SELECT setval(pg_get_serial_sequence('nota_venta', 'id_nota_venta'),                   GREATEST((SELECT MAX(id_nota_venta) FROM nota_venta), 1));
SELECT setval(pg_get_serial_sequence('detalle_servicio', 'id_detalle_servicio'),       GREATEST((SELECT MAX(id_detalle_servicio) FROM detalle_servicio), 1));
SELECT setval(pg_get_serial_sequence('detalle_nota_venta', 'id_detalle_nota_venta'),   GREATEST((SELECT MAX(id_detalle_nota_venta) FROM detalle_nota_venta), 1));
SELECT setval(pg_get_serial_sequence('nota_compra', 'id_nota_compra'),                 GREATEST((SELECT MAX(id_nota_compra) FROM nota_compra), 1));
SELECT setval(pg_get_serial_sequence('detalle_nota_compra', 'id_detalle_nota_compra'), GREATEST((SELECT MAX(id_detalle_nota_compra) FROM detalle_nota_compra), 1));
SELECT setval(pg_get_serial_sequence('pago_empleado', 'id_pago'),                      GREATEST((SELECT MAX(id_pago) FROM pago_empleado), 1));
SELECT setval(pg_get_serial_sequence('detalle_pago_empleado', 'id_detalle_pago'),      GREATEST((SELECT MAX(id_detalle_pago) FROM detalle_pago_empleado), 1));

COMMIT;

-- Fin de la población. No se tocaron rol, permiso, rol_permiso ni bitacora.
