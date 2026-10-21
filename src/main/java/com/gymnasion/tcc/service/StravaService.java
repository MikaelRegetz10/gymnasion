package com.gymnasion.tcc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.IntegracaoStrava;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.StravaAtividadeDTO;
import com.gymnasion.tcc.dto.StravaAuthUrlResponseDTO;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@Slf4j
public class StravaService {

    private static final String STRAVA_OAUTH_AUTHORIZE_URL = "https://www.strava.com/oauth/authorize";
    private static final String STRAVA_OAUTH_TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String STRAVA_OAUTH_DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize";
    private static final String STRAVA_API_ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final String DEFAULT_SCOPES = "read";

    private final IntegracaoStravaRepository integracaoStravaRepository;
    private final AlunoRepository alunoRepository;
    private final NotificacaoService notificacaoService;
    private final StravaProperties stravaProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public StravaService(
            IntegracaoStravaRepository integracaoStravaRepository,
            AlunoRepository alunoRepository,
            NotificacaoService notificacaoService,
            StravaProperties stravaProperties,
            ObjectMapper objectMapper) {
        this.integracaoStravaRepository = integracaoStravaRepository;
        this.alunoRepository = alunoRepository;
        this.notificacaoService = notificacaoService;
        this.stravaProperties = stravaProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(6));
        factory.setReadTimeout(Duration.ofSeconds(12));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Retorna a URL de autorização OAuth 2.0 (US-14 C1).
     * Se não houver credenciais configuradas ou mock ativo, retorna URL de sandbox.
     */
    public StravaAuthUrlResponseDTO obterAuthUrl(Usuario usuario) {
        validarUsuarioAluno(usuario);

        boolean usarMock = !stravaProperties.isConfigured() || stravaProperties.isMockEnabled();
        if (usarMock) {
            String mockUrl = UriComponentsBuilder.fromUriString(stravaProperties.getRedirectUri())
                    .queryParam("code", "mock_code_" + usuario.getId())
                    .queryParam("scope", DEFAULT_SCOPES)
                    .build().encode().toUriString();
            return new StravaAuthUrlResponseDTO(mockUrl, true);
        }

        String redirectUriEncoded = URLEncoder.encode(stravaProperties.getRedirectUri(), StandardCharsets.UTF_8);
        String authUrl = STRAVA_OAUTH_AUTHORIZE_URL
                + "?client_id=" + stravaProperties.getClientId()
                + "&response_type=code"
                + "&redirect_uri=" + redirectUriEncoded
                + "&approval_prompt=force"
                + "&scope=" + DEFAULT_SCOPES;

        return new StravaAuthUrlResponseDTO(authUrl, false);
    }

    public StravaStatusResponseDTO conectar(Usuario usuario, String code) {
        return conectar(usuario, new com.gymnasion.tcc.dto.StravaConectarRequestDTO(code, null, null, null));
    }

    /**
     * Conecta a conta Strava via authorization code retornado pelo Strava (US-14 C1)
     * ou diretamente via access token / refresh token.
     */
    @Transactional
    public StravaStatusResponseDTO conectar(Usuario usuario, com.gymnasion.tcc.dto.StravaConectarRequestDTO dto) {
        Aluno aluno = obterAlunoPorUsuario(usuario);

        // Conexão direta com Access Token / Refresh Token (para desenvolvedores ou painel)
        if (dto != null && dto.accessToken() != null && !dto.accessToken().isBlank()) {
            return conectarComTokensDiretos(aluno, usuario, dto.accessToken().trim(), dto.refreshToken());
        }

        String code = dto != null ? dto.code() : null;
        if (code == null || code.isBlank()) {
            throw new BusinessException("Informe o código de autorização ('code') ou os tokens de acesso do Strava.");
        }

        boolean ehMock = code.startsWith("mock") || !stravaProperties.isConfigured() || stravaProperties.isMockEnabled();
        if (ehMock) {
            log.info("Conectando Strava em modo sandbox/mock para o aluno id={}", aluno.getId());
            return salvarOuAtualizarIntegracao(
                    aluno,
                    "mock_access_token_" + aluno.getId(),
                    "mock_refresh_token_" + aluno.getId(),
                    OffsetDateTime.now().plusDays(30),
                    999000L,
                    usuario.getNome() != null ? usuario.getNome() : "Atleta Strava (Demo)",
                    true
            );
        }

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
            String accessToken = root.path("access_token").asText();
            String refreshToken = root.path("refresh_token").asText();
            long expiresAtEpoch = root.path("expires_at").asLong();
            OffsetDateTime expiraEm = OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), ZoneOffset.UTC);

            JsonNode athleteNode = root.path("athlete");
            Long atletaId = athleteNode.hasNonNull("id") ? athleteNode.path("id").asLong() : null;
            String firstname = athleteNode.path("firstname").asText("");
            String lastname = athleteNode.path("lastname").asText("");
            String nomeAtleta = (firstname + " " + lastname).trim();
            if (nomeAtleta.isBlank() && usuario.getNome() != null) {
                nomeAtleta = usuario.getNome();
            }

            log.info("Strava conectado com sucesso para o aluno id={}, atletaId={}", aluno.getId(), atletaId);
            return salvarOuAtualizarIntegracao(aluno, accessToken, refreshToken, expiraEm, atletaId, nomeAtleta, false);

        } catch (RestClientResponseException e) {
            log.error("Erro na resposta do Strava ao trocar code por token: HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException("Erro ao conectar conta Strava. Entre em contato com o administrador.");
        } catch (Exception e) {
            log.error("Falha ao comunicar com o Strava", e);
            throw new BusinessException("Erro ao conectar conta Strava. Entre em contato com o administrador.");
        }
    }

    private StravaStatusResponseDTO conectarComTokensDiretos(
            Aluno aluno,
            Usuario usuario,
            String accessToken,
            String refreshToken) {

        log.info("Conectando Strava diretamente com access token para o aluno id={}", aluno.getId());

        Long atletaId = null;
        String nomeAtleta = usuario.getNome() != null ? usuario.getNome() : "Atleta Strava";

        try {
            String json = restClient.get()
                    .uri("https://www.strava.com/api/v3/athlete")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode athlete = objectMapper.readTree(json);
            if (athlete.hasNonNull("id")) {
                atletaId = athlete.path("id").asLong();
            }
            String firstname = athlete.path("firstname").asText("");
            String lastname = athlete.path("lastname").asText("");
            String nomeCompleto = (firstname + " " + lastname).trim();
            if (!nomeCompleto.isBlank()) {
                nomeAtleta = nomeCompleto;
            }
        } catch (Exception e) {
            log.warn("Não foi possível consultar perfil do atleta no Strava (usando dados básicos): {}", e.getMessage());
        }

        String refresh = (refreshToken != null && !refreshToken.isBlank()) ? refreshToken.trim() : accessToken;
        OffsetDateTime expiraEm = OffsetDateTime.now().plusHours(6);

        return salvarOuAtualizarIntegracao(aluno, accessToken, refresh, expiraEm, atletaId, nomeAtleta, false);
    }

    /**
     * Consulta o status da integração Strava para o aluno logado.
     */
    @Transactional
    public StravaStatusResponseDTO obterStatus(Usuario usuario) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        return obterStatusPorAluno(aluno);
    }

    /**
     * Consulta o status do Strava de um aluno a partir da visão do Personal Trainer.
     */
    @Transactional
    public StravaStatusResponseDTO obterStatusPorPersonal(UUID alunoId, UUID personalUsuarioId) {
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getUsuario().getId().equals(personalUsuarioId)) {
            throw new BusinessException("Acesso negado: este aluno não pertence ao seu perfil.");
        }

        return obterStatusPorAluno(aluno);
    }

    private StravaStatusResponseDTO obterStatusPorAluno(Aluno aluno) {
        Optional<IntegracaoStrava> opt = integracaoStravaRepository.findByAluno(aluno);
        if (opt.isEmpty()) {
            return new StravaStatusResponseDTO(false, false, null, null, false);
        }

        IntegracaoStrava integracao = opt.get();
        boolean ehMock = isMockIntegracao(integracao);

        // Verifica se o token expirou (US-14 C3)
        if (integracao.getExpiraEm().isBefore(OffsetDateTime.now())) {
            if (ehMock) {
                integracao.setExpiraEm(OffsetDateTime.now().plusDays(30));
                integracaoStravaRepository.save(integracao);
            } else {
                try {
                    renovarTokenSeNecessario(integracao, aluno.getUsuario());
                } catch (Exception e) {
                    log.warn("Token Strava expirado e falha na renovação para o aluno id={}", aluno.getId());
                    return new StravaStatusResponseDTO(true, true, integracao.getAtletaId(), integracao.getNomeAtleta(), false);
                }
            }
        }

        return new StravaStatusResponseDTO(
                true,
                false,
                integracao.getAtletaId(),
                integracao.getNomeAtleta(),
                ehMock
        );
    }

    /**
     * Desconecta a conta Strava do aluno (remove a integração).
     */
    @Transactional
    public void desconectar(Usuario usuario) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        Optional<IntegracaoStrava> opt = integracaoStravaRepository.findByAluno(aluno);
        if (opt.isEmpty()) {
            return;
        }

        IntegracaoStrava integracao = opt.get();
        if (!isMockIntegracao(integracao)) {
            try {
                restClient.post()
                        .uri(STRAVA_OAUTH_DEAUTHORIZE_URL)
                        .header("Authorization", "Bearer " + integracao.getAccessToken())
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Não foi possível revogar token no Strava (prosseguindo com remoção local): {}", e.getMessage());
            }
        }

        integracaoStravaRepository.delete(integracao);
        log.info("Integração com Strava removida para o aluno id={}", aluno.getId());
    }

    /**
     * Lista atividades recentes do Strava para o aluno logado (US-14 C2).
     */
    @Transactional
    public List<StravaAtividadeDTO> listarAtividadesRecentes(Usuario usuario, Integer limite) {
        Aluno aluno = obterAlunoPorUsuario(usuario);
        return listarAtividadesPorAluno(aluno, limite);
    }

    /**
     * Lista atividades recentes do aluno para o personal trainer.
     */
    @Transactional
    public List<StravaAtividadeDTO> listarAtividadesPorPersonal(UUID alunoId, UUID personalUsuarioId, Integer limite) {
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new NotFoundException("Aluno não encontrado."));

        if (!aluno.getPersonal().getUsuario().getId().equals(personalUsuarioId)) {
            throw new BusinessException("Acesso negado: este aluno não pertence ao seu perfil.");
        }

        return listarAtividadesPorAluno(aluno, limite);
    }

    private List<StravaAtividadeDTO> listarAtividadesPorAluno(Aluno aluno, Integer limite) {
        IntegracaoStrava integracao = integracaoStravaRepository.findByAluno(aluno)
                .orElseThrow(() -> new BusinessException("Conta Strava não conectada. Conecte sua conta do Strava nas configurações."));

        int max = (limite == null || limite <= 0 || limite > 30) ? 10 : limite;

        if (isMockIntegracao(integracao)) {
            return gerarAtividadesMock();
        }

        try {
            renovarTokenSeNecessario(integracao, aluno.getUsuario());

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
            log.error("Erro ao buscar atividades no Strava: HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            if (e.getStatusCode().value() == 401) {
                notificacaoService.notificarStravaExpirado(aluno.getUsuario());
                throw new BusinessException("O token de acesso do Strava expirou. Por favor, reconecte sua conta.");
            }
            throw new BusinessException("Falha ao carregar atividades do Strava. Tente novamente mais tarde.");
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar atividades do Strava", e);
            throw new BusinessException("Falha ao carregar atividades do Strava. Tente novamente mais tarde.");
        }
    }

    private void renovarTokenSeNecessario(IntegracaoStrava integracao, Usuario usuario) {
        if (isMockIntegracao(integracao)) {
            return;
        }

        // Se expira em menos de 5 minutos, renova preventivamente
        if (integracao.getExpiraEm().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            return;
        }

        log.info("Renovando token do Strava para a integracao id={}", integracao.getId());
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

            JsonNode root = objectMapper.readTree(responseBody);
            integracao.setAccessToken(root.path("access_token").asText());
            integracao.setRefreshToken(root.path("refresh_token").asText());
            long expiresAtEpoch = root.path("expires_at").asLong();
            integracao.setExpiraEm(OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), ZoneOffset.UTC));
            integracaoStravaRepository.save(integracao);
            log.info("Token Strava renovado com sucesso!");

        } catch (Exception e) {
            log.error("Falha ao renovar token do Strava: {}", e.getMessage());
            notificacaoService.notificarStravaExpirado(usuario);
            throw new BusinessException("O token de acesso do Strava expirou. Por favor, reconecte sua conta.");
        }
    }

    private StravaAtividadeDTO converterJsonParaAtividadeDTO(JsonNode item) {
        String id = item.path("id").asText();
        String nome = item.path("name").asText("Atividade Strava");
        String tipo = item.path("type").asText(item.path("sport_type").asText("Run"));
        String esporte = traduzirEsporte(tipo);

        String startDateStr = item.path("start_date").asText("");
        OffsetDateTime dataHora = null;
        LocalDate data = LocalDate.now();
        if (!startDateStr.isBlank()) {
            try {
                dataHora = OffsetDateTime.parse(startDateStr);
                data = dataHora.toLocalDate();
            } catch (Exception ignored) {}
        }

        double distanciaMetros = item.path("distance").asDouble(0.0);
        double distanciaKm = BigDecimal.valueOf(distanciaMetros / 1000.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        String distanciaFormatada = String.format(Locale.US, "%.2f km", distanciaKm);

        int movingTime = item.path("moving_time").asInt(item.path("elapsed_time").asInt(0));
        long min = movingTime / 60;
        long sec = movingTime % 60;
        String tempoFormatado = String.format("%02d:%02d", min, sec);
        int duracaoMinutos = Math.max(1, (int) Math.round(movingTime / 60.0));

        Integer hrMedia = item.hasNonNull("average_heartrate")
                ? (int) Math.round(item.path("average_heartrate").asDouble())
                : null;
        Integer hrMax = item.hasNonNull("max_heartrate")
                ? (int) Math.round(item.path("max_heartrate").asDouble())
                : null;

        String ritmoMedio = null;
        if (distanciaMetros > 10.0 && movingTime > 0) {
            double paceSegPorKm = movingTime / (distanciaMetros / 1000.0);
            long pMin = (long) (paceSegPorKm / 60);
            long pSec = (long) (paceSegPorKm % 60);
            ritmoMedio = String.format("%02d:%02d min/km", pMin, pSec);
        }

        Double calorias = item.hasNonNull("calories") ? item.path("calories").asDouble() : null;

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
                calorias
        );
    }

    private List<StravaAtividadeDTO> gerarAtividadesMock() {
        LocalDate hoje = LocalDate.now();

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
                        "5.23 km",
                        1635,
                        "27:15",
                        27,
                        154,
                        172,
                        "05:13 min/km",
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
                        "3.50 km",
                        1020,
                        "17:00",
                        17,
                        168,
                        185,
                        "04:51 min/km",
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
                        "21.40 km",
                        3120,
                        "52:00",
                        52,
                        142,
                        160,
                        "02:26 min/km",
                        610.0
                )
        );
    }

    private String traduzirEsporte(String tipo) {
        if (tipo == null) return "Treino";
        return switch (tipo.toLowerCase()) {
            case "run", "trailrun" -> "Corrida";
            case "ride", "virtualride", "ebikeride" -> "Ciclismo";
            case "swim" -> "Natação";
            case "walk", "hike" -> "Caminhada";
            case "weighttraining", "workout" -> "Musculação";
            case "rowing" -> "Remo";
            default -> tipo;
        };
    }

    private boolean isMockIntegracao(IntegracaoStrava integracao) {
        return integracao.getAccessToken() != null && integracao.getAccessToken().startsWith("mock");
    }

    private StravaStatusResponseDTO salvarOuAtualizarIntegracao(
            Aluno aluno,
            String accessToken,
            String refreshToken,
            OffsetDateTime expiraEm,
            Long atletaId,
            String nomeAtleta,
            boolean ehMock) {

        IntegracaoStrava integracao = integracaoStravaRepository.findByAluno(aluno)
                .orElseGet(() -> IntegracaoStrava.builder().aluno(aluno).build());

        integracao.setAccessToken(accessToken);
        integracao.setRefreshToken(refreshToken);
        integracao.setExpiraEm(expiraEm);
        integracao.setAtletaId(atletaId);
        integracao.setNomeAtleta(nomeAtleta);

        integracaoStravaRepository.save(integracao);

        return new StravaStatusResponseDTO(true, false, atletaId, nomeAtleta, ehMock);
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
}
