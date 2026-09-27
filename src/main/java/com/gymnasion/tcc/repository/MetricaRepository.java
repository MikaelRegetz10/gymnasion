package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Metrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricaRepository extends JpaRepository<Metrica, UUID> {

    boolean existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(UUID personalId, Long modalidadeId, String titulo);

    boolean existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCaseAndIdNot(UUID personalId, Long modalidadeId, String titulo, UUID id);

    List<Metrica> findByPersonalTrainerIdAndModalidadeId(UUID personalId, Long modalidadeId);

    Optional<Metrica> findByIdAndPersonalTrainerId(UUID id, UUID personalId);
}