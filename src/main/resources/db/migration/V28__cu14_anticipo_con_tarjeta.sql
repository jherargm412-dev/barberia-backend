-- CU14 flujo A (pago web del anticipo): el cliente paga con tarjeta mediante Stripe (CU29).
-- Reemplaza al QR simulado, que daba por pagado un QR sin consultar a ningún banco.
--   - metodo TARJETA: todo el anticipo con tarjeta. Los pagos QR ya registrados se conservan tal cual.
--   - monto_tarjeta: parte cobrada con tarjeta (0 en efectivo, QR y mixto).
--   - referencia_tarjeta: id del PaymentIntent de Stripe (pi_...). UNIQUE: un cobro paga una sola reserva.
-- El ingreso entra a movimiento_caja con medio TARJETA (V21), que no cambia el efectivo del arqueo.

ALTER TABLE pago_anticipo ADD COLUMN monto_tarjeta NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (monto_tarjeta >= 0);
ALTER TABLE pago_anticipo ADD COLUMN referencia_tarjeta VARCHAR(100);
CREATE UNIQUE INDEX ux_pago_anticipo_referencia_tarjeta ON pago_anticipo (referencia_tarjeta)
    WHERE referencia_tarjeta IS NOT NULL;

ALTER TABLE pago_anticipo DROP CONSTRAINT pago_anticipo_metodo_check;
ALTER TABLE pago_anticipo ADD CONSTRAINT pago_anticipo_metodo_check
    CHECK (metodo IN ('EFECTIVO', 'QR', 'MIXTO', 'TARJETA'));

ALTER TABLE pago_anticipo DROP CONSTRAINT chk_pago_anticipo_suma;
ALTER TABLE pago_anticipo ADD CONSTRAINT chk_pago_anticipo_suma
    CHECK (monto_total = monto_efectivo + monto_qr + monto_tarjeta);

ALTER TABLE pago_anticipo DROP CONSTRAINT chk_pago_anticipo_metodo;
ALTER TABLE pago_anticipo ADD CONSTRAINT chk_pago_anticipo_metodo CHECK (
    (metodo = 'EFECTIVO'   AND monto_efectivo > 0 AND monto_qr = 0       AND monto_tarjeta = 0)
    OR (metodo = 'QR'      AND monto_qr > 0       AND monto_efectivo = 0 AND monto_tarjeta = 0)
    OR (metodo = 'MIXTO'   AND monto_efectivo > 0 AND monto_qr > 0       AND monto_tarjeta = 0)
    OR (metodo = 'TARJETA' AND monto_tarjeta > 0  AND monto_efectivo = 0 AND monto_qr = 0
        AND referencia_tarjeta IS NOT NULL)
);
