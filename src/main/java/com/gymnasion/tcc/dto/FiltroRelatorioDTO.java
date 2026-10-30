package com.gymnasion.tcc.dto;

import com.gymnasion.tcc.domain.enums.ConteudoRelatorio;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Escolhas do usuário para o relatório de performance (US-16).
 * Sem datas, métricas ou conteúdos selecionados, o relatório sai completo dentro do período.
 */
public record FiltroRelatorioDTO(
        Long modalidadeId,
        LocalDate dataInicio,
        LocalDate dataFim,
        Set<LocalDate> datas,
        Set<UUID> metricaIds,
        Set<ConteudoRelatorio> conteudos
) {

    public boolean selecionouDatas() {
        return datas != null && !datas.isEmpty();
    }

    public boolean incluiData(LocalDate data) {
        return !selecionouDatas() || datas.contains(data);
    }

    public boolean selecionouMetricas() {
        return metricaIds != null && !metricaIds.isEmpty();
    }

    public boolean incluiMetrica(UUID metricaId) {
        return !selecionouMetricas() || metricaIds.contains(metricaId);
    }

    public Set<ConteudoRelatorio> conteudosSelecionados() {
        return conteudos == null || conteudos.isEmpty() ? EnumSet.allOf(ConteudoRelatorio.class) : conteudos;
    }
}
