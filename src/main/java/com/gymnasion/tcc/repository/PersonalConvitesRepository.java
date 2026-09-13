package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.PersonalConvites;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalConvitesRepository extends JpaRepository<PersonalConvites, UUID> {

    Optional<PersonalConvites> findByToken(String token);

    List<PersonalConvites> findByPersonalId(UUID personalId);

    List<PersonalConvites> findByPersonalIdAndStatus(UUID personalId, String status);

    @Modifying
    @Query("UPDATE PersonalConvites p SET p.quantidadesUsuarios = p.quantidadesUsuarios + 1 WHERE p.id = :id")
    void incrementarQuantidadeUsuarios(UUID id);
}