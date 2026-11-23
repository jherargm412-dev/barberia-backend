-- CU28 Comando de Voz: permiso para usar el micrófono de la barra superior.
-- Los comandos solo buscan y abren pantallas; cada pantalla sigue exigiendo su propio permiso.
-- Se asigna a los cuatro roles: cada uno solo puede abrir por voz las pantallas que ya puede abrir con el menú.
INSERT INTO permiso (accion, descripcion) VALUES
('VOZ_USAR', 'Usar comandos de voz para buscar y abrir pantallas')
ON CONFLICT (accion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre IN ('Administrador', 'Recepcionista', 'Barbero', 'Cliente')
  AND p.accion = 'VOZ_USAR'
ON CONFLICT (rol_id, permiso_id) DO NOTHING;
