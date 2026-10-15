-- CU12 Solicitar Cita (Cliente, canal web).
-- No cambia tablas: el cliente crea su cita en `reserva` (V11). Aquí solo se dan los permisos.
-- Los permisos se recalculan desde la BD en cada petición (PermisosService), así que
-- aplican de inmediato sin que el cliente tenga que volver a iniciar sesión.
 
-- 1) Permiso nuevo: agendar la propia cita en línea.
--    ON CONFLICT: si otro caso de uso (p. ej. CU13) ya lo sembró, no falla ni se duplica.
INSERT INTO permiso (accion, descripcion) VALUES
('RESERVA_SOLICITAR', 'Solicitar una cita propia desde la web (cliente)')
ON CONFLICT (accion) DO NOTHING;
 
-- 2) El rol Cliente podía solo PERFIL_EDITAR. Ahora también solicita citas y ve el catálogo
--    de servicios (SERVICIO_CONSULTAR ya existe desde V2) para elegir qué reservar.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre = 'Cliente'
  AND p.accion IN ('RESERVA_SOLICITAR', 'SERVICIO_CONSULTAR')
ON CONFLICT (rol_id, permiso_id) DO NOTHING;