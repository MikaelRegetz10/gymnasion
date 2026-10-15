package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.DesempenhoRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Serviço de agregação de dados para os dashboards e gráficos dinâmicos de evolução (US-15 / US-20 C3).
 * Atende às regras de negócio RN-005, RN-006, RN-014, RN-015, RN-017 e RN-018.
 */
@Service
@RequiredArgsConstructor
public class DashboardEvolucaoService {

    private final AlunoRepository alunoRepository;
    private final PersonalTrainerRepository personalTrainerRepository;
    private final DesempenhoRepository desempenhoRepository;

    @Transactional(readOnly = true)
    public DashboardEvolucaoResponseDTO obterDashboardPeloPersonal(
            Usuario usuario,
            UUID alunoId,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        PersonalTrainer personal = personalTrainerRepository.getByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Personal Trainer não encontrado."));

        Aluno aluno = alunoRepository.findByIdAndPersonalId(alunoId, personal.getId())
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado para este Personal Trainer."));

        return gerarDashboard(aluno, modalidadeId, dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public DashboardEvolucaoResponseDTO obterDashboardPeloAluno(
            Usuario usuario,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        Aluno aluno = alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        // RN-014: Aluno inativo não pode acessar
        if (aluno.getStatus() == StatusConvite.INATIVO) {
            throw new BusinessException("Erro. Este aluno está inativo.");
        }

        return gerarDashboard(aluno, modalidadeId, dataInicio, dataFim);
    }

    private DashboardEvolucaoResponseDTO gerarDashboard(
            Aluno aluno,
            Long modalidadeId,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        // Validação de intervalo temporal (US-15 C2)
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new BusinessException("A data de início não pode ser posterior à data de fim.");
        }

        // Busca todos os desempenhos registrados para o aluno com suas sessões e métricas
        List<Desempenho> todosDesempenhos = desempenhoRepository.findByAlunoIdWithMetricas(aluno.getId());

        // Identifica todas as modalidades disponíveis (praticadas + histórico de treinos)
        Map<Long, Modalidade> mapaModalidades = new LinkedHashMap<>();
        if (aluno.getModalidades() != null) {
            for (Modalidade m : aluno.getModalidades()) {
                mapaModalidades.put(m.getId(), m);
            }
        }
        for (Desempenho d : todosDesempenhos) {
            if (d.getSessaoTreino() != null && d.getSessaoTreino().getRotinaTreino() != null) {
                Modalidade m = d.getSessaoTreino().getRotinaTreino().getModalidade();
                if (m != null) {
                    mapaModalidades.putIfAbsent(m.getId(), m);
                }
            }
        }

        List<ModalidadeResumoDashboardDTO> modalidadesDisponiveis = mapaModalidades.values().stream()
                .map(m -> new ModalidadeResumoDashboardDTO(m.getId(), m.getNome()))
                .toList();

        // Determina a modalidade ativa da consulta
        Modalidade modalidadeSelecionada = null;
        if (modalidadeId != null) {
            modalidadeSelecionada = mapaModalidades.get(modalidadeId);
            if (modalidadeSelecionada == null) {
                throw new NotFoundException("Modalidade não encontrada para este aluno.");
            }
        } else if (!mapaModalidades.isEmpty()) {
            modalidadeSelecionada = mapaModalidades.values().iterator().next();
        }

        Long finalModalidadeId = modalidadeSelecionada != null ? modalidadeSelecionada.getId() : null;

        // Histórico completo de desempenhos da modalidade (para checagem do limiar de 3 sessões - US-15 C1)
        List<Desempenho> desempenhosModalidade = todosDesempenhos.stream()
                .filter(d -> finalModalidadeId == null || (
                        d.getSessaoTreino() != null &&
                        d.getSessaoTreino().getRotinaTreino() != null &&
                        d.getSessaoTreino().getRotinaTreino().getModalidade() != null &&
                        finalModalidadeId.equals(d.getSessaoTreino().getRotinaTreino().getModalidade().getId())
                ))
                .sorted(Comparator.comparing((Desempenho d) -> obterDataReferencia(d.getSessaoTreino()))
                        .thenComparing(Desempenho::getDataRegistro))
                .toList();

        int totalSessoesHistorico = desempenhosModalidade.size();
        boolean possuiDadosSuficientes = totalSessoesHistorico >= 3;
        String mensagem = possuiDadosSuficientes
                ? null
                : "São necessárias pelo menos 3 sessões registradas para exibir os gráficos de evolução.";

        // Filtro de período aplicado sobre o conjunto (US-15 C2)
        List<Desempenho> desempenhosPeriodo = desempenhosModalidade.stream()
                .filter(d -> {
                    LocalDate data = obterDataReferencia(d.getSessaoTreino());
                    if (dataInicio != null && data.isBefore(dataInicio)) return false;
                    if (dataFim != null && data.isAfter(dataFim)) return false;
                    return true;
                })
                .toList();

        // Agregação das séries temporais das métricas
        List<SerieMetricaDashboardDTO> seriesMetricas = agregarSeriesMetricas(desempenhosPeriodo);

        // Agregação da evolução do RPE (US-20 C3 / RN-018)
        EvolucaoRpeDashboardDTO evolucaoRpe = agregarEvolucaoRpe(desempenhosModalidade, desempenhosPeriodo);

        // Resumo geral de sessões e adesão no período
        ResumoGeralDashboardDTO resumoGeral = calcularResumoGeral(totalSessoesHistorico, desempenhosPeriodo);

        AlunoResumoDashboardDTO alunoDTO = new AlunoResumoDashboardDTO(
                aluno.getId(),
                aluno.getUsuario() != null ? aluno.getUsuario().getNome() : null,
                aluno.getUsuario() != null ? aluno.getUsuario().getEmail() : null
        );

        ModalidadeResumoDashboardDTO modalidadeDTO = modalidadeSelecionada != null
                ? new ModalidadeResumoDashboardDTO(modalidadeSelecionada.getId(), modalidadeSelecionada.getNome())
                : null;

        PeriodoDashboardDTO periodoDTO = new PeriodoDashboardDTO(dataInicio, dataFim);

        return new DashboardEvolucaoResponseDTO(
                alunoDTO,
                modalidadeDTO,
                modalidadesDisponiveis,
                periodoDTO,
                resumoGeral,
                possuiDadosSuficientes,
                mensagem,
                seriesMetricas,
                evolucaoRpe
        );
    }

    private List<SerieMetricaDashboardDTO> agregarSeriesMetricas(List<Desempenho> desempenhosPeriodo) {
        Map<UUID, Metrica> metricasEncontradas = new LinkedHashMap<>();
        Map<UUID, List<PontoMetricaDashboardDTO>> pontosPorMetrica = new HashMap<>();

        for (Desempenho d : desempenhosPeriodo) {
            SessaoTreino sessao = d.getSessaoTreino();
            LocalDate data = obterDataReferencia(sessao);
            UUID sessaoId = sessao != null ? sessao.getId() : null;

            if (d.getRegistrosMetricas() == null) continue;

            for (RegistroMetrica rm : d.getRegistrosMetricas()) {
                Metrica metrica = rm.getMetrica();
                if (metrica == null) continue;

                metricasEncontradas.putIfAbsent(metrica.getId(), metrica);
                pontosPorMetrica.computeIfAbsent(metrica.getId(), k -> new ArrayList<>());

                String valor = rm.getValor();
                Double valorNumerico = parseValorNumerico(metrica.getTipo(), valor);
                String formatado = formatarValor(metrica.getTipo(), valor);

                pontosPorMetrica.get(metrica.getId()).add(new PontoMetricaDashboardDTO(
                        sessaoId,
                        data,
                        valor,
                        valorNumerico,
                        formatado
                ));
            }
        }

        List<SerieMetricaDashboardDTO> resultado = new ArrayList<>();
        for (Metrica metrica : metricasEncontradas.values()) {
            List<PontoMetricaDashboardDTO> pontos = pontosPorMetrica.getOrDefault(metrica.getId(), List.of());
            pontos = pontos.stream()
                    .sorted(Comparator.comparing(PontoMetricaDashboardDTO::data))
                    .toList();

            boolean descontinuada = !Boolean.TRUE.equals(metrica.getAtivo());
            // RN-017: mantida no histórico como "métrica descontinuada"
            String statusMetrica = descontinuada ? "métrica descontinuada" : "ATIVA";

            TendenciaMetricaDTO tendencia = calcularTendencia(metrica.getTipo(), pontos);

            resultado.add(new SerieMetricaDashboardDTO(
                    metrica.getId(),
                    metrica.getTitulo(),
                    metrica.getTipo(),
                    Boolean.TRUE.equals(metrica.getObrigatoria()),
                    metrica.getQuemPreenche(),
                    descontinuada,
                    statusMetrica,
                    tendencia,
                    pontos
            ));
        }

        // Ordena métricas por título
        resultado.sort(Comparator.comparing(SerieMetricaDashboardDTO::titulo));
        return resultado;
    }

    private TendenciaMetricaDTO calcularTendencia(TipoMetrica tipo, List<PontoMetricaDashboardDTO> pontos) {
        List<PontoMetricaDashboardDTO> pontosNumericos = pontos.stream()
                .filter(p -> p.valorNumerico() != null)
                .toList();

        if (pontosNumericos.size() < 2) {
            Double val = pontosNumericos.isEmpty() ? null : pontosNumericos.get(0).valorNumerico();
            return new TendenciaMetricaDTO(
                    val,
                    val,
                    0.0,
                    0.0,
                    "INSUFICIENTE",
                    "NEUTRO",
                    "Dados insuficientes para calcular tendência no período."
            );
        }

        Double vInicial = pontosNumericos.get(0).valorNumerico();
        Double vAtual = pontosNumericos.get(pontosNumericos.size() - 1).valorNumerico();
        double delta = Math.round((vAtual - vInicial) * 100.0) / 100.0;
        double percentual = vInicial != 0 ? Math.round(((vAtual - vInicial) / Math.abs(vInicial)) * 1000.0) / 10.0 : 0.0;

        String direcao;
        if (delta > 0.0001) {
            direcao = "AUMENTO";
        } else if (delta < -0.0001) {
            direcao = "REDUCAO";
        } else {
            direcao = "ESTAVEL";
        }

        // Indicador visual de melhoria ou regressão (US-15 C3)
        String indicadorVisual;
        if (tipo == TipoMetrica.TEMPO) {
            // Em métricas de tempo (ex: tempo de corrida/natação), redução de tempo é melhoria
            indicadorVisual = switch (direcao) {
                case "REDUCAO" -> "MELHORIA";
                case "AUMENTO" -> "REGRESSAO";
                default -> "NEUTRO";
            };
        } else {
            indicadorVisual = switch (direcao) {
                case "AUMENTO" -> "MELHORIA";
                case "REDUCAO" -> "REGRESSAO";
                default -> "NEUTRO";
            };
        }

        String resumo;
        if (direcao.equals("ESTAVEL")) {
            resumo = "Desempenho estável no período.";
        } else if (direcao.equals("AUMENTO")) {
            resumo = String.format(Locale.US, "Aumento de %.1f%% no período (+%.2f).", percentual, delta);
        } else {
            resumo = String.format(Locale.US, "Redução de %.1f%% no período (%.2f).", Math.abs(percentual), delta);
        }

        return new TendenciaMetricaDTO(
                vInicial,
                vAtual,
                delta,
                percentual,
                direcao,
                indicadorVisual,
                resumo
        );
    }

    private EvolucaoRpeDashboardDTO agregarEvolucaoRpe(
            List<Desempenho> historicoModalidade,
            List<Desempenho> desempenhosPeriodo
    ) {
        // Histórico total com RPE para checagem do limiar da US-20 C3 (ao menos 3 sessões com RPE)
        long totalHistoricoComRpe = historicoModalidade.stream()
                .filter(d -> d.getNivelCansaco() != null)
                .count();

        boolean possuiDadosSuficientes = totalHistoricoComRpe >= 3;
        String mensagem = possuiDadosSuficientes
                ? null
                : "São necessárias pelo menos 3 sessões com nível de cansaço (RPE) registrado para exibir a tendência de esforço.";

        List<PontoRpeDashboardDTO> pontos = new ArrayList<>();
        List<Integer> rpesNumericos = new ArrayList<>();

        for (Desempenho d : desempenhosPeriodo) {
            SessaoTreino sessao = d.getSessaoTreino();
            LocalDate data = obterDataReferencia(sessao);
            UUID sessaoId = sessao != null ? sessao.getId() : null;
            Integer nivel = d.getNivelCansaco();

            // US-20 C2: quando pulado, exibe como "não informado" no histórico
            String descricao = obterDescricaoBorg(nivel);

            pontos.add(new PontoRpeDashboardDTO(
                    sessaoId,
                    data,
                    nivel,
                    descricao
            ));

            if (nivel != null) {
                rpesNumericos.add(nivel);
            }
        }

        Double mediaRpe = null;
        Integer ultimoRpe = null;
        String direcaoTendencia = "INSUFICIENTE";

        if (!rpesNumericos.isEmpty()) {
            double media = rpesNumericos.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            mediaRpe = Math.round(media * 10.0) / 10.0;
            ultimoRpe = rpesNumericos.get(rpesNumericos.size() - 1);

            if (rpesNumericos.size() >= 2) {
                int primeiro = rpesNumericos.get(0);
                if (ultimoRpe > primeiro) {
                    direcaoTendencia = "AUMENTO";
                } else if (ultimoRpe < primeiro) {
                    direcaoTendencia = "REDUCAO";
                } else {
                    direcaoTendencia = "ESTAVEL";
                }
            }
        }

        return new EvolucaoRpeDashboardDTO(
                (int) totalHistoricoComRpe,
                possuiDadosSuficientes,
                mensagem,
                mediaRpe,
                ultimoRpe,
                direcaoTendencia,
                pontos
        );
    }

    private ResumoGeralDashboardDTO calcularResumoGeral(int totalHistorico, List<Desempenho> desempenhosPeriodo) {
        int totalPeriodo = desempenhosPeriodo.size();
        int totalConcluidas = (int) desempenhosPeriodo.stream()
                .filter(d -> d.getSessaoTreino() != null && d.getSessaoTreino().getStatus() == StatusSessaoTreino.CONCLUIDA)
                .count();

        OptionalDouble duracaoMedia = desempenhosPeriodo.stream()
                .filter(d -> d.getSessaoTreino() != null && d.getSessaoTreino().getDuracao() != null)
                .mapToInt(d -> d.getSessaoTreino().getDuracao())
                .average();

        Double mediaMinutos = duracaoMedia.isPresent()
                ? Math.round(duracaoMedia.getAsDouble() * 10.0) / 10.0
                : null;

        Double taxaAdesao = totalPeriodo > 0
                ? Math.round(((double) totalConcluidas / totalPeriodo) * 1000.0) / 10.0
                : 100.0;

        return new ResumoGeralDashboardDTO(
                totalHistorico,
                totalPeriodo,
                totalConcluidas,
                mediaMinutos,
                taxaAdesao
        );
    }

    private LocalDate obterDataReferencia(SessaoTreino sessao) {
        if (sessao == null) return LocalDate.now();
        return sessao.getDataReal() != null ? sessao.getDataReal() : sessao.getDataPlanejada();
    }

    private Double parseValorNumerico(TipoMetrica tipo, String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return switch (tipo) {
                case NUMERICO -> Double.parseDouble(valor.trim().replace(',', '.'));
                case TEMPO -> {
                    String[] partes = valor.trim().split(":");
                    if (partes.length == 2) {
                        long min = Long.parseLong(partes[0]);
                        long seg = Long.parseLong(partes[1]);
                        yield (double) (min * 60 + seg);
                    }
                    yield null;
                }
                case FREQUENCIA_CARDIACA -> Double.parseDouble(valor.trim());
                case TEXTO -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String formatarValor(TipoMetrica tipo, String valor) {
        if (valor == null) return "";
        return switch (tipo) {
            case NUMERICO -> valor.trim();
            case TEMPO -> valor.trim();
            case FREQUENCIA_CARDIACA -> valor.trim().endsWith(" bpm") ? valor.trim() : valor.trim() + " bpm";
            case TEXTO -> valor.trim();
        };
    }

    private String obterDescricaoBorg(Integer nivel) {
        if (nivel == null) return "Não informado";
        return switch (nivel) {
            case 1 -> "Borg 1 - Muito muito leve";
            case 2 -> "Borg 2 - Muito leve";
            case 3 -> "Borg 3 - Leve";
            case 4 -> "Borg 4 - Moderado";
            case 5 -> "Borg 5 - Um pouco forte";
            case 6 -> "Borg 6 - Forte";
            case 7 -> "Borg 7 - Muito forte";
            case 8 -> "Borg 8 - Muito forte +";
            case 9 -> "Borg 9 - Quase máximo";
            case 10 -> "Borg 10 - Máximo";
            default -> "Borg " + nivel;
        };
    }
}
