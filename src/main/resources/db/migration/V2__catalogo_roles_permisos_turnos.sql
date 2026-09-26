-- Datos de catálogo [DEFINIDO]: 4 roles, 30 permisos, 41 filas rol_permiso, 3 turnos.
-- Fuente: funcionalidades/CU01-CU02/02-roles-y-permisos.md y 03-modelo-de-datos.md §8.
-- Las filas de rol_permiso se insertan por nombre de rol y código de permiso, no por id.

INSERT INTO rol (nombre, descripcion) VALUES
('Administrador', 'Control total del sistema: usuarios, roles, permisos, reportes y configuración general'),
('Recepcionista', 'Gestión de clientes, agenda de reservas, ventas y caja diaria'),
('Barbero',       'Ejecución de servicios, consulta de agenda propia y comisiones'),
('Cliente',       'Rol para clientes registrados que agendan citas desde el portal en línea');

INSERT INTO permiso (accion, descripcion) VALUES
('USUARIO_GESTIONAR',         'Crear, editar, desactivar usuarios'),
('ROL_ASIGNAR',               'Asignar roles y permisos'),
('PERFIL_EDITAR',             'Ver y editar el perfil propio, cambiar contraseña'),
('CLIENTE_CREAR',             'Registrar nuevos clientes'),
('CLIENTE_EDITAR',            'Modificar datos de clientes'),
('CLIENTE_CONSULTAR',         'Ver lista e historial de clientes'),
('RESERVA_CREAR',             'Registrar una cita (con anticipo 20%)'),
('RESERVA_EDITAR',            'Reprogramar o modificar citas'),
('RESERVA_CANCELAR',          'Cancelar cita / retener anticipo'),
('RESERVA_ATENDER',           'Marcar la cita como atendida'),
('AGENDA_CONSULTAR',          'Ver la agenda completa del día'),
('AGENDA_CONSULTAR_PROPIA',   'Ver solo la agenda propia'),
('SERVICIO_GESTIONAR',        'Crear/editar servicios, tarifas y combos'),
('SERVICIO_CONSULTAR',        'Ver catálogo de servicios y precios'),
('VENTA_REGISTRAR',           'Emitir notas de venta (servicios y productos)'),
('VENTA_ANULAR',              'Anular una nota de venta'),
('VENTA_CONSULTAR',           'Consultar ventas realizadas'),
('CAJA_ABRIR',                'Aperturar caja con monto inicial'),
('CAJA_CERRAR',               'Cierre diario de caja'),
('CAJA_MOVIMIENTO_REGISTRAR', 'Registrar ingresos/egresos'),
('CAJA_CONSULTAR',            'Ver estado y movimientos de caja'),
('PRODUCTO_GESTIONAR',        'Crear/editar productos, stock mínimo'),
('PRODUCTO_CONSULTAR',        'Consultar stock y precios de vitrina'),
('STOCK_AJUSTAR',             'Ajustes por merma o daño'),
('PROVEEDOR_GESTIONAR',       'Administrar proveedores'),
('COMPRA_REGISTRAR',          'Registrar notas de compra'),
('PAGO_EMPLEADO_GENERAR',     'Liquidar comisiones/sueldos'),
('COMISION_CONSULTAR_PROPIA', 'Ver sus propias comisiones acumuladas'),
('REPORTE_GENERAR',           'Reportes de ventas, inventario y comisiones'),
('BITACORA_CONSULTAR',        'Consultar el registro de auditoría');

-- Administrador: los 30 permisos
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p WHERE r.nombre = 'Administrador';

-- Recepcionista: 17 permisos
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'Recepcionista' AND p.accion IN (
    'PERFIL_EDITAR', 'CLIENTE_CREAR', 'CLIENTE_EDITAR', 'CLIENTE_CONSULTAR',
    'RESERVA_CREAR', 'RESERVA_EDITAR', 'RESERVA_CANCELAR', 'RESERVA_ATENDER',
    'AGENDA_CONSULTAR', 'SERVICIO_CONSULTAR', 'VENTA_REGISTRAR', 'VENTA_CONSULTAR',
    'CAJA_ABRIR', 'CAJA_CERRAR', 'CAJA_MOVIMIENTO_REGISTRAR', 'CAJA_CONSULTAR',
    'PRODUCTO_CONSULTAR'
);

-- Barbero: 4 permisos
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'Barbero' AND p.accion IN (
    'PERFIL_EDITAR', 'AGENDA_CONSULTAR_PROPIA', 'SERVICIO_CONSULTAR', 'COMISION_CONSULTAR_PROPIA'
);

-- Cliente: 1 permiso
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'Cliente' AND p.accion IN ('PERFIL_EDITAR');

INSERT INTO turno (nombre, hora_entrada, hora_salida) VALUES
('Mañana',   '09:00', '15:00'),
('Tarde',    '15:00', '21:00'),
('Completo', '09:00', '21:00');
