package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.RotinaTreino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RotinaTreinoRepository extends JpaRepository<RotinaTreino, UUID> {

    List<RotinaTreino> findByPersonalTrainerId(UUID personalTrainerId);

    List<RotinaTreino> findByPersonalTrainerIdAndAlunosId(UUID personalTrainerId, UUID alunoId);

    Optional<RotinaTreino> findByIdAndPersonalTrainerId(UUID id, UUID personalTrainerId);
}