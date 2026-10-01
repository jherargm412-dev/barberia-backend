package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

/** Respuesta de escritura de CU17: mensaje de confirmación + empleado guardado. */
public record RespuestaEmpleado(String mensaje, EmpleadoResponse empleado) {

    public static final String MENSAJE_REGISTRADO = "Empleado registrado correctamente";
    public static final String MENSAJE_GUARDADO = "Empleado guardado correctamente";
    public static final String MENSAJE_SERVICIOS = "Servicios actualizados correctamente";
    public static final String MENSAJE_DESVINCULADO = "Empleado desvinculado correctamente";
    public static final String MENSAJE_REACTIVADO = "Empleado reactivado correctamente";
}
