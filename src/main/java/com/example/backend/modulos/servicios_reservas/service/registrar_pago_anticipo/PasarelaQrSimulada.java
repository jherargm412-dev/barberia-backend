package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;

/**
 * SIMULACIÓN de la pasarela de pagos QR: no consulta ningún banco. Recuerda en memoria los QR que emite
 * y da por pagado uno que sea vigente y corresponda a esa reserva y a ese monto. Un QR vale unos minutos
 * ({@code app.pagos.qr.vigencia-minutos}, 15 por defecto); al reiniciar el backend los QR emitidos se pierden
 * y el cliente solo pide otro. Para conectar un banco real se escribe otra implementación de {@link PasarelaQr}
 * y se retira esta.
 */
@Component
public class PasarelaQrSimulada implements PasarelaQr {

    /** Lo que la pasarela recuerda de cada QR emitido. */
    private record QrEmitido(Integer idReserva, BigDecimal monto, LocalDateTime venceEn) {
    }

    private final Map<String, QrEmitido> emitidos = new ConcurrentHashMap<>();

    @Value("${app.pagos.qr.vigencia-minutos:15}")
    private long vigenciaMinutos;

    @Override
    public QrGenerado generar(Integer idReserva, BigDecimal monto, LocalDateTime venceComoMaximo) {
        LocalDateTime ahora = ahora();
        // Para que la memoria no crezca sin límite, se olvidan los QR ya vencidos.
        emitidos.values().removeIf(qr -> qr.venceEn().isBefore(ahora));

        String referencia = "HOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        LocalDateTime venceEn = ahora.plusMinutes(vigenciaMinutos);
        if (venceComoMaximo != null && venceComoMaximo.isBefore(venceEn)) {
            venceEn = venceComoMaximo;
        }
        emitidos.put(referencia, new QrEmitido(idReserva, monto, venceEn));

        String contenido = "HOUSEOFCUT|" + referencia + "|" + monto.toPlainString();
        return new QrGenerado(referencia, contenido, venceEn);
    }

    @Override
    public boolean verificarPago(String referencia, Integer idReserva, BigDecimal monto) {
        QrEmitido qr = referencia == null ? null : emitidos.get(referencia);
        if (qr == null) {
            return false; // referencia desconocida: no la emitimos nosotros
        }
        if (qr.venceEn().isBefore(ahora())) {
            emitidos.remove(referencia);
            return false; // QR vencido
        }
        return qr.idReserva().equals(idReserva) && qr.monto().compareTo(monto) == 0;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }
}
