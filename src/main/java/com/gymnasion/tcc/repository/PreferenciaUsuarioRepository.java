package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.PreferenciaUsuario;
import com.gymnasion.tcc.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreferenciaUsuarioRepository extends JpaRepository<PreferenciaUsuario, UUID> {
    Optional<PreferenciaUsuario> findByUsuario(Usuario usuario);
    Optional<PreferenciaUsuario> findByUsuarioId(UUID usuarioId);
}
