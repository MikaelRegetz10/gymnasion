package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.CampoAuditado;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.TipoOperacaoAuditoria;
import com.gymnasion.tcc.dto.AuditoriaDesempenhoResponseDTO;
import com.gymnasion.tcc.dto.CorrigirDesempenhoRequestDTO;
import com.gymnasion.tcc.dto.DesempenhoDetalheResponseDTO;
import com.gymnasion.tcc.dto.RegistrarResultadoMetricaDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.MetricasInvalidasException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AuditoriaDesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.SessaoTreinoRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * US-23: Correção de registros de desempenho e preenchimento de métricas pendentes pelo personal responsável,
 * com auditoria de inclusões e correções (valor anterior, novo valor, responsável e data/hora).
 */
@Service
@AllArgsConstructor
public class CorrecaoDesempenhoService {

    private final PersonalTrainerRepository personalTrainerRepository;
    private final SessaoTreinoRepository sessaoTreinoRepository;
    private final AuditoriaDesempenhoRepository auditoriaDesempenhoRepository;
    private final AcompanhamentoTreinoService acompanhamentoTreinoService;

    @Transactional(readOnly = true)
    public DesempenhoDetalheResponseDTO consultarRegistro(Usuario usuario, UUID sessaoId) {
        // US-23 Cenário 1 e 8: Somente o personal responsável acessa o registro para correção
        SessaoTreino sessao = obterSessaoComDesempenhoDoPersonal(usuario, sessaoId);
        return toDetalheDTO(sessao);
    }

    @Transactional
    public DesempenhoDetalheResponseDTO corrigirRegistro(Usuario usuario, UUID sessaoId, CorrigirDesempenhoRequestDTO dto) {
        SessaoTreino sessao = obterSessaoComDesempenhoDoPersonal(usuario, sessaoId);
        Desempenho desempenho = sessao.getDesempenho();

        Map<String, String> camposInvalidos = new LinkedHashMap<>();

        // US-23 Cenário 4: Observações são salvas após a validação dos dados
        String observacoes = dto.observacoes() != null ? dto.observacoes().trim() : null;
        if (observacoes != null && observacoes.isEmpty()) {
            camposInvalidos.put("observacoes", "As observações não podem ficar em branco.");
        }

        // US-23 Cenários 2, 6 e 7: Valida todas as métricas antes de aplicar qualquer alteração
        Map<UUID, Metrica> metricasDaRotina = new HashMap<>();
        sessao.getRotinaTreino().getMetricas().forEach(metrica -> metricasDaRotina.put(metrica.getId(), metrica));

        List<AlteracaoMetrica> alteracoes = new ArrayList<>();
        Set<UUID> metricasInformadas = new HashSet<>();
        List<RegistrarResultadoMetricaDTO> metricas = dto.metricas() != null ? dto.metricas() : List.of();
        for (RegistrarResultadoMetricaDTO resDto : metricas) {
            if (!metricasInformadas.add(resDto.metricaId())) {
                camposInvalidos.put(resDto.metricaId().toString(), "A métrica foi informada mais de uma vez.");
                continue;
            }

            RegistroMetrica registroExistente = buscarRegistro(desempenho, resDto.metricaId());
            Metrica metrica = registroExistente != null ? registroExistente.getMetrica() : metricasDaRotina.get(resDto.metricaId());
            if (metrica == null) {
                throw new BusinessException("A métrica informada não está associada à rotina desta sessão.");
            }

            if (registroExistente == null) {
                // US-23 Cenário 7: Inclusão somente de métricas pendentes que o personal pode preencher (US-18 C5)
                if (metrica.getQuemPreenche() == ResponsavelPreenchimento.ALUNO) {
                    throw new BusinessException("A métrica '" + metrica.getTitulo() + "' é de preenchimento exclusivo do aluno.");
                }
                // RN-017: Métrica descontinuada não recebe novos registros
                if (!Boolean.TRUE.equals(metrica.getAtivo())) {
                    throw new BusinessException("A métrica '" + metrica.getTitulo() + "' foi descontinuada e não aceita novos registros.");
                }
            }

            String valor = resDto.valor().trim();
            String erroTipo = ValidadorValorMetrica.validar(metrica, valor);
            if (erroTipo != null) {
                camposInvalidos.put(metrica.getId().toString(), erroTipo);
                continue;
            }
            alteracoes.add(new AlteracaoMetrica(metrica, registroExistente, valor));
        }

        // US-23 Cenário 6: Impede o salvamento, informa os campos e preserva os dados anteriores
        if (!camposInvalidos.isEmpty()) {
            throw new MetricasInvalidasException(
                    "Existem campos com valores inválidos. Corrija os campos indicados antes de salvar a correção.",
                    camposInvalidos
            );
        }

        List<AuditoriaDesempenho> auditorias = new ArrayList<>();

        // US-23 Cenário 3: Corrige a data real e preserva a data planejada
        if (dto.dataReal() != null && !dto.dataReal().equals(sessao.getDataReal())) {
            auditorias.add(auditoria(desempenho, usuario, CampoAuditado.DATA_REAL, null,
                    sessao.getDataReal() != null ? sessao.getDataReal().toString() : null,
                    dto.dataReal().toString()));
            sessao.setDataReal(dto.dataReal());
        }

        // US-23 Cenário 4: Corrige as observações do desempenho
        if (observacoes != null && !observacoes.equals(desempenho.getObservacoes())) {
            auditorias.add(auditoria(desempenho, usuario, CampoAuditado.OBSERVACOES, null,
                    desempenho.getObservacoes(), observacoes));
            desempenho.setObservacoes(observacoes);
        }

        // US-23 Cenários 2 e 7: Correção de valores existentes e inclusão de métricas pendentes
        boolean alterouMetricas = false;
        for (AlteracaoMetrica alteracao : alteracoes) {
            RegistroMetrica registro = alteracao.registroExistente();
            if (registro == null) {
                desempenho.getRegistrosMetricas().add(RegistroMetrica.builder()
                        .desempenho(desempenho)
                        .metrica(alteracao.metrica())
                        .valor(alteracao.valor())
                        .build());
                auditorias.add(auditoria(desempenho, usuario, CampoAuditado.METRICA, alteracao.metrica(), null, alteracao.valor()));
                alterouMetricas = true;
            } else if (!alteracao.valor().equals(registro.getValor())) {
                auditorias.add(auditoria(desempenho, usuario, CampoAuditado.METRICA, alteracao.metrica(), registro.getValor(), alteracao.valor()));
                registro.setValor(alteracao.valor());
                alterouMetricas = true;
            }
        }

        if (auditorias.isEmpty()) {
            throw new BusinessException("Nenhuma alteração informada.");
        }

        // US-23 Cenário 5: Salva as informações atualizadas e registra a auditoria
        sessao = sessaoTreinoRepository.save(sessao);
        auditoriaDesempenhoRepository.saveAll(auditorias);

        // US-24 C5 e C6: Preencher métricas pendentes atualiza a contagem da notificação e do perfil
        if (alterouMetricas && desempenho.getAluno() != null) {
            acompanhamentoTreinoService.sincronizarNotificacaoParaAluno(desempenho.getAluno());
        }

        return toDetalheDTO(sessao);
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaDesempenhoResponseDTO> consultarAuditoria(Usuario usuario, UUID sessaoId, Pageable pageable) {
        // US-23 Cenário 5: Valores anteriores e novos disponíveis para consulta do histórico de alterações
        SessaoTreino sessao = obterSessaoComDesempenhoDoPersonal(usuario, sessaoId);
        return auditoriaDesempenhoRepository.findByDesempenhoIdOrderByDataHoraAsc(sessao.getDesempenho().getId(), pageable)
                .map(this::toAuditoriaResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AuditoriaDesempenhoResponseDTO> consultarAuditoria(Usuario usuario, UUID sessaoId) {
        // US-23 Cenário 5: Valores anteriores e novos disponíveis para consulta do histórico de alterações
        SessaoTreino sessao = obterSessaoComDesempenhoDoPersonal(usuario, sessaoId);
        return auditoriaDesempenhoRepository.findByDesempenhoIdOrderByDataHoraAsc(sessao.getDesempenho().getId())
                .stream()
                .map(this::toAuditoriaResponseDTO)
                .toList();
    }

    private AuditoriaDesempenhoResponseDTO toAuditoriaResponseDTO(AuditoriaDesempenho auditoria) {
        return new AuditoriaDesempenhoResponseDTO(
                auditoria.getId(),
                auditoria.getTipoOperacao(),
                auditoria.getCampo(),
                auditoria.getMetrica() != null ? auditoria.getMetrica().getId() : null,
                auditoria.getMetrica() != null ? auditoria.getMetrica().getTitulo() : null,
                auditoria.getValorAnterior(),
                auditoria.getValorNovo(),
                auditoria.getUsuario().getId(),
                auditoria.getUsuario().getNome(),
                auditoria.getDataHora()
        );
    }

    /**
     * US-23 Cenário 8: Garante que somente o personal responsável pelo aluno acesse o registro.
     */
    private SessaoTreino obterSessaoComDesempenhoDoPersonal(Usuario usuario, UUID sessaoId) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));

        SessaoTreino sessao = sessaoTreinoRepository.findByIdAndPersonalTrainerId(sessaoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Sessão de treino não encontrada para este Personal Trainer."));

        Desempenho desempenho = sessao.getDesempenho();
        if (desempenho == null) {
            throw new BusinessException("Esta sessão ainda não possui registro de desempenho.");
        }

        Aluno aluno = desempenho.getAluno();
        if (aluno != null && aluno.getPersonal() != null && !aluno.getPersonal().getId().equals(personal.getId())) {
            throw new NotFoundException("Sessão de treino não encontrada para este Personal Trainer.");
        }
        return sessao;
    }

    private RegistroMetrica buscarRegistro(Desempenho desempenho, UUID metricaId) {
        return desempenho.getRegistrosMetricas().stream()
                .filter(registro -> registro.getMetrica().getId().equals(metricaId))
                .findFirst()
                .orElse(null);
    }

    private AuditoriaDesempenho auditoria(Desempenho desempenho, Usuario usuario, CampoAuditado campo,
                                          Metrica metrica, String valorAnterior, String valorNovo) {
        return AuditoriaDesempenho.builder()
                .desempenho(desempenho)
                // US-23 notas técnicas: preencher valor inexistente = inclusão; alterar valor existente = correção
                .tipoOperacao(valorAnterior == null ? TipoOperacaoAuditoria.INCLUSAO : TipoOperacaoAuditoria.CORRECAO)
                .campo(campo)
                .metrica(metrica)
                .valorAnterior(valorAnterior)
                .valorNovo(valorNovo)
                .usuario(usuario)
                .build();
    }

    private DesempenhoDetalheResponseDTO toDetalheDTO(SessaoTreino sessao) {
        Desempenho desempenho = sessao.getDesempenho();

        Map<UUID, String> valores = new HashMap<>();
        Map<UUID, Metrica> metricas = new LinkedHashMap<>();
        sessao.getRotinaTreino().getMetricas().forEach(metrica -> metricas.put(metrica.getId(), metrica));
        for (RegistroMetrica registro : desempenho.getRegistrosMetricas()) {
            valores.put(registro.getMetrica().getId(), registro.getValor());
            metricas.putIfAbsent(registro.getMetrica().getId(), registro.getMetrica());
        }

        // RN-017: Métrica descontinuada só aparece quando possui valor registrado
        List<DesempenhoDetalheResponseDTO.MetricaDesempenhoDTO> metricasDTO = metricas.values().stream()
                .filter(metrica -> Boolean.TRUE.equals(metrica.getAtivo()) || valores.containsKey(metrica.getId()))
                .sorted(Comparator.comparing(Metrica::getTitulo))
                .map(metrica -> {
                    String valor = valores.get(metrica.getId());
                    boolean inclusaoPermitida = valor == null
                            && Boolean.TRUE.equals(metrica.getAtivo())
                            && metrica.getQuemPreenche() != ResponsavelPreenchimento.ALUNO;
                    return new DesempenhoDetalheResponseDTO.MetricaDesempenhoDTO(
                            metrica.getId(),
                            metrica.getTitulo(),
                            metrica.getTipo(),
                            metrica.getObrigatoria(),
                            metrica.getQuemPreenche(),
                            metrica.getAtivo(),
                            valor,
                            valor != null,
                            inclusaoPermitida
                    );
                })
                .toList();

        Aluno aluno = desempenho.getAluno();
        return new DesempenhoDetalheResponseDTO(
                sessao.getId(),
                sessao.getRotinaTreino().getId(),
                desempenho.getId(),
                aluno != null ? aluno.getId() : null,
                aluno != null && aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null,
                sessao.getSemana(),
                sessao.getDataPlanejada(),
                sessao.getDataReal(),
                sessao.getStatus(),
                sessao.getConcluidaAluno(),
                sessao.getValidadaPersonal(),
                desempenho.getNivelCansaco(),
                desempenho.getRegistradoPor(),
                desempenho.getDataRegistro(),
                desempenho.getObservacoes(),
                metricasDTO
        );
    }

    private record AlteracaoMetrica(Metrica metrica, RegistroMetrica registroExistente, String valor) {}
}
