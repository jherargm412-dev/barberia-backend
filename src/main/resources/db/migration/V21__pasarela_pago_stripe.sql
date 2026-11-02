-- Pasarela de pago (Stripe) en la sección Venta / Caja.
-- pasarela_pago es el catálogo de métodos/pasarelas. api_key guarda SOLO la clave pública (pk_...):
-- al iniciar, el backend la copia desde STRIPE_PUBLISHABLE_KEY. La secreta (sk_...) nunca va a la BD.

CREATE TABLE pasarela_pago (
    id_metodo_pago SERIAL       PRIMARY KEY,
    nombre         VARCHAR(30)  NOT NULL UNIQUE,
    descripcion    VARCHAR(150),
    proveedor      VARCHAR(20)  NOT NULL DEFAULT 'NINGUNO' CHECK (proveedor IN ('NINGUNO', 'STRIPE')),
    api_key        VARCHAR(255),
    modo           VARCHAR(20)  NOT NULL DEFAULT 'PRUEBA' CHECK (modo IN ('PRUEBA', 'PRODUCCION')),
    moneda         CHAR(3)      NOT NULL DEFAULT 'BOB',
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO pasarela_pago (nombre, descripcion, proveedor) VALUES
('Efectivo', 'Dinero físico en el cajón; entra al arqueo', 'NINGUNO'),
('QR',       'Transferencia por QR; no va al cajón',      'NINGUNO'),
('Stripe',   'Cobro con tarjeta mediante Stripe',          'STRIPE');

-- Los cobros con tarjeta entran a la caja como INGRESO con medio TARJETA (no afectan al efectivo esperado).
ALTER TABLE movimiento_caja DROP CONSTRAINT movimiento_caja_medio_pago_check;
ALTER TABLE movimiento_caja ADD CONSTRAINT movimiento_caja_medio_pago_check
    CHECK (medio_pago IN ('EFECTIVO', 'QR', 'TARJETA'));

ALTER TABLE movimiento_caja ADD COLUMN pasarela_pago_id INTEGER REFERENCES pasarela_pago(id_metodo_pago);
-- Id del PaymentIntent de Stripe (pi_...). UNIQUE: un mismo cobro no se registra dos veces.
ALTER TABLE movimiento_caja ADD COLUMN referencia_externa VARCHAR(100);
CREATE UNIQUE INDEX ux_movimiento_caja_referencia_externa ON movimiento_caja (referencia_externa)
    WHERE referencia_externa IS NOT NULL;
