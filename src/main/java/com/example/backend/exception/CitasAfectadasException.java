package com.example.backend.exception;

import java.util.List;

/** HTTP 409 con la lista de citas que impiden guardar un horario o una excepción (CU09 7c y 4f). */
public class CitasAfectadasException extends ConflictoException {

    private final List<?> citas;

    public CitasAfectadasException(String mensaje, List<?> citas) {
        super(mensaje);
        this.citas = List.copyOf(citas);
    }

    public List<?> getCitas() {
        return citas;
    }
}
