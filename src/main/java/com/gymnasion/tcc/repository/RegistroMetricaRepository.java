package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.RegistroMetrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RegistroMetricaRepository extends JpaRepository<RegistroMetrica, UUID> {
}
