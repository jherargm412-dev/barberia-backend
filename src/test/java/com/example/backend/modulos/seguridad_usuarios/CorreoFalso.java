package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.comun.correo.CorreoService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Cartero falso" de las pruebas: guarda los correos en memoria en vez de enviarlos. */
public class CorreoFalso implements CorreoService {

    public record Mensaje(String para, String asunto, String cuerpo) {
    }

    private static final Pattern CODIGO = Pattern.compile("Tu código es: (\\d{6})");

    private final List<Mensaje> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String asunto, String cuerpo) {
        enviados.add(new Mensaje(para, asunto, cuerpo));
    }

    public List<Mensaje> enviadosA(String para) {
        List<Mensaje> resultado = new ArrayList<>();
        for (Mensaje m : enviados) {
            if (m.para().equals(para)) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    /** Código de 6 dígitos del último correo de recuperación enviado a {@code para}. */
    public Optional<String> ultimoCodigo(String para) {
        List<Mensaje> mensajes = enviadosA(para);
        if (mensajes.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = CODIGO.matcher(mensajes.getLast().cuerpo());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
