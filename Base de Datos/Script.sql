-- ================================================================
-- 1. ROL
-- Catálogo de roles del sistema (Administrador, Recepcionista, etc.)
-- ================================================================
CREATE TABLE rol (
 id_rol SERIAL PRIMARY KEY,
 nombre VARCHAR(30) NOT NULL UNIQUE,
 descripcion VARCHAR(150),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
-- ==================================s==============================
-- 2. PERMISO
-- Catálogo de permisos granulares por módulo/acción
-- ================================================================
CREATE TABLE permiso (
 id_permiso SERIAL PRIMARY KEY,
 accion VARCHAR(50) NOT NULL UNIQUE,
 descripcion VARCHAR(150),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
-- ================================================================
-- 3. USUARIO
-- Cuentas de acceso al sistema (base de EMPLEADO y opcional de CLIENTE)
-- ================================================================
CREATE TABLE usuario (
 id_usuario SERIAL PRIMARY KEY,
 nombre VARCHAR(80) NOT NULL,
 telefono VARCHAR(15),
 fecha_nacimiento DATE,
 correo VARCHAR(100) UNIQUE,
 contrasena VARCHAR(255) NOT NULL, -- hash
(bcrypt/argon2)
 estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
 CHECK (estado IN ('ACTIVO', 'INACTIVO',
'SUSPENDIDO')),
 fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
-- ================================================================
-- 4. TURNO
-- Extraída en 3FN: turno -> hora_entrada, hora_salida
-- ================================================================
CREATE TABLE turno (
 id_turno SERIAL PRIMARY KEY,
 nombre VARCHAR(30) NOT NULL UNIQUE, -- Mañana, Tarde,
Completo
 hora_entrada TIME NOT NULL,
 hora_salida TIME NOT NULL,
 CONSTRAINT chk_turno_horas CHECK (hora_salida > hora_entrada)
);
-- ================================================================
-- 5. SERVICIO
-- Catálogo de servicios de la barbería (corte, barba, facial, etc.)
-- ================================================================
CREATE TABLE servicio (
 id_servicio SERIAL PRIMARY KEY,
 nombre VARCHAR(80) NOT NULL,
 descripcion VARCHAR(200),
 precio NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
-- ================================================================
-- 6. HORARIO
-- Franjas horarias de atención por día de la semana
-- ================================================================
CREATE TABLE horario (
 id_horario SERIAL PRIMARY KEY,
 dia_semana VARCHAR(10) NOT NULL
 CHECK (dia_semana IN ('LUNES', 'MARTES',
'MIERCOLES',
 'JUEVES', 'VIERNES', 'SABADO',
 'DOMINGO')),
 hora_inicio TIME NOT NULL,
 hora_fin TIME NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
 CHECK (estado IN ('ACTIVO', 'INACTIVO')),
 CONSTRAINT chk_horario_horas CHECK (hora_fin > hora_inicio)
);
-- ================================================================
-- 7. PASARELA_PAGO
-- Catálogo de métodos / pasarelas de pago (Efectivo, QR, Stripe...).
-- api_key guarda SOLO la clave pública (pk_...). La clave secreta
-- (sk_...) nunca va a la BD: vive en la variable STRIPE_SECRET_KEY.
-- El backend copia api_key desde STRIPE_PUBLISHABLE_KEY al iniciar.
-- ================================================================
CREATE TABLE pasarela_pago (
 id_metodo_pago SERIAL PRIMARY KEY,
 nombre VARCHAR(30) NOT NULL UNIQUE,
 descripcion VARCHAR(150),
 proveedor VARCHAR(20) NOT NULL DEFAULT 'NINGUNO'
 CHECK (proveedor IN ('NINGUNO', 'STRIPE')),
 api_key VARCHAR(255), -- clave pública (pk_...)
 modo VARCHAR(20) NOT NULL DEFAULT 'PRUEBA'
 CHECK (modo IN ('PRUEBA', 'PRODUCCION')),
 moneda CHAR(3) NOT NULL DEFAULT 'BOB', -- ISO 4217
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);
-- ================================================================
-- 8. CATEGORIA
-- Categorías de productos (vitrina/venta, insumo interno, etc.)
-- ================================================================
CREATE TABLE categoria (
 id_categoria SERIAL PRIMARY KEY,
 categoria_padre_id INTEGER,
 nombre VARCHAR(50) NOT NULL UNIQUE,
 descripcion VARCHAR(150),
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT fk_categoria_padre
 FOREIGN KEY (categoria_padre_id)
 REFERENCES categoria(id_categoria)
 ON UPDATE CASCADE
);
-- ================================================================
-- 9. PROVEEDOR
-- Proveedores de productos e insumos
-- (se eliminó "Attribute2" del diagrama por ser un placeholder)
-- ================================================================
CREATE TABLE proveedor (
 id_proveedor SERIAL PRIMARY KEY,
 empresa VARCHAR(80) NOT NULL,
 nit VARCHAR(20) NOT NULL UNIQUE,
 nombre VARCHAR(80), -- persona de
contacto
 telefono VARCHAR(15),
 correo VARCHAR(100)
);
-- ================================================================
-- 10. CAJA
-- Sesiones de caja diaria (apertura / cierre)
-- ================================================================
CREATE TABLE caja (
 id_caja SERIAL PRIMARY KEY,
 fecha_apertura TIMESTAMP NOT NULL DEFAULT NOW(),
 fecha_cierre TIMESTAMP,
 monto_inicial NUMERIC(10,2) NOT NULL CHECK (monto_inicial >= 0),
 monto_final NUMERIC(10,2), -- se registra al
cierre
 estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
 CHECK (estado IN ('ABIERTA', 'CERRADA')),
 CONSTRAINT chk_caja_fechas CHECK (fecha_cierre IS NULL
 OR fecha_cierre >= fecha_apertura)
);
-- 11. ROL_USUARIO
-- Tabla intermedia (M:N) entre USUARIO y ROL - resuelta en 1FN
-- ----------------------------------------------------------------
CREATE TABLE rol_usuario (
 usuario_id INTEGER NOT NULL REFERENCES
usuario(id_usuario) ON DELETE CASCADE,
 rol_id INTEGER NOT NULL REFERENCES rol(id_rol),
 fecha_asignacion DATE NOT NULL DEFAULT CURRENT_DATE,
 PRIMARY KEY (usuario_id, rol_id)
);
-- ----------------------------------------------------------------
-- 12. ROL_PERMISO
-- Tabla intermedia (M:N) entre ROL y PERMISO - resuelta en 1FN
-- ----------------------------------------------------------------
CREATE TABLE rol_permiso (
 rol_id INTEGER NOT NULL REFERENCES rol(id_rol)
ON DELETE CASCADE,
 permiso_id INTEGER NOT NULL REFERENCES
permiso(id_permiso),
 fecha_asignacion DATE NOT NULL DEFAULT CURRENT_DATE,
 PRIMARY KEY (rol_id, permiso_id)
);
-- ----------------------------------------------------------------
-- 13. EMPLEADO
-- Herencia USUARIO <|-- EMPLEADO mapeada con FK 1:1 (UNIQUE)
-- turno_id: FK extraída en 3FN (turno -> entrada, salida)
-- ----------------------------------------------------------------
CREATE TABLE empleado (
 id_empleado SERIAL PRIMARY KEY,
 usuario_id INTEGER NOT NULL UNIQUE REFERENCES
usuario(id_usuario),
 turno_id INTEGER REFERENCES turno(id_turno),
 especialidad VARCHAR(80),
 tipo_contrato VARCHAR(30) NOT NULL
 CHECK (tipo_contrato IN ('COMISIONISTA',
'ASALARIADO'))
);
-- ----------------------------------------------------------------
-- 14. CLIENTE
-- nombre y telefono viven AQUÍ (no en usuario) para que un cliente
-- walk-in u online pueda registrarse SIN cuenta (RF6 / RF16).
-- usuario_id nullable: vínculo opcional para clientes con cuenta
-- (relación 0..1 del diagrama). UNIQUE garantiza el 1 a 1.
-- Nota 3FN: visitas_acumuladas y ultima_visita se eliminaron por
-- ser atributos derivados de RESERVA / NOTA_VENTA.
-- ----------------------------------------------------------------
CREATE TABLE cliente (
 id_cliente SERIAL PRIMARY KEY,
 usuario_id INTEGER UNIQUE REFERENCES
usuario(id_usuario),
 nombre VARCHAR(80) NOT NULL,
 telefono VARCHAR(15),
 fecha_registro DATE NOT NULL DEFAULT CURRENT_DATE
);
-- ----------------------------------------------------------------
-- 15. BITACORA
-- Auditoría de acciones. ip_origen usa el tipo nativo INET de
-- PostgreSQL (acepta IPv4 e IPv6). datos_anteriores / datos_nuevos
-- como JSONB para guardar el registro completo de forma atómica.
-- ----------------------------------------------------------------
CREATE TABLE bitacora (
 id_bitacora SERIAL PRIMARY KEY,
 usuario_id INTEGER NOT NULL REFERENCES
usuario(id_usuario),
 accion VARCHAR(50) NOT NULL,
 detalle VARCHAR(200),
 tabla_afectada VARCHAR(50) NOT NULL,
 datos_anteriores JSONB,
 datos_nuevos JSONB,
 fecha_hora TIMESTAMP NOT NULL DEFAULT NOW(),
 ip_origen INET
);
-- ----------------------------------------------------------------
-- 16. PRODUCTO
-- Nota 3FN: se eliminó porcentaje_comision (redundante con
-- comision_unitaria; el porcentaje aplica a servicios).
-- ----------------------------------------------------------------
CREATE TABLE producto (
 id_producto SERIAL PRIMARY KEY,
 categoria_id INTEGER NOT NULL REFERENCES
categoria(id_categoria),
 nombre VARCHAR(80) NOT NULL,
 tipo VARCHAR(20) NOT NULL
 CHECK (tipo IN ('VENTA', 'INSUMO')),
 precio_venta NUMERIC(10,2) NOT NULL CHECK (precio_venta >=
0),
 costo NUMERIC(10,2) NOT NULL CHECK (costo >= 0),
 comision_unitaria NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(comision_unitaria >= 0),
 stock_actual INTEGER NOT NULL DEFAULT 0 CHECK
(stock_actual >= 0),
 stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK
(stock_minimo >= 0),
 activo BOOLEAN NOT NULL DEFAULT TRUE
);
-- ----------------------------------------------------------------
-- 17. SERVICIO_HORARIO
-- Tabla intermedia (M:N) entre SERVICIO y HORARIO
-- ----------------------------------------------------------------
CREATE TABLE servicio_horario (
 servicio_id INTEGER NOT NULL REFERENCES
servicio(id_servicio) ON DELETE CASCADE,
 horario_id INTEGER NOT NULL REFERENCES
horario(id_horario),
 PRIMARY KEY (servicio_id, horario_id)
);
-- ----------------------------------------------------------------
-- 18. MOVIMIENTO_CAJA
-- Composición con CAJA (rombo relleno del diagrama):
-- ON DELETE CASCADE, un movimiento no existe sin su caja.
-- ----------------------------------------------------------------
CREATE TABLE movimiento_caja (
 id_movimiento_caja SERIAL PRIMARY KEY,
 caja_id INTEGER NOT NULL REFERENCES
caja(id_caja) ON DELETE CASCADE,
 tipo VARCHAR(20) NOT NULL
 CHECK (tipo IN ('INGRESO', 'EGRESO')),
 concepto VARCHAR(100) NOT NULL,
 monto NUMERIC(10,2) NOT NULL CHECK (monto > 0),
 fecha TIMESTAMP NOT NULL DEFAULT NOW(),
 observaciones VARCHAR(200)
);
-- ----------------------------------------------------------------
-- 19. NOTA_COMPRA
-- Compras a proveedores
-- ----------------------------------------------------------------
CREATE TABLE nota_compra (
 id_nota_compra SERIAL PRIMARY KEY,
 proveedor_id INTEGER NOT NULL REFERENCES
proveedor(id_proveedor),
 metodo_pago_id INTEGER REFERENCES
pasarela_pago(id_metodo_pago),
 nro_fact VARCHAR(30),
 fecha DATE NOT NULL DEFAULT
CURRENT_DATE,
 fecha_entrega_programada DATE,
 fecha_entrega_real DATE,
 sub_total NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(sub_total >= 0)
);
-- ----------------------------------------------------------------
-- 20. EMPLEADO_SERVICIO
-- Tabla intermedia (M:N): qué servicios está habilitado a
-- realizar cada empleado
-- ----------------------------------------------------------------
CREATE TABLE empleado_servicio (
 empleado_id INTEGER NOT NULL REFERENCES
empleado(id_empleado) ON DELETE CASCADE,
 servicio_id INTEGER NOT NULL REFERENCES
servicio(id_servicio),
 estado VARCHAR(20) NOT NULL DEFAULT 'HABILITADO'
 CHECK (estado IN ('HABILITADO',
'INHABILITADO')),
 PRIMARY KEY (empleado_id, servicio_id)
);
-- ----------------------------------------------------------------
-- 21. CLIENTE_SERVICIO
-- Tabla intermedia (M:N): historial/preferencias de servicios
-- por cliente
-- ----------------------------------------------------------------
CREATE TABLE cliente_servicio (
 cliente_id INTEGER NOT NULL REFERENCES
cliente(id_cliente) ON DELETE CASCADE,
 servicio_id INTEGER NOT NULL REFERENCES
servicio(id_servicio),
 estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
 CHECK (estado IN ('ACTIVO', 'INACTIVO')),
 PRIMARY KEY (cliente_id, servicio_id)
);
-- ----------------------------------------------------------------
-- 22. RESERVA
-- Citas: cliente + empleado que atiende + servicio
-- Anticipo del 20% según reglas del negocio
-- ----------------------------------------------------------------
CREATE TABLE reserva (
 id_reserva SERIAL PRIMARY KEY,
 cliente_id INTEGER NOT NULL REFERENCES
cliente(id_cliente),
 empleado_id INTEGER REFERENCES
empleado(id_empleado),
 servicio_id INTEGER NOT NULL REFERENCES
servicio(id_servicio),
 fecha DATE NOT NULL,
 hora_inicio TIME NOT NULL,
 hora_fin TIME NOT NULL,
 canal VARCHAR(20) NOT NULL
 CHECK (canal IN ('PRESENCIAL', 'WHATSAPP',
'TELEFONO', 'WEB')),
 monto_anticipo NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(monto_anticipo >= 0),
 estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
 CHECK (estado IN ('PENDIENTE', 'CONFIRMADA',
'ATENDIDA',
 'CANCELADA', 'NO_ASISTIO')),
 CONSTRAINT chk_reserva_horas CHECK (hora_fin > hora_inicio)
);
-- ----------------------------------------------------------------
-- 23. NOTA_VENTA
-- Venta de servicios y productos. Los totales se guardan
-- "congelados" por trazabilidad histórica (decisión de diseño,
-- documentada en la normalización).
-- ----------------------------------------------------------------
CREATE TABLE nota_venta (
 id_nota_venta SERIAL PRIMARY KEY,
 cliente_id INTEGER NOT NULL REFERENCES
cliente(id_cliente),
 metodo_pago_id INTEGER NOT NULL REFERENCES
pasarela_pago(id_metodo_pago),
 caja_id INTEGER NOT NULL REFERENCES
caja(id_caja),
 nro_fact VARCHAR(30),
 nit VARCHAR(20),
 fecha TIMESTAMP NOT NULL DEFAULT NOW(),
 sub_total NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(sub_total >= 0),
 descuento NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(descuento >= 0),
 impuesto NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(impuesto >= 0),
 total NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total
>= 0),
 estado VARCHAR(20) NOT NULL DEFAULT 'EMITIDA'
 CHECK (estado IN ('EMITIDA', 'PAGADA',
'ANULADA'))
);
-- ----------------------------------------------------------------
-- 24. PAGO_EMPLEADO
-- Liquidación de sueldos / comisiones por período
-- ----------------------------------------------------------------
CREATE TABLE pago_empleado (
 id_pago SERIAL PRIMARY KEY,
 empleado_id INTEGER NOT NULL REFERENCES
empleado(id_empleado),
 fecha DATE NOT NULL DEFAULT CURRENT_DATE,
 monto_total NUMERIC(10,2) NOT NULL CHECK (monto_total >=
0)
);
-- ----------------------------------------------------------------
-- 25. DETALLE_NOTA_COMPRA
-- Composición con NOTA_COMPRA (rombo relleno): ON DELETE CASCADE
-- ----------------------------------------------------------------
CREATE TABLE detalle_nota_compra (
 id_detalle_nota_compra SERIAL PRIMARY KEY,
 nota_compra_id INTEGER NOT NULL REFERENCES
nota_compra(id_nota_compra) ON DELETE CASCADE,
 producto_id INTEGER NOT NULL REFERENCES
producto(id_producto),
 cantidad INTEGER NOT NULL CHECK (cantidad >
0),
 precio_unitario NUMERIC(10,2) NOT NULL CHECK
(precio_unitario >= 0),
 descuento NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK
(descuento >= 0),
 sub_total NUMERIC(10,2) NOT NULL CHECK (sub_total >=
0)
);
-- ----------------------------------------------------------------
-- 26. DETALLE_NOTA_VENTA
-- Productos vendidos en una nota de venta.
-- Composición con NOTA_VENTA: ON DELETE CASCADE.
-- precio_unitario "congelado" al momento de la venta.
-- ----------------------------------------------------------------
CREATE TABLE detalle_nota_venta (
 id_detalle_nota_venta SERIAL PRIMARY KEY,
 nota_venta_id INTEGER NOT NULL REFERENCES
nota_venta(id_nota_venta) ON DELETE CASCADE,
 producto_id INTEGER NOT NULL REFERENCES
producto(id_producto),
 cantidad INTEGER NOT NULL CHECK (cantidad >
0),
 precio_unitario NUMERIC(10,2) NOT NULL CHECK
(precio_unitario >= 0),
 sub_total NUMERIC(10,2) NOT NULL CHECK (sub_total >=
0),
 descripcion VARCHAR(150)
);
-- ----------------------------------------------------------------
-- 27. DETALLE_SERVICIO
-- Servicios cobrados en una nota de venta, con el empleado que
-- lo realizó y el % de comisión "congelado" para la liquidación.
-- ----------------------------------------------------------------
CREATE TABLE detalle_servicio (
 id_detalle_servicio SERIAL PRIMARY KEY,
 nota_venta_id INTEGER NOT NULL REFERENCES
nota_venta(id_nota_venta) ON DELETE CASCADE,
 servicio_id INTEGER NOT NULL REFERENCES
servicio(id_servicio),
 empleado_id INTEGER NOT NULL REFERENCES
empleado(id_empleado),
 estado VARCHAR(20) NOT NULL DEFAULT 'REALIZADO'
 CHECK (estado IN ('REALIZADO', 'ANULADO')),
 porcentaje_comision NUMERIC(5,2) NOT NULL DEFAULT 0
 CHECK (porcentaje_comision BETWEEN 0 AND
100)
);
-- ----------------------------------------------------------------
-- 28. DETALLE_PAGO_EMPLEADO
-- Resolución de la relación M:N entre PAGO_EMPLEADO y los
-- servicios liquidados (creada en 1FN). Vincula cada pago con
-- los detalles de servicio comisionados.
-- ----------------------------------------------------------------
CREATE TABLE detalle_pago_empleado (
 pago_id INTEGER NOT NULL REFERENCES
pago_empleado(id_pago) ON DELETE CASCADE,
 detalle_servicio_id INTEGER NOT NULL REFERENCES
detalle_servicio(id_detalle_servicio),
 monto_comision NUMERIC(10,2) NOT NULL CHECK
(monto_comision >= 0),
 PRIMARY KEY (pago_id, detalle_servicio_id)
);