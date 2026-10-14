package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

import java.time.LocalDateTime;

/** Extra de CU04: un inicio de sesión del propio usuario, tomado de la bitácora. */
public record InicioSesionReciente(LocalDateTime fechaHora, String ipOrigen) {
}
