package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gymnasion.tcc.domain.enums.TipoNotificacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(UUID usuarioId);

    Page<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioIdAndLidaFalse(UUID usuarioId);

    Optional<Notificacao> findByUsuarioIdAndTipoAndReferenciaId(UUID usuarioId, TipoNotificacao tipo, UUID referenciaId);

    void deleteByUsuarioIdAndTipoAndReferenciaId(UUID usuarioId, TipoNotificacao tipo, UUID referenciaId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.usuario.id = :usuarioId AND n.lida = false")
    int marcarTodasComoLidas(@Param("usuarioId") UUID usuarioId);
}
