-- CU Gestionar pasarela de pago (Ventas y caja).
-- PASARELA_PAGO_GESTIONAR: activar/desactivar pasarelas (Administrador).
-- PASARELA_PAGO_COBRAR: cobrar con tarjeta y ver los cobros (Administrador y Recepcionista).
INSERT INTO permiso (accion, descripcion) VALUES
('PASARELA_PAGO_GESTIONAR', 'Activar o desactivar pasarelas de pago'),
('PASARELA_PAGO_COBRAR',    'Cobrar con tarjeta mediante la pasarela de pago')
ON CONFLICT (accion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE (r.nombre = 'Administrador' AND p.accion IN ('PASARELA_PAGO_GESTIONAR', 'PASARELA_PAGO_COBRAR'))
   OR (r.nombre = 'Recepcionista' AND p.accion = 'PASARELA_PAGO_COBRAR')
ON CONFLICT (rol_id, permiso_id) DO NOTHING;
