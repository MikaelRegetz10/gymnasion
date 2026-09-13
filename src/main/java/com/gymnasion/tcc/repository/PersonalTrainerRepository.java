package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalTrainerRepository extends JpaRepository<PersonalTrainer, UUID> {
    Optional<PersonalTrainer> getByUsuario(Usuario usuario);
}
