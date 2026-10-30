package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.enums.ConteudoRelatorio;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.EvolucaoRpeDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.PontoMetricaDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.PontoRpeDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.ResumoGeralDashboardDTO;
import com.gymnasion.tcc.dto.DashboardEvolucaoResponseDTO.SerieMetricaDashboardDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO.SecaoModalidadeRelatorioDTO;
import com.gymnasion.tcc.dto.RelatorioPerformanceDTO.SessaoRelatorioDTO;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Geração do documento PDF do relatório de performance com dados tabulados e gráficos (US-16 C1).
 * Segue a identidade visual do front-end: tokens de Gymnasion_web/src/styles/tokens.css, logo do componente Logo
 * e cards de evolução do componente StudentEvolution.
 */
@Component
public class RelatorioPdfGenerator {

    // Tokens de cor de Gymnasion_web/src/styles/tokens.css (manter sincronizado com o front-end)
    private static final Color PRIMARY_50 = new Color(0xFFF4EF);
    private static final Color PRIMARY_100 = new Color(0xFFE4D9);
    private static final Color PRIMARY_500 = new Color(0xF05A28);
    private static final Color PRIMARY_600 = new Color(0xD9461A);
    private static final Color INK_400 = new Color(0x94A3B8);
    private static final Color INK_500 = new Color(0x64748B);
    private static final Color INK_700 = new Color(0x334155);
    private static final Color INK_900 = new Color(0x0F172A);
    private static final Color BORDER = new Color(0xE7EAF0);
    private static final Color BACKGROUND = new Color(0xF6F7FB);
    private static final Color SUCCESS_50 = new Color(0xECFDF3);
    private static final Color SUCCESS_700 = new Color(0x067647);
    private static final Color DANGER = new Color(0xF04438);
    private static final Color DANGER_50 = new Color(0xFEF3F2);
    private static final Color DANGER_700 = new Color(0xB42318);
    private static final Color INFO = new Color(0x2E90FA);
    private static final Color SPORT_ACADEMIA = new Color(0x7A5AF8);

    // Degradê do símbolo (Gymnasion_web/public/design-system/imgMark.svg)
    private static final Color LOGO_INICIO = new Color(0xFF8A3D);
    private static final Color LOGO_FIM = new Color(0xE5391B);
    private static final String CAMINHO_SIMBOLO = "M26.7 13.3C25.1415 11.7496 23.0936 10.7877 20.9052 10.5783"
            + "C18.7169 10.3688 16.5237 10.9248 14.6994 12.1513C12.8751 13.3779 11.5326 15.1992 10.9009 17.3048"
            + "C10.2691 19.4104 10.3871 21.6699 11.2347 23.6982C12.0824 25.7265 13.6072 27.3981 15.5493 28.428"
            + "C17.4915 29.4579 19.7307 29.7824 21.8853 29.3461C24.0399 28.9099 25.9765 27.7399 27.365 26.0356"
            + "C28.7536 24.3314 29.5081 22.1983 29.5 20H26L24.4 17.2L22 22.8L20.4 20H18.5";

    // Cores das séries na tela de evolução (StudentEvolution.tsx): azul, laranja e vermelho em rodízio; RPE em roxo
    private static final Color[] CORES_SERIES = {INFO, PRIMARY_500, DANGER};

    // --color-sport-* e --color-sport-*-50; modalidades sem cor definida usam o estilo neutro
    private static final Map<String, Color[]> CORES_MODALIDADES = Map.of(
            "natacao", new Color[]{new Color(0x2E90FA), new Color(0xEFF8FF)},
            "tenis", new Color[]{new Color(0x65A30D), new Color(0xF3FCE8)},
            "academia", new Color[]{new Color(0x7A5AF8), new Color(0xF4F3FF)},
            "corrida", new Color[]{new Color(0xE11D48), new Color(0xFFF1F3)},
            "ciclismo", new Color[]{new Color(0x0E9384), new Color(0xF0FDF9)},
            "funcional", new Color[]{new Color(0xDC6803), new Color(0xFEF6EE)}
    );

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private static final float MARGEM = 36;
    // --radius-card (16px) convertido para pontos
    private static final float RAIO_CARD = 12;
    private static final float ESPACO_ENTRE_CARDS = 5;
    private static final float PADDING_CARD = 12;
    // Proporção do gráfico da tela de evolução (viewBox 500 x 165), com mais altura para a impressão
    private static final float PROPORCAO_GRAFICO = 0.42f;
    // Acima deste número de pontos os marcadores poluem o gráfico; exibe apenas a linha
    private static final int LIMITE_PONTOS_COM_MARCADOR = 60;
    // Máximo de métricas por tabela de valores, para manter as colunas legíveis na largura da página
    private static final int METRICAS_POR_TABELA = 5;

    private final BaseFont fonteRegular;
    private final BaseFont fonteSemiBold;
    private final BaseFont fonteBold;
    private final BaseFont fonteExtraBold;

    public RelatorioPdfGenerator() {
        this.fonteRegular = carregarFonte("PlusJakartaSans-Regular.ttf");
        this.fonteSemiBold = carregarFonte("PlusJakartaSans-SemiBold.ttf");
        this.fonteBold = carregarFonte("PlusJakartaSans-Bold.ttf");
        this.fonteExtraBold = carregarFonte("PlusJakartaSans-ExtraBold.ttf");
    }

    public byte[] gerar(RelatorioPerformanceDTO relatorio) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, MARGEM, MARGEM, MARGEM, MARGEM + 16);
        PdfWriter writer = PdfWriter.getInstance(documento, saida);
        // Mantém os gráficos na ordem em que foram adicionados, sem antecipar o texto seguinte
        writer.setStrictImageSequence(true);
        writer.setPageEvent(new RodapePagina());

        documento.open();
        adicionarCabecalho(documento, writer, relatorio);
        for (SecaoModalidadeRelatorioDTO secao : relatorio.secoes()) {
            adicionarSecaoModalidade(documento, writer, secao, relatorio.conteudos());
        }
        documento.close();
        return saida.toByteArray();
    }

    private void adicionarCabecalho(Document documento, PdfWriter writer, RelatorioPerformanceDTO relatorio) {
        PdfPTable topo = new PdfPTable(new float[]{1, 1});
        topo.setWidthPercentage(100);

        Phrase logo = new Phrase();
        logo.add(new Chunk(criarSimbolo(writer, 22), 0, -6, true));
        logo.add(new Chunk("  "));
        Chunk marca = new Chunk("gymnasion", fonte(fonteExtraBold, 16, INK_900));
        marca.setCharacterSpacing(-0.48f);
        logo.add(marca);
        PdfPCell celulaLogo = celulaSemBorda(logo);
        celulaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        topo.addCell(celulaLogo);

        Phrase gerado = new Phrase("Gerado em " + relatorio.dataGeracao().format(FORMATO_DATA)
                + " às " + relatorio.dataGeracao().format(FORMATO_HORA), fonte(fonteRegular, 8, INK_500));
        PdfPCell celulaGerado = celulaSemBorda(gerado);
        celulaGerado.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celulaGerado.setVerticalAlignment(Element.ALIGN_MIDDLE);
        topo.addCell(celulaGerado);
        topo.setSpacingAfter(14);
        documento.add(topo);

        // Faixa de identificação com --gradient-brand-soft (primary-100 para primary-50)
        PdfPCell faixa = new PdfPCell();
        faixa.setBorder(Rectangle.NO_BORDER);
        faixa.setPadding(18);
        faixa.setCellEvent(new FundoArredondado(PRIMARY_100, PRIMARY_50, null, 0, RAIO_CARD, PdfPTable.BASECANVAS));

        faixa.addElement(new Paragraph("RELATÓRIO DE PERFORMANCE", fonte(fonteBold, 8, PRIMARY_600)));
        Chunk nome = new Chunk(valorOuTraco(relatorio.nomeAluno()), fonte(fonteExtraBold, 20, INK_900));
        nome.setCharacterSpacing(-0.4f);
        Paragraph paragrafoNome = new Paragraph(nome);
        paragrafoNome.setSpacingBefore(4);
        faixa.addElement(paragrafoNome);

        Paragraph detalhes = new Paragraph();
        detalhes.setSpacingBefore(6);
        detalhes.add(new Chunk("Personal trainer  ", fonte(fonteSemiBold, 8.5f, INK_500)));
        detalhes.add(new Chunk(valorOuTraco(relatorio.nomePersonal()), fonte(fonteRegular, 9, INK_700)));
        detalhes.add(new Chunk("      Período  ", fonte(fonteSemiBold, 8.5f, INK_500)));
        detalhes.add(new Chunk(relatorio.dataInicio().format(FORMATO_DATA) + " – " + relatorio.dataFim().format(FORMATO_DATA),
                fonte(fonteRegular, 9, INK_700)));
        if (relatorio.totalDatasSelecionadas() != null) {
            // Datas escolhidas uma a uma: o período é só o intervalo entre a primeira e a última
            int total = relatorio.totalDatasSelecionadas();
            detalhes.add(new Chunk("      Datas  ", fonte(fonteSemiBold, 8.5f, INK_500)));
            detalhes.add(new Chunk(total + (total == 1 ? " selecionada" : " selecionadas"), fonte(fonteRegular, 9, INK_700)));
        }
        faixa.addElement(detalhes);

        PdfPTable tabelaFaixa = new PdfPTable(1);
        tabelaFaixa.setWidthPercentage(100);
        tabelaFaixa.addCell(faixa);
        documento.add(tabelaFaixa);
    }

    private void adicionarSecaoModalidade(Document documento, PdfWriter writer, SecaoModalidadeRelatorioDTO secao, Set<ConteudoRelatorio> conteudos) {
        garantirEspaco(documento, writer, 140);
        Color[] cores = CORES_MODALIDADES.getOrDefault(chaveModalidade(secao.modalidade()), new Color[]{INK_700, BACKGROUND});
        PdfPTable etiqueta = criarSelo(secao.modalidade(), cores[1], cores[0], Element.ALIGN_LEFT);
        etiqueta.setSpacingBefore(20);
        documento.add(etiqueta);

        if (conteudos.contains(ConteudoRelatorio.RESUMO)) {
            adicionarResumo(documento, secao.resumoGeral());
        }
        adicionarEvolucao(documento, writer, secao, conteudos);
        if (conteudos.contains(ConteudoRelatorio.SESSOES)) {
            adicionarSessoes(documento, writer, secao.sessoes(), conteudos.contains(ConteudoRelatorio.RPE));
        }
        if (conteudos.contains(ConteudoRelatorio.VALORES)) {
            adicionarValoresRegistrados(documento, writer, secao);
        }
    }

    private void adicionarResumo(Document documento, ResumoGeralDashboardDTO resumo) {
        documento.add(criarTituloSecao("Resumo do período", null));

        PdfPTable indicadores = new PdfPTable(4);
        indicadores.setWidthPercentage(100);
        indicadores.addCell(criarIndicador("Sessões registradas", String.valueOf(resumo.totalSessoesPeriodo())));
        indicadores.addCell(criarIndicador("Sessões concluídas", String.valueOf(resumo.totalSessoesConcluidasPeriodo())));
        indicadores.addCell(criarIndicador("Taxa de adesão",
                resumo.taxaAdesaoPercentual() != null ? formatarNumero(resumo.taxaAdesaoPercentual()) + "%" : "—"));
        indicadores.addCell(criarIndicador("Duração média",
                resumo.duracaoMediaMinutos() != null ? formatarNumero(resumo.duracaoMediaMinutos()) + " min" : "—"));
        documento.add(indicadores);
    }

    private PdfPCell criarIndicador(String rotulo, String valor) {
        PdfPCell celula = criarCelulaCard();
        celula.addElement(new Paragraph(rotulo, fonte(fonteSemiBold, 8, INK_500)));
        Chunk valorChunk = new Chunk(valor, fonte(fonteExtraBold, 16, INK_900));
        valorChunk.setCharacterSpacing(-0.32f);
        Paragraph paragrafoValor = new Paragraph(valorChunk);
        paragrafoValor.setSpacingBefore(4);
        celula.addElement(paragrafoValor);
        return celula;
    }

    private void adicionarEvolucao(Document documento, PdfWriter writer, SecaoModalidadeRelatorioDTO secao, Set<ConteudoRelatorio> conteudos) {
        float larguraConteudo = documento.right() - documento.left();
        float larguraGrafico = larguraConteudo / 2 - 2 * (ESPACO_ENTRE_CARDS + PADDING_CARD);

        List<PdfPCell> cards = new ArrayList<>();
        int indiceCor = 0;
        if (conteudos.contains(ConteudoRelatorio.GRAFICOS)) {
            for (SerieMetricaDashboardDTO metrica : secao.metricas()) {
                // Métricas de texto não possuem gráfico, assim como na tela de evolução
                if (metrica.tipo() == TipoMetrica.TEXTO) continue;
                cards.add(criarCardMetrica(writer, metrica, CORES_SERIES[indiceCor++ % CORES_SERIES.length], larguraGrafico));
            }
        }
        if (conteudos.contains(ConteudoRelatorio.RPE)) {
            cards.add(criarCardRpe(writer, secao.evolucaoRpe(), larguraGrafico));
        }
        if (cards.isEmpty()) return;

        garantirEspaco(documento, writer, 200);
        documento.add(criarTituloSecao("Evolução", "Variação entre o primeiro e o último registro do período"));

        PdfPTable grade = new PdfPTable(2);
        grade.setWidthPercentage(100);
        cards.forEach(grade::addCell);
        if (cards.size() % 2 != 0) {
            grade.addCell(celulaSemBorda(new Phrase("")));
        }
        documento.add(grade);
    }

    private PdfPCell criarCardMetrica(PdfWriter writer, SerieMetricaDashboardDTO metrica, Color cor, float larguraGrafico) {
        List<PontoMetricaDashboardDTO> pontos = metrica.pontos().stream()
                .filter(p -> p.valorNumerico() != null)
                .toList();
        List<LocalDate> datas = pontos.stream().map(PontoMetricaDashboardDTO::data).toList();
        List<Double> valores = pontos.stream().map(PontoMetricaDashboardDTO::valorNumerico).toList();

        Double variacao = metrica.tendencia() != null && !"INSUFICIENTE".equals(metrica.tendencia().direcao())
                ? metrica.tendencia().percentualVariacao()
                : null;

        PdfPCell card = criarCelulaCard();
        card.addElement(criarCabecalhoCard(metrica.titulo(), rotuloTipo(metrica.tipo()), variacao,
                metrica.tendencia() != null ? metrica.tendencia().indicadorVisual() : null, metrica.descontinuada()));
        adicionarConteudoGrafico(writer, card, datas, valores, metrica.tipo(), unidadeMedia(metrica.tipo()), cor, larguraGrafico);
        return card;
    }

    private PdfPCell criarCardRpe(PdfWriter writer, EvolucaoRpeDashboardDTO rpe, float larguraGrafico) {
        List<PontoRpeDashboardDTO> pontos = rpe.pontos().stream()
                .filter(p -> p.nivelCansaco() != null)
                .toList();
        List<LocalDate> datas = pontos.stream().map(PontoRpeDashboardDTO::data).toList();
        List<Double> valores = pontos.stream().map(p -> p.nivelCansaco().doubleValue()).toList();

        PdfPCell card = criarCelulaCard();
        card.addElement(criarCabecalhoCard("Nível de esforço (RPE)", "Escala de Borg CR-10", calcularVariacao(valores), null, false));
        adicionarConteudoGrafico(writer, card, datas, valores, null, "média", SPORT_ACADEMIA, larguraGrafico);
        return card;
    }

    private PdfPTable criarCabecalhoCard(String titulo, String subtitulo, Double variacao, String indicadorVisual, boolean descontinuada) {
        PdfPTable cabecalho = new PdfPTable(new float[]{2.2f, 1});
        cabecalho.setWidthPercentage(100);

        PdfPCell esquerda = celulaSemBorda(null);
        esquerda.addElement(new Paragraph(titulo, fonte(fonteBold, 10.5f, INK_900)));
        esquerda.addElement(new Paragraph(subtitulo, fonte(fonteRegular, 8.5f, INK_500)));
        if (descontinuada) {
            // RN-017: métrica excluída com histórico continua no relatório, identificada como descontinuada
            PdfPTable selo = criarSelo("métrica descontinuada", BACKGROUND, INK_700, Element.ALIGN_LEFT);
            selo.setSpacingBefore(4);
            esquerda.addElement(selo);
        }
        cabecalho.addCell(esquerda);

        PdfPCell direita = celulaSemBorda(null);
        if (variacao != null) {
            String texto = (variacao > 0 ? "+" : "") + formatarNumero(variacao) + "%";
            direita.addElement(criarSelo(texto, BACKGROUND, INK_500, Element.ALIGN_RIGHT));
        }
        PdfPTable seloTendencia = criarSeloTendencia(indicadorVisual);
        if (seloTendencia != null) {
            seloTendencia.setSpacingBefore(4);
            direita.addElement(seloTendencia);
        }
        cabecalho.addCell(direita);
        return cabecalho;
    }

    // Indicação de melhoria ou regressão (US-15 C3) com as cores de feedback do Badge
    private PdfPTable criarSeloTendencia(String indicadorVisual) {
        if (indicadorVisual == null) return null;
        return switch (indicadorVisual) {
            case "MELHORIA" -> criarSelo("Melhoria", SUCCESS_50, SUCCESS_700, Element.ALIGN_RIGHT);
            case "REGRESSAO" -> criarSelo("Regressão", DANGER_50, DANGER_700, Element.ALIGN_RIGHT);
            default -> null;
        };
    }

    private void adicionarConteudoGrafico(PdfWriter writer, PdfPCell card, List<LocalDate> datas, List<Double> valores,
                                          TipoMetrica tipo, String unidade, Color cor, float larguraGrafico) {
        if (valores.isEmpty()) {
            Paragraph vazio = new Paragraph("Sem registros para esta métrica no período.", fonte(fonteRegular, 8.5f, INK_500));
            vazio.setAlignment(Element.ALIGN_CENTER);
            vazio.setSpacingBefore(40);
            vazio.setSpacingAfter(40);
            card.addElement(vazio);
            return;
        }

        double media = valores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        Paragraph valor = new Paragraph();
        valor.setSpacingBefore(8);
        Chunk valorChunk = new Chunk(formatarValor(media, tipo), fonte(fonteExtraBold, 17, INK_900));
        valorChunk.setCharacterSpacing(-0.34f);
        valor.add(valorChunk);
        valor.add(new Chunk("  " + unidade, fonte(fonteRegular, 8.5f, INK_500)));
        card.addElement(valor);

        Image grafico = criarGrafico(writer, datas, valores, cor, larguraGrafico);
        grafico.setSpacingBefore(8);
        card.addElement(grafico);
    }

    // Reproduz o gráfico do EvolutionCard: grade tracejada, área em degradê, linha e marcadores, datas inicial e final
    private Image criarGrafico(PdfWriter writer, List<LocalDate> datas, List<Double> valores, Color cor, float largura) {
        float altura = largura * PROPORCAO_GRAFICO;
        PdfTemplate t = writer.getDirectContent().createTemplate(largura, altura);

        float esquerda = 4, direita = largura - 4;
        float base = 14, topo = altura - 4;
        float inicioPlot = base + 4;

        t.saveState();
        t.setColorStroke(BORDER);
        t.setLineWidth(0.75f);
        t.setLineDash(2, 3.5f, 0);
        for (int i = 0; i < 4; i++) {
            float y = inicioPlot + (topo - inicioPlot) * i / 3;
            t.moveTo(esquerda, y);
            t.lineTo(direita, y);
        }
        t.stroke();
        t.restoreState();

        double menor = Collections.min(valores), maior = Collections.max(valores);
        long inicio = datas.get(0).toEpochDay(), fim = datas.get(datas.size() - 1).toEpochDay();
        float[] xs = new float[valores.size()];
        float[] ys = new float[valores.size()];
        for (int i = 0; i < valores.size(); i++) {
            xs[i] = fim == inicio
                    ? (esquerda + direita) / 2
                    : esquerda + 4 + (float) (datas.get(i).toEpochDay() - inicio) / (fim - inicio) * (direita - esquerda - 8);
            ys[i] = maior == menor
                    ? (inicioPlot + topo) / 2
                    : inicioPlot + 2 + (float) ((valores.get(i) - menor) / (maior - menor)) * (topo - inicioPlot - 4);
        }

        if (valores.size() > 1) {
            // Área sob a linha: a cor da série com 16% de opacidade sobre o fundo branco, até transparente
            t.saveState();
            t.moveTo(xs[0], base);
            for (int i = 0; i < xs.length; i++) {
                t.lineTo(xs[i], ys[i]);
            }
            t.lineTo(xs[xs.length - 1], base);
            t.closePath();
            t.clip();
            t.newPath();
            t.paintShading(PdfShading.simpleAxial(writer, 0, topo, 0, base, misturarComBranco(cor, 0.16f), Color.WHITE));
            t.restoreState();

            t.saveState();
            t.setColorStroke(cor);
            t.setLineWidth(1.8f);
            t.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
            t.setLineCap(PdfContentByte.LINE_CAP_ROUND);
            t.moveTo(xs[0], ys[0]);
            for (int i = 1; i < xs.length; i++) {
                t.lineTo(xs[i], ys[i]);
            }
            t.stroke();
            t.restoreState();
        }

        if (valores.size() <= LIMITE_PONTOS_COM_MARCADOR) {
            t.saveState();
            t.setColorStroke(cor);
            t.setColorFill(Color.WHITE);
            t.setLineWidth(1.4f);
            for (int i = 0; i < xs.length; i++) {
                t.circle(xs[i], ys[i], 2.6f);
                t.fillStroke();
            }
            t.restoreState();
        }

        escreverTexto(t, fonteRegular, 7, INK_400, datas.get(0).format(FORMATO_DATA), esquerda, 2, Element.ALIGN_LEFT);
        escreverTexto(t, fonteRegular, 7, INK_400, datas.get(datas.size() - 1).format(FORMATO_DATA), direita, 2, Element.ALIGN_RIGHT);

        Image imagem = Image.getInstance(t);
        imagem.setAlignment(Element.ALIGN_CENTER);
        return imagem;
    }

    private void adicionarSessoes(Document documento, PdfWriter writer, List<SessaoRelatorioDTO> sessoes, boolean incluirRpe) {
        garantirEspaco(documento, writer, 120);
        documento.add(criarTituloSecao("Sessões", "Data planejada e data real de execução registradas separadamente"));

        PdfPTable tabela = incluirRpe
                ? criarTabela(new float[]{1.2f, 1.2f, 1, 1.2f, 1}, "Data planejada", "Data real", "Duração", "Status", "RPE")
                : criarTabela(new float[]{1.2f, 1.2f, 1, 1.2f}, "Data planejada", "Data real", "Duração", "Status");
        for (SessaoRelatorioDTO sessao : sessoes) {
            adicionarCelula(tabela, formatarData(sessao.dataPlanejada()), INK_700);
            adicionarCelula(tabela, formatarData(sessao.dataReal()), INK_700);
            adicionarCelula(tabela, sessao.duracao() != null ? sessao.duracao() + " min" : "—", INK_700);
            boolean concluida = "Concluída".equals(sessao.status());
            adicionarCelulaSelo(tabela, sessao.status(), concluida ? SUCCESS_50 : BACKGROUND, concluida ? SUCCESS_700 : INK_700);
            if (incluirRpe) {
                // US-20 C2: RPE pulado aparece como "não informado"
                boolean rpeInformado = !"não informado".equals(sessao.rpe());
                adicionarCelula(tabela, sessao.rpe(), rpeInformado ? INK_700 : INK_500);
            }
        }
        documento.add(tabela);
    }

    private void adicionarValoresRegistrados(Document documento, PdfWriter writer, SecaoModalidadeRelatorioDTO secao) {
        if (secao.metricas().isEmpty()) return;
        garantirEspaco(documento, writer, 120);
        documento.add(criarTituloSecao("Valores registrados", "Resultados de cada métrica por sessão executada"));

        List<SerieMetricaDashboardDTO> metricas = secao.metricas();
        // Distribui as métricas de forma equilibrada entre as tabelas, sem deixar uma métrica sozinha
        int quantidadeTabelas = (metricas.size() + METRICAS_POR_TABELA - 1) / METRICAS_POR_TABELA;
        int inicio = 0;
        for (int tabelaAtual = 0; tabelaAtual < quantidadeTabelas; tabelaAtual++) {
            int tamanhoGrupo = (metricas.size() - inicio + (quantidadeTabelas - tabelaAtual) - 1) / (quantidadeTabelas - tabelaAtual);
            List<SerieMetricaDashboardDTO> grupo = metricas.subList(inicio, inicio + tamanhoGrupo);
            inicio += tamanhoGrupo;

            float[] larguras = new float[grupo.size() + 1];
            String[] colunas = new String[grupo.size() + 1];
            larguras[0] = 1.1f;
            colunas[0] = "Data real";
            List<Map<UUID, String>> valoresPorSessao = new ArrayList<>();
            for (int i = 0; i < grupo.size(); i++) {
                SerieMetricaDashboardDTO metrica = grupo.get(i);
                larguras[i + 1] = metrica.tipo() == TipoMetrica.TEXTO ? 2.2f : 1.2f;
                colunas[i + 1] = metrica.descontinuada()
                        ? metrica.titulo() + " (" + metrica.statusMetrica() + ")"
                        : metrica.titulo();
                Map<UUID, String> valores = new HashMap<>();
                for (PontoMetricaDashboardDTO ponto : metrica.pontos()) {
                    valores.put(ponto.sessaoId(), formatarPonto(ponto, metrica.tipo()));
                }
                valoresPorSessao.add(valores);
            }

            PdfPTable tabela = criarTabela(larguras, colunas);
            for (SessaoRelatorioDTO sessao : secao.sessoes()) {
                adicionarCelula(tabela, formatarData(sessao.dataReal() != null ? sessao.dataReal() : sessao.dataPlanejada()), INK_700);
                for (Map<UUID, String> valores : valoresPorSessao) {
                    String valor = valores.get(sessao.sessaoId());
                    adicionarCelula(tabela, valor != null ? valor : "—", valor != null ? INK_700 : INK_400);
                }
            }
            tabela.setSpacingAfter(10);
            documento.add(tabela);
        }
    }

    private Paragraph criarTituloSecao(String titulo, String subtitulo) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.setSpacingBefore(16);
        paragrafo.setSpacingAfter(8);
        Chunk tituloChunk = new Chunk(titulo, fonte(fonteExtraBold, 13, INK_900));
        tituloChunk.setCharacterSpacing(-0.26f);
        paragrafo.add(tituloChunk);
        if (subtitulo != null) {
            paragrafo.add(Chunk.NEWLINE);
            paragrafo.add(new Chunk(subtitulo, fonte(fonteRegular, 8.5f, INK_500)));
        }
        return paragrafo;
    }

    private PdfPTable criarTabela(float[] larguras, String... colunas) {
        PdfPTable tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100);
        // Repete o cabeçalho nas quebras de página
        tabela.setHeaderRows(1);
        for (String coluna : colunas) {
            PdfPCell celula = new PdfPCell(new Phrase(coluna, fonte(fonteSemiBold, 7.5f, INK_500)));
            celula.setBackgroundColor(BACKGROUND);
            celula.setBorder(Rectangle.BOTTOM);
            celula.setBorderColor(BORDER);
            celula.setBorderWidth(0.75f);
            celula.setPaddingTop(6);
            celula.setPaddingBottom(7);
            celula.setPaddingLeft(8);
            celula.setPaddingRight(6);
            tabela.addCell(celula);
        }
        return tabela;
    }

    private void adicionarCelula(PdfPTable tabela, String texto, Color cor) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte(fonteRegular, 8, cor)));
        configurarCelulaLinha(celula);
        tabela.addCell(celula);
    }

    private void adicionarCelulaSelo(PdfPTable tabela, String texto, Color fundo, Color cor) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte(fonteSemiBold, 7.5f, cor)));
        configurarCelulaLinha(celula);
        celula.setPaddingLeft(14);
        celula.setCellEvent(new SeloNaCelula(fundo, fonteSemiBold.getWidthPoint(texto, 7.5f), 7.5f));
        tabela.addCell(celula);
    }

    private void configurarCelulaLinha(PdfPCell celula) {
        celula.setBorder(Rectangle.BOTTOM);
        celula.setBorderColor(BORDER);
        celula.setBorderWidth(0.5f);
        celula.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celula.setUseAscender(true);
        celula.setUseDescender(true);
        celula.setPaddingTop(4);
        celula.setPaddingBottom(5);
        celula.setPaddingLeft(8);
        celula.setPaddingRight(6);
    }

    private PdfPCell criarCelulaCard() {
        PdfPCell celula = new PdfPCell();
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(ESPACO_ENTRE_CARDS + PADDING_CARD);
        celula.setCellEvent(new FundoArredondado(Color.WHITE, null, BORDER, ESPACO_ENTRE_CARDS, RAIO_CARD, PdfPTable.BASECANVAS));
        return celula;
    }

    // Selo em formato de pílula, como os componentes Badge e SportTag do front-end
    private PdfPTable criarSelo(String texto, Color fundo, Color cor, int alinhamento) {
        float tamanho = 7.5f;
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte(fonteSemiBold, tamanho, cor)));
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPaddingTop(2.5f);
        celula.setPaddingBottom(4);
        celula.setPaddingLeft(7);
        celula.setPaddingRight(7);
        celula.setUseAscender(true);
        celula.setCellEvent(new FundoArredondado(fundo, null, null, 0, 8, PdfPTable.BACKGROUNDCANVAS));

        PdfPTable selo = new PdfPTable(1);
        selo.setTotalWidth(fonteSemiBold.getWidthPoint(texto, tamanho) + 15);
        selo.setLockedWidth(true);
        selo.setHorizontalAlignment(alinhamento);
        selo.addCell(celula);
        return selo;
    }

    private Image criarSimbolo(PdfWriter writer, float tamanho) {
        PdfTemplate t = writer.getDirectContent().createTemplate(40, 40);
        t.saveState();
        t.roundRectangle(0, 0, 40, 40, 12);
        t.clip();
        t.newPath();
        t.paintShading(PdfShading.simpleAxial(writer, 0, 40, 40, 0, LOGO_INICIO, LOGO_FIM));
        t.restoreState();

        t.setColorStroke(Color.WHITE);
        t.setLineWidth(3.2f);
        t.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        t.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        desenharCaminhoSvg(t, CAMINHO_SIMBOLO, 40);
        t.stroke();

        Image simbolo = Image.getInstance(t);
        simbolo.scaleAbsolute(tamanho, tamanho);
        return simbolo;
    }

    // Interpreta os comandos absolutos M, L, H e C de um caminho SVG (eixo Y invertido em relação ao PDF)
    private void desenharCaminhoSvg(PdfContentByte cb, String caminho, float altura) {
        Matcher comandos = Pattern.compile("([MLHC])([^MLHC]*)").matcher(caminho);
        float y = 0;
        while (comandos.find()) {
            String[] partes = comandos.group(2).trim().split("[\\s,]+");
            float[] v = new float[partes.length];
            for (int i = 0; i < partes.length; i++) {
                v[i] = Float.parseFloat(partes[i]);
            }
            switch (comandos.group(1)) {
                case "M" -> { y = v[1]; cb.moveTo(v[0], altura - y); }
                case "L" -> { y = v[1]; cb.lineTo(v[0], altura - y); }
                case "H" -> cb.lineTo(v[0], altura - y);
                case "C" -> {
                    for (int i = 0; i + 5 < v.length; i += 6) {
                        cb.curveTo(v[i], altura - v[i + 1], v[i + 2], altura - v[i + 3], v[i + 4], altura - v[i + 5]);
                        y = v[i + 5];
                    }
                }
                default -> throw new IllegalArgumentException("Comando SVG não suportado: " + comandos.group(1));
            }
        }
    }

    private void garantirEspaco(Document documento, PdfWriter writer, float alturaMinima) {
        if (writer.getVerticalPosition(false) - documento.bottom() < alturaMinima) {
            documento.newPage();
        }
    }

    private void escreverTexto(PdfContentByte cb, BaseFont fonte, float tamanho, Color cor, String texto, float x, float y, int alinhamento) {
        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(fonte, tamanho);
        cb.setColorFill(cor);
        cb.showTextAligned(alinhamento, texto, x, y, 0);
        cb.endText();
        cb.restoreState();
    }

    private Font fonte(BaseFont base, float tamanho, Color cor) {
        return new Font(base, tamanho, Font.NORMAL, cor);
    }

    private PdfPCell celulaSemBorda(Phrase conteudo) {
        PdfPCell celula = conteudo != null ? new PdfPCell(conteudo) : new PdfPCell();
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(0);
        return celula;
    }

    private Double calcularVariacao(List<Double> valores) {
        if (valores.size() < 2 || valores.get(0) == 0) return null;
        double primeiro = valores.get(0), ultimo = valores.get(valores.size() - 1);
        return Math.round((ultimo - primeiro) / Math.abs(primeiro) * 1000.0) / 10.0;
    }

    private String rotuloTipo(TipoMetrica tipo) {
        return switch (tipo) {
            case NUMERICO -> "Numérico";
            case TEMPO -> "Tempo";
            case FREQUENCIA_CARDIACA -> "Freq. cardíaca";
            case TEXTO -> "Texto";
        };
    }

    private String unidadeMedia(TipoMetrica tipo) {
        return switch (tipo) {
            case TEMPO -> "mm:ss · média";
            case FREQUENCIA_CARDIACA -> "bpm · média";
            default -> "média";
        };
    }

    private String formatarPonto(PontoMetricaDashboardDTO ponto, TipoMetrica tipo) {
        if (tipo != TipoMetrica.TEXTO && ponto.valorNumerico() != null) {
            return formatarValor(ponto.valorNumerico(), tipo);
        }
        return valorOuTraco(ponto.valor() != null ? ponto.valor().trim() : null);
    }

    // Mesma formatação do formatMetric do front-end: tempo em m:ss e números em pt-BR com até 1 casa decimal
    private String formatarValor(double valor, TipoMetrica tipo) {
        if (tipo == TipoMetrica.TEMPO) {
            long total = Math.round(valor);
            return (total / 60) + ":" + String.format("%02d", total % 60);
        }
        NumberFormat formato = NumberFormat.getNumberInstance(LOCALE_PT_BR);
        formato.setMaximumFractionDigits(1);
        return formato.format(valor);
    }

    private String formatarNumero(Double valor) {
        return formatarValor(valor, null);
    }

    private String formatarData(LocalDate data) {
        return data != null ? data.format(FORMATO_DATA) : "—";
    }

    private String valorOuTraco(String valor) {
        return valor != null && !valor.isBlank() ? valor : "—";
    }

    private String chaveModalidade(String modalidade) {
        if (modalidade == null) return "";
        return Normalizer.normalize(modalidade, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static Color misturarComBranco(Color cor, float opacidade) {
        return new Color(
                Math.round(255 - (255 - cor.getRed()) * opacidade),
                Math.round(255 - (255 - cor.getGreen()) * opacidade),
                Math.round(255 - (255 - cor.getBlue()) * opacidade)
        );
    }

    private static BaseFont carregarFonte(String arquivo) {
        try (InputStream entrada = RelatorioPdfGenerator.class.getResourceAsStream("/fonts/plus-jakarta-sans/" + arquivo)) {
            if (entrada == null) {
                throw new IllegalStateException("Fonte não encontrada nos recursos: " + arquivo);
            }
            return BaseFont.createFont(arquivo, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, entrada.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar a fonte do relatório: " + arquivo, e);
        }
    }

    private class RodapePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            PdfContentByte cb = writer.getDirectContent();
            float y = documento.bottom() - 22;
            cb.saveState();
            cb.setColorStroke(BORDER);
            cb.setLineWidth(0.5f);
            cb.moveTo(documento.left(), y + 12);
            cb.lineTo(documento.right(), y + 12);
            cb.stroke();
            cb.restoreState();
            escreverTexto(cb, fonteSemiBold, 7.5f, INK_400, "gymnasion · Relatório de performance", documento.left(), y, Element.ALIGN_LEFT);
            escreverTexto(cb, fonteRegular, 7.5f, INK_400, "Página " + writer.getPageNumber(), documento.right(), y, Element.ALIGN_RIGHT);
        }
    }

    // Cards usam a camada base para que os selos internos (tabelas aninhadas) fiquem visíveis sobre o fundo
    private record FundoArredondado(Color fundo, Color fundoFinal, Color borda, float recuo, float raio, int camada) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell celula, Rectangle posicao, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[camada];
            float x = posicao.getLeft() + recuo, y = posicao.getBottom() + recuo;
            float largura = posicao.getWidth() - 2 * recuo, altura = posicao.getHeight() - 2 * recuo;
            float raioAjustado = Math.min(raio, altura / 2);
            cb.saveState();
            if (fundoFinal != null) {
                cb.roundRectangle(x, y, largura, altura, raioAjustado);
                cb.clip();
                cb.newPath();
                cb.paintShading(PdfShading.simpleAxial(cb.getPdfWriter(), x, y, x + largura, y, fundo, fundoFinal));
            } else {
                cb.setColorFill(fundo);
                cb.roundRectangle(x, y, largura, altura, raioAjustado);
                if (borda != null) {
                    cb.setColorStroke(borda);
                    cb.setLineWidth(0.75f);
                    cb.fillStroke();
                } else {
                    cb.fill();
                }
            }
            cb.restoreState();
        }
    }

    // Pílula desenhada atrás do texto da célula, sem criar uma tabela por linha (tabelas com muitas sessões)
    private record SeloNaCelula(Color fundo, float larguraTexto, float tamanhoFonte) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell celula, Rectangle posicao, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float altura = tamanhoFonte + 6;
            float x = posicao.getLeft() + celula.getPaddingLeft() - 6;
            float centro = (posicao.getTop() - celula.getPaddingTop() + posicao.getBottom() + celula.getPaddingBottom()) / 2;
            cb.saveState();
            cb.setColorFill(fundo);
            cb.roundRectangle(x, centro - altura / 2, larguraTexto + 12, altura, altura / 2);
            cb.fill();
            cb.restoreState();
        }
    }
}
