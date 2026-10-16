package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Desempenho;
import com.gymnasion.tcc.domain.Metrica;
import com.gymnasion.tcc.domain.RegistroMetrica;
import com.gymnasion.tcc.domain.SessaoTreino;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Agregação dos dados de desempenho em séries temporais, tendências, evolução do RPE e resumo de adesão.
 * Compartilhada pelos dashboards de evolução (US-15 / US-20 C3) e pelo relatório de performance em PDF (US-16).
 * Atende às regras de negócio RN-015, RN-017 e RN-018.
 */
public final class AgregadorEvolucaoDesempenho {

    private AgregadorEvolucaoDesempenho() {
    }

    public static List<SerieMetricaDashboardDTO> agregarSeriesMetricas(List<Desempenho> desempenhosPeriodo) {
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

    private static TendenciaMetricaDTO calcularTendencia(TipoMetrica tipo, List<PontoMetricaDashboardDTO> pontos) {
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

    public static EvolucaoRpeDashboardDTO agregarEvolucaoRpe(
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

    public static ResumoGeralDashboardDTO calcularResumoGeral(int totalHistorico, List<Desempenho> desempenhosPeriodo) {
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

    public static LocalDate obterDataReferencia(SessaoTreino sessao) {
        if (sessao == null) return LocalDate.now();
        return sessao.getDataReal() != null ? sessao.getDataReal() : sessao.getDataPlanejada();
    }

    private static Double parseValorNumerico(TipoMetrica tipo, String valor) {
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

    private static String formatarValor(TipoMetrica tipo, String valor) {
        if (valor == null) return "";
        return switch (tipo) {
            case NUMERICO -> valor.trim();
            case TEMPO -> valor.trim();
            case FREQUENCIA_CARDIACA -> valor.trim().endsWith(" bpm") ? valor.trim() : valor.trim() + " bpm";
            case TEXTO -> valor.trim();
        };
    }

    public static String obterDescricaoBorg(Integer nivel) {
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
