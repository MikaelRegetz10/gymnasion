package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.IntegracaoStrava;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.StravaAtividadeDTO;
import com.gymnasion.tcc.dto.StravaAuthUrlResponseDTO;
import com.gymnasion.tcc.dto.StravaConectarRequestDTO;
import com.gymnasion.tcc.dto.StravaStatusResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.infra.config.StravaProperties;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.IntegracaoStravaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Integração com o Strava API v3 (US-14).
 *
 * Os métodos que falam com o Strava usam noRollbackFor = BusinessException: a renovação de token
 * e a notificação de reconexão (C3) precisam ser gravadas mesmo quando a operação termina em erro.
 */
@Service
@Slf4j
public class StravaService {

    private static final String STRAVA_OAUTH_AUTHORIZE_URL = "https://www.strava.com/oauth/authorize";
    private static final String STRAVA_OAUTH_TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String STRAVA_OAUTH_DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize";
    private static final String STRAVA_API_ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String SCOPES_SOLICITADOS = "read,activity:read_all";
    private static final Set<String> SCOPES_ATIVIDADES = Set.of("activity:read", "activity:read_all");
    private static final Duration VALIDADE_STATE = Duration.ofMinutes(10);
    private static final Duration ANTECEDENCIA_RENOVACAO = Duration.ofMinutes(5);
    private static final int LIMITE_PADRAO = 10;
    private static final int LIMITE_MAXIMO = 30;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private static final String MSG_ERRO_CONEXAO = "Erro ao conectar conta Strava. Entre em contato com o administrador.";
    private static final String MSG_SEM_PERMISSAO_ATIVIDADES = "O acesso às atividades do Strava não foi autorizado. Reconecte sua conta e permita o acesso às atividades.";
    private static final String MSG_TOKEN_EXPIRADO = "O token de acesso do Strava expirou. Por favor, reconecte sua conta.";
    private static final String MSG_FALHA_COMUNICACAO = "Falha ao carregar atividades do Strava. Tente novamente mais tarde.";

    private final IntegracaoStravaRepository integracaoStravaRepository;
    private final AlunoRepository alunoRepository;
    private final NotificacaoService notificacaoService;
    private final StravaProperties stravaProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public StravaService(
            IntegracaoStravaRepository integracaoStravaRepository,
            AlunoRepository alunoRepository,
            NotificacaoService notificacaoService,
            StravaProperties stravaProperties,
            ObjectMapper objectMapper) {
        this(integracaoStravaRepository, alunoRepository, notificacaoService, stravaProperties, objectMapper,
                RestClient.builder().requestFactory(criarRequestFactory()), Clock.systemDefaultZone());
    }

    StravaService(
            IntegracaoStravaRepository integracaoStravaRepository,
            AlunoRepository alunoRepository,
            NotificacaoService notificacaoService,
            StravaProperties stravaProperties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this.integracaoStravaRepository = integracaoStravaRepository;
        this.alunoRepository = alunoRepository;
        this.notificacaoService = notificacaoService;
        this.stravaProperties = stravaProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.restClient = restClientBuilder.build();
        this.clock = clock;

        if (!stravaProperties.isMockEnabled() && !stravaProperties.isConfigured()) {
            log.warn("Integração com Strava indisponível: defina STRAVA_CLIENT_ID e STRAVA_CLIENT_SECRET "
                    + "ou STRAVA_MOCK_ENABLED=true para simular em desenvolvimento.");
        }
    }

    private static SimpleClientHttpRequestFactory criarRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(6));
        factory.setReadTimeout(Duration.ofSeconds(12));
        return factory;
    }

    /**
     * Retorna a URL de autorização OAuth 2.0 (US-14 C1).
     * Com mock habilitado, retorna a URL de callback já com um code simulado.
     */
    public StravaAuthUrlResponseDTO obterAuthUrl(Usuario usuario) {
        validarUsuarioAluno(usuario);

        if (stravaProperties.isMockEnabled()) {
            String mockUrl = UriComponentsBuilder.fromUriString(stravaProperties.getRedirectUri())
                    .queryParam("code", "mock_code_" + usuario.getId())
                    .queryParam("scope", SCOPES_SOLICITADOS)
                    .build().encode().toUriString();
            return new StravaAuthUrlResponseDTO(mockUrl, true);
        }

        exigirCredenciais();

        String redirectUriEncoded = URLEncoder.encode(stravaProperties.getRedirectUri(), StandardCharsets.UTF_8);
        String authUrl = STRAVA_OAUTH_AUTHORIZE_URL
                + "?client_id=" + stravaProperties.getClientId()
                + "&response_type=code"
                + "&redirect_uri=" + redirectUriEncoded
                + "&approval_prompt=force"
                + "&scope=" + SCOPES_SOLICITADOS
                + "&state=" + gerarState(usuario.getId());

        return new StravaAuthUrlResponseDTO(authUrl, false);
    }

    /**
     * Conecta a conta Strava com o authorization code retornado no callback (US-14 C1).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public StravaStatusResponseDTO conectar(Usuario usuario, StravaConectarRequestDTO dto) {
        Aluno aluno = obterAlunoPorUsuario(usuario);

        String code = dto != null ? dto.code() : null;
        if (code == null || code.isBlank()) {
            throw new BusinessException("Informe o código de autorização retornado pelo Strava.");
        }

        if (stravaProperties.isMockEnabled()) {
            log.info("Conectando Strava em modo mock para o aluno id={}", aluno.getId());
            return salvarIntegracao(
                    aluno,
                    usuario,
                    new TokenStrava("mock_access_token_" + aluno.getId(), "mock_refresh_token_" + aluno.getId(),
                            OffsetDateTime.now(clock).plusDays(30)),
                    999000L,
                    usuario.getNome() != null ? usuario.getNome() : "Atleta Strava (Demo)",
                    SCOPES_SOLICITADOS,
                    true
            );
        }

        exigirCredenciais();

        if (!stateValido(dto.state(), usuario.getId())) {
            log.warn("Callback do Strava com state ausente, inválido ou expirado para o aluno id={}", aluno.getId());
            throw new BusinessException(MSG_ERRO_CONEXAO);
        }

        if (!possuiPermissaoAtividades(dto.scope())) {
            throw new BusinessException(MSG_SEM_PERMISSAO_ATIVIDADES);
        }

        TokenStrava token;
        Long atletaId;
        String nomeAtleta;
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", stravaProperties.getClientId());
            formData.add("client_secret", stravaProperties.getClientSecret());
            formData.add("code", code);
            formData.add("grant_type", "authorization_code");

            String responseBody = restClient.post()
                    .uri(STRAVA_OAUTH_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            token = lerToken(root);

            JsonNode athleteNode = root.path("athlete");
            atletaId = athleteNode.hasNonNull("id") ? athleteNode.path("id").asLong(0L) : null;
            String firstname = athleteNode.path("firstname").asString("");
            String lastname = athleteNode.path("lastname").asString("");
            nomeAtleta = (firstname + " " + lastname).trim();
            if (nomeAtleta.isBlank() && usuario.getNome() != null) {
                nomeAtleta = usuario.getNome();
            }
        } catch (RestClientResponseException e) {
            log.error("Erro na resposta do Strava ao trocar code por token: HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MSG_ERRO_CONEXAO);
        } catch (Exception e) {
            log.error("Falha ao comunicar com o Strava", e);
            throw new BusinessException(MSG_ERRO_CONEXAO);
        }

        if (atletaId != null && integracaoStravaRepository.existsByAtletaIdAndAlunoIdNot(atletaId, aluno.getId())) {
            log.warn("Atleta Strava id={} já vinculado a outro aluno; conexão recusada para o aluno id={}", atletaId, aluno.getId());
            throw new BusinessException("Esta conta do Strava já está vinculada a outro aluno.");
        }

        log.info("Strava conectado com sucesso para o aluno id={}, atletaId={}", aluno.getId(), atletaId);
        return salvarIntegracao(aluno, usuario, token, atletaId, nomeAtleta, dto.scope(), false);
    }

    /**
     * Consulta o status da integração Strava para o aluno logado.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public StravaStatusResponseDTO obterStatus(Usuario usuario) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        return obterStatusPorAluno(aluno);
    }

    /**
     * Consulta o status do Strava de um aluno a partir da visão do Personal Trainer.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public StravaStatusResponseDTO obterStatusPorPersonal(UUID alunoId, UUID personalUsuarioId) {
        return obterStatusPorAluno(obterAlunoDoPersonal(alunoId, personalUsuarioId));
    }

    private StravaStatusResponseDTO obterStatusPorAluno(Aluno aluno) {
        Optional<IntegracaoStrava> opt = buscarIntegracao(aluno);
        if (opt.isEmpty()) {
            return new StravaStatusResponseDTO(false, false, null, null, false);
        }

        IntegracaoStrava integracao = opt.get();
        boolean ehMock = isMockIntegracao(integracao);

        // US-14 C3: reconexão já pendente não gera nova chamada ao Strava nem nova notificação
        if (integracao.isReconexaoPendente()) {
            return new StravaStatusResponseDTO(true, true, integracao.getAtletaId(), integracao.getNomeAtleta(), ehMock);
        }

        if (!ehMock) {
            try {
                renovarTokenSeNecessario(integracao, aluno.getUsuario());
            } catch (BusinessException e) {
                log.warn("Não foi possível renovar o token Strava do aluno id={}: {}", aluno.getId(), e.getMessage());
            }
        }

        return new StravaStatusResponseDTO(
                true,
                integracao.isReconexaoPendente(),
                integracao.getAtletaId(),
                integracao.getNomeAtleta(),
                ehMock
        );
    }

    /**
     * Desconecta a conta Strava do aluno: revoga a autorização no Strava e remove a integração.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void desconectar(Usuario usuario) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        Optional<IntegracaoStrava> opt = integracaoStravaRepository.findByAluno(aluno);
        if (opt.isEmpty()) {
            return;
        }

        IntegracaoStrava integracao = opt.get();
        if (!isMockIntegracao(integracao) && stravaProperties.isConfigured()) {
            revogarAutorizacao(integracao, usuario);
        }

        integracaoStravaRepository.delete(integracao);
        notificacaoService.resolverStravaExpirado(usuario);
        log.info("Integração com Strava removida para o aluno id={}", aluno.getId());
    }

    /**
     * Lista atividades recentes do Strava para o aluno logado (US-14 C2).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public List<StravaAtividadeDTO> listarAtividadesRecentes(Usuario usuario, Integer limite) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        return listarAtividadesPorAluno(aluno, limite);
    }

    /**
     * Lista atividades recentes do aluno para o personal trainer.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public List<StravaAtividadeDTO> listarAtividadesPorPersonal(UUID alunoId, UUID personalUsuarioId, Integer limite) {
        return listarAtividadesPorAluno(obterAlunoDoPersonal(alunoId, personalUsuarioId), limite);
    }

    private List<StravaAtividadeDTO> listarAtividadesPorAluno(Aluno aluno, Integer limite) {
        IntegracaoStrava integracao = buscarIntegracao(aluno)
                .orElseThrow(() -> new BusinessException("Conta Strava não conectada. Conecte sua conta do Strava nas configurações."));

        int max = (limite == null || limite <= 0) ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);

        if (isMockIntegracao(integracao)) {
            return gerarAtividadesMock();
        }

        if (integracao.isReconexaoPendente()) {
            throw new BusinessException(MSG_TOKEN_EXPIRADO);
        }

        renovarTokenSeNecessario(integracao, aluno.getUsuario());

        try {
            String json = restClient.get()
                    .uri(STRAVA_API_ACTIVITIES_URL + "?per_page=" + max)
                    .header("Authorization", "Bearer " + integracao.getAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode arrayNode = objectMapper.readTree(json);
            List<StravaAtividadeDTO> lista = new ArrayList<>();

            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    lista.add(converterJsonParaAtividadeDTO(item));
                }
            }

            return lista;

        } catch (RestClientResponseException e) {
            log.error("Erro ao buscar atividades no Strava: HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                marcarReconexaoPendente(integracao, aluno.getUsuario());
                // 401 com "activity:read_permission" indica escopo sem acesso às atividades, não token vencido
                if (e.getResponseBodyAsString().contains("activity:read_permission")) {
                    throw new BusinessException(MSG_SEM_PERMISSAO_ATIVIDADES);
                }
                throw new BusinessException(MSG_TOKEN_EXPIRADO);
            }
            throw new BusinessException(MSG_FALHA_COMUNICACAO);
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar atividades do Strava", e);
            throw new BusinessException(MSG_FALHA_COMUNICACAO);
        }
    }

    private void renovarTokenSeNecessario(IntegracaoStrava integracao, Usuario usuario) {
        if (isMockIntegracao(integracao)) {
            return;
        }

        // Se expira em menos de 5 minutos, renova preventivamente
        if (integracao.getExpiraEm().isAfter(OffsetDateTime.now(clock).plus(ANTECEDENCIA_RENOVACAO))) {
            return;
        }

        exigirCredenciais();

        log.info("Renovando token do Strava para a integracao id={}", integracao.getId());
        TokenStrava token;
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", stravaProperties.getClientId());
            formData.add("client_secret", stravaProperties.getClientSecret());
            formData.add("grant_type", "refresh_token");
            formData.add("refresh_token", integracao.getRefreshToken());

            String responseBody = restClient.post()
                    .uri(STRAVA_OAUTH_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            token = lerToken(objectMapper.readTree(responseBody));
        } catch (RestClientResponseException e) {
            log.error("Strava recusou a renovação do token: HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            // 400/401 indicam refresh token inválido ou revogado; 429 e 5xx são transitórios
            int status = e.getStatusCode().value();
            if (status == 400 || status == 401) {
                marcarReconexaoPendente(integracao, usuario);
                throw new BusinessException(MSG_TOKEN_EXPIRADO);
            }
            throw new BusinessException(MSG_FALHA_COMUNICACAO);
        } catch (Exception e) {
            // Falha de rede ou resposta inválida: não exige reconexão, apenas tenta de novo depois
            log.error("Falha ao renovar token do Strava: {}", e.getMessage());
            throw new BusinessException(MSG_FALHA_COMUNICACAO);
        }

        integracao.setAccessToken(token.accessToken());
        integracao.setRefreshToken(token.refreshToken());
        integracao.setExpiraEm(token.expiraEm());
        integracaoStravaRepository.save(integracao);
        log.info("Token Strava renovado com sucesso para a integracao id={}", integracao.getId());
    }

    private void marcarReconexaoPendente(IntegracaoStrava integracao, Usuario usuario) {
        if (integracao.isReconexaoPendente()) {
            return;
        }
        integracao.setReconexaoPendente(true);
        integracaoStravaRepository.save(integracao);
        notificacaoService.notificarStravaExpirado(usuario);
    }

    private void revogarAutorizacao(IntegracaoStrava integracao, Usuario usuario) {
        if (integracao.isReconexaoPendente()) {
            log.warn("Token Strava já inválido para a integracao id={}; revogação no Strava não é possível", integracao.getId());
            return;
        }

        try {
            renovarTokenSeNecessario(integracao, usuario);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("access_token", integracao.getAccessToken());

            restClient.post()
                    .uri(STRAVA_OAUTH_DEAUTHORIZE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Não foi possível revogar token no Strava (prosseguindo com remoção local): {}", e.getMessage());
        }
    }

    private TokenStrava lerToken(JsonNode root) {
        String accessToken = root.path("access_token").asString("");
        String refreshToken = root.path("refresh_token").asString("");
        long expiresAtEpoch = root.path("expires_at").asLong(0L);
        if (accessToken.isBlank() || refreshToken.isBlank() || expiresAtEpoch <= 0) {
            throw new IllegalStateException("Resposta de token do Strava sem access_token, refresh_token ou expires_at");
        }
        return new TokenStrava(accessToken, refreshToken,
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), ZoneOffset.UTC));
    }

    private StravaAtividadeDTO converterJsonParaAtividadeDTO(JsonNode item) {
        String id = item.path("id").asString("");
        String nome = item.path("name").asString("Atividade Strava");
        String tipo = item.hasNonNull("sport_type")
                ? item.path("sport_type").asString("")
                : item.path("type").asString("");
        if (tipo.isBlank()) {
            tipo = null;
        }
        String esporte = traduzirEsporte(tipo);

        OffsetDateTime dataHora = lerDataHora(item);
        LocalDate data = lerDataLocal(item, dataHora);

        double distanciaMetros = item.path("distance").asDouble(0.0);
        double distanciaKm = BigDecimal.valueOf(distanciaMetros / 1000.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        String distanciaFormatada = String.format(PT_BR, "%.2f km", distanciaKm);

        int movingTime = item.path("moving_time").asInt(item.path("elapsed_time").asInt(0));
        long min = movingTime / 60;
        long sec = movingTime % 60;
        String tempoFormatado = String.format("%02d:%02d", min, sec);
        int duracaoMinutos = (int) Math.round(movingTime / 60.0);

        int elapsedTime = item.path("elapsed_time").asInt(movingTime);
        long elMin = elapsedTime / 60;
        long elSec = elapsedTime % 60;
        String tempoTotalFormatado = String.format("%02d:%02d", elMin, elSec);

        Integer hrMedia = item.hasNonNull("average_heartrate")
                ? (int) Math.round(item.path("average_heartrate").asDouble(0.0))
                : null;
        Integer hrMax = item.hasNonNull("max_heartrate")
                ? (int) Math.round(item.path("max_heartrate").asDouble(0.0))
                : null;

        String ritmoMedio = calcularRitmo(tipo, distanciaMetros, movingTime);

        Double cadenciaMedia = item.hasNonNull("average_cadence")
                ? BigDecimal.valueOf(item.path("average_cadence").asDouble()).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double potenciaMediaWatts = item.hasNonNull("average_watts")
                ? BigDecimal.valueOf(item.path("average_watts").asDouble()).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double potenciaMaximaWatts = item.hasNonNull("max_watts")
                ? BigDecimal.valueOf(item.path("max_watts").asDouble()).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double elevacaoGanhoMetros = item.hasNonNull("total_elevation_gain")
                ? BigDecimal.valueOf(item.path("total_elevation_gain").asDouble()).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double velMediaKmh = item.hasNonNull("average_speed")
                ? BigDecimal.valueOf(item.path("average_speed").asDouble() * 3.6).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double velMaximaKmh = item.hasNonNull("max_speed")
                ? BigDecimal.valueOf(item.path("max_speed").asDouble() * 3.6).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;

        Double calorias = item.hasNonNull("calories") ? item.path("calories").asDouble(0.0) : null;

        return new StravaAtividadeDTO(
                id,
                nome,
                tipo,
                esporte,
                data,
                dataHora,
                distanciaMetros,
                distanciaKm,
                distanciaFormatada,
                movingTime,
                tempoFormatado,
                duracaoMinutos,
                hrMedia,
                hrMax,
                ritmoMedio,
                tempoTotalFormatado,
                cadenciaMedia,
                potenciaMediaWatts,
                potenciaMaximaWatts,
                elevacaoGanhoMetros,
                velMediaKmh,
                velMaximaKmh,
                calorias
        );
    }

    /**
     * Data/hora de início no fuso do atleta: start_date vem em UTC e utc_offset traz o deslocamento local.
     */
    private OffsetDateTime lerDataHora(JsonNode item) {
        String inicioUtc = item.path("start_date").asString("");
        if (inicioUtc.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime dataHora = OffsetDateTime.parse(inicioUtc);
            if (item.hasNonNull("utc_offset")) {
                int offsetSegundos = (int) item.path("utc_offset").asDouble(0.0);
                return dataHora.withOffsetSameInstant(ZoneOffset.ofTotalSeconds(offsetSegundos));
            }
            return dataHora;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate lerDataLocal(JsonNode item, OffsetDateTime dataHora) {
        // start_date_local já está no horário local do atleta (o "Z" no fim não indica UTC)
        String inicioLocal = item.path("start_date_local").asString("");
        if (!inicioLocal.isBlank()) {
            try {
                return LocalDateTime.parse(inicioLocal, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
            } catch (Exception ignored) {
            }
        }
        return dataHora != null ? dataHora.toLocalDate() : null;
    }

    private String calcularRitmo(String tipo, double distanciaMetros, int movingTime) {
        if (distanciaMetros <= 10.0 || movingTime <= 0) {
            return null;
        }

        String tipoNormalizado = tipo != null ? tipo.toLowerCase() : "";
        if (tipoNormalizado.contains("swim")) {
            double segPor100m = movingTime / (distanciaMetros / 100.0);
            return String.format("%02d:%02d min/100m", (long) (segPor100m / 60), (long) (segPor100m % 60));
        }
        if (tipoNormalizado.contains("ride")) {
            double kmPorHora = (distanciaMetros / 1000.0) / (movingTime / 3600.0);
            return String.format(PT_BR, "%.1f km/h", kmPorHora);
        }

        double paceSegPorKm = movingTime / (distanciaMetros / 1000.0);
        return String.format("%02d:%02d min/km", (long) (paceSegPorKm / 60), (long) (paceSegPorKm % 60));
    }

    private List<StravaAtividadeDTO> gerarAtividadesMock() {
        LocalDate hoje = LocalDate.now(clock);

        return List.of(
                new StravaAtividadeDTO(
                        "mock-act-1",
                        "Corrida Matinal no Parque",
                        "Run",
                        "Corrida",
                        hoje.minusDays(1),
                        hoje.minusDays(1).atTime(7, 30).atOffset(ZoneOffset.ofHours(-3)),
                        5230.5,
                        5.23,
                        "5,23 km",
                        1635,
                        "27:15",
                        27,
                        154,
                        172,
                        "05:13 min/km",
                        "28:10",
                        166.0,
                        null,
                        null,
                        45.0,
                        11.5,
                        15.8,
                        385.0
                ),
                new StravaAtividadeDTO(
                        "mock-act-2",
                        "Treino de Tiros / Intervalado",
                        "Run",
                        "Corrida",
                        hoje.minusDays(3),
                        hoje.minusDays(3).atTime(18, 15).atOffset(ZoneOffset.ofHours(-3)),
                        3500.0,
                        3.50,
                        "3,50 km",
                        1020,
                        "17:00",
                        17,
                        168,
                        185,
                        "04:51 min/km",
                        "17:45",
                        174.0,
                        null,
                        null,
                        22.0,
                        12.4,
                        18.0,
                        260.0
                ),
                new StravaAtividadeDTO(
                        "mock-act-3",
                        "Pedal de Fim de Semana",
                        "Ride",
                        "Ciclismo",
                        hoje.minusDays(5),
                        hoje.minusDays(5).atTime(8, 0).atOffset(ZoneOffset.ofHours(-3)),
                        21400.0,
                        21.40,
                        "21,40 km",
                        3120,
                        "52:00",
                        52,
                        142,
                        160,
                        "24,7 km/h",
                        "55:30",
                        82.0,
                        185.0,
                        340.0,
                        210.0,
                        24.7,
                        42.5,
                        610.0
                )
        );
    }

    private String traduzirEsporte(String tipo) {
        if (tipo == null) return "Treino";
        return switch (tipo.toLowerCase()) {
            case "run", "trailrun", "virtualrun" -> "Corrida";
            case "ride", "virtualride", "ebikeride", "mountainbikeride", "emountainbikeride", "gravelride" -> "Ciclismo";
            case "swim" -> "Natação";
            case "tennis" -> "Tênis";
            case "walk", "hike" -> "Caminhada";
            case "weighttraining", "workout" -> "Musculação";
            case "rowing", "virtualrow" -> "Remo";
            default -> tipo;
        };
    }

    private boolean isMockIntegracao(IntegracaoStrava integracao) {
        return integracao.getAccessToken() != null && integracao.getAccessToken().startsWith("mock");
    }

    /**
     * Integrações criadas em modo mock não valem quando o mock está desligado: são removidas
     * para não exibir atividades simuladas como se fossem do aluno.
     */
    private Optional<IntegracaoStrava> buscarIntegracao(Aluno aluno) {
        Optional<IntegracaoStrava> opt = integracaoStravaRepository.findByAluno(aluno);
        if (opt.isPresent() && isMockIntegracao(opt.get()) && !stravaProperties.isMockEnabled()) {
            log.info("Removendo integração Strava simulada do aluno id={} (mock desabilitado)", aluno.getId());
            integracaoStravaRepository.delete(opt.get());
            return Optional.empty();
        }
        return opt;
    }

    private StravaStatusResponseDTO salvarIntegracao(
            Aluno aluno,
            Usuario usuario,
            TokenStrava token,
            Long atletaId,
            String nomeAtleta,
            String escopo,
            boolean ehMock) {

        IntegracaoStrava integracao = integracaoStravaRepository.findByAluno(aluno)
                .orElseGet(() -> IntegracaoStrava.builder().aluno(aluno).build());

        integracao.setAccessToken(token.accessToken());
        integracao.setRefreshToken(token.refreshToken());
        integracao.setExpiraEm(token.expiraEm());
        integracao.setAtletaId(atletaId);
        integracao.setNomeAtleta(nomeAtleta);
        integracao.setEscopo(escopo);
        integracao.setConectadoEm(OffsetDateTime.now(clock));
        integracao.setReconexaoPendente(false);

        integracaoStravaRepository.save(integracao);
        notificacaoService.resolverStravaExpirado(usuario);

        return new StravaStatusResponseDTO(true, false, atletaId, nomeAtleta, ehMock);
    }

    private boolean possuiPermissaoAtividades(String escopo) {
        if (escopo == null || escopo.isBlank()) {
            return false;
        }
        return Arrays.stream(escopo.split(","))
                .map(String::trim)
                .anyMatch(SCOPES_ATIVIDADES::contains);
    }

    /**
     * O state amarra o callback ao aluno que iniciou o fluxo (proteção contra CSRF no OAuth):
     * base64url("usuarioId:epochSegundos") + "." + HMAC-SHA256 assinado com o client secret.
     */
    private String gerarState(UUID usuarioId) {
        String payload = usuarioId + ":" + Instant.now(clock).getEpochSecond();
        String payloadCodificado = BASE64_URL.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadCodificado + "." + assinar(payloadCodificado);
    }

    private boolean stateValido(String state, UUID usuarioId) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String[] partes = state.split("\\.", -1);
        if (partes.length != 2) {
            return false;
        }

        byte[] assinaturaEsperada = assinar(partes[0]).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(assinaturaEsperada, partes[1].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
            String[] campos = payload.split(":", -1);
            if (campos.length != 2 || !campos[0].equals(usuarioId.toString())) {
                return false;
            }
            Instant emitidoEm = Instant.ofEpochSecond(Long.parseLong(campos[1]));
            Instant agora = Instant.now(clock);
            return !emitidoEm.isAfter(agora) && emitidoEm.plus(VALIDADE_STATE).isAfter(agora);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String assinar(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stravaProperties.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o state do OAuth do Strava", e);
        }
    }

    private void exigirCredenciais() {
        if (!stravaProperties.isConfigured()) {
            log.error("Strava sem STRAVA_CLIENT_ID/STRAVA_CLIENT_SECRET configurados e com mock desabilitado");
            throw new BusinessException(MSG_ERRO_CONEXAO);
        }
    }

    private Aluno obterAlunoDoPersonal(UUID alunoId, UUID personalUsuarioId) {
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer."));

        if (!aluno.getPersonal().getUsuario().getId().equals(personalUsuarioId)) {
            throw new NotFoundException("Aluno não encontrado ou não pertence a este Personal Trainer.");
        }
        return aluno;
    }

    private Aluno obterAlunoPorUsuario(Usuario usuario) {
        validarUsuarioAluno(usuario);
        return alunoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new NotFoundException("Perfil de aluno não encontrado para este usuário."));
    }

    private void validarUsuarioAluno(Usuario usuario) {
        if (usuario == null || usuario.getRole() != Role.ALUNO) {
            throw new BusinessException("Apenas alunos podem vincular sua conta do Strava.");
        }
    }

    private record TokenStrava(String accessToken, String refreshToken, OffsetDateTime expiraEm) {}
}
