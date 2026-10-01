package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios;

import java.time.LocalDateTime;

/** En el detalle de usuario (CU01): la invitación que todavía no aceptó. {@code vencida} = hay que reenviarla. */
public record InvitacionPendiente(LocalDateTime expiraEn, boolean vencida) {
}
