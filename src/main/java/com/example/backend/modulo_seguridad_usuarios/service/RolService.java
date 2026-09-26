package com.example.backend.modulo_seguridad_usuarios.service;

import com.example.backend.modulo_seguridad_usuarios.repository.RolRepository;
import com.example.backend.modulo_seguridad_usuarios.dto.RolResumen;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lectura de roles existentes: lo único de CU03 que CU01 necesita (poblar el selector de rol). */
@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;
    private final UsuarioMapper mapper;

    @Transactional(readOnly = true)
    public List<RolResumen> listar(boolean activo) {
        return rolRepository.findByActivoOrderByNombreAsc(activo).stream().map(mapper::aRolResumen).toList();
    }
}
