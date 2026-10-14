package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.AuditoriaDesempenho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditoriaDesempenhoRepository extends JpaRepository<AuditoriaDesempenho, UUID> {
    List<AuditoriaDesempenho> findByDesempenhoIdOrderByDataHoraAsc(UUID desempenhoId);
}
