package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.IntegracaoStrava;
import com.gymnasion.tcc.domain.PersonalTrainer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class StravaServiceTest {

    private static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    private static final String DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize";
    private static final String ATIVIDADES_URL = "https://www.strava.com/api/v3/athlete/activities";
    private static final Instant AGORA = Instant.parse("2026-10-21T15:00:00Z");

    @Mock
    private IntegracaoStravaRepository integracaoStravaRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    private StravaProperties stravaProperties;
    private ObjectMapper objectMapper;
    private MockRestServiceServer stravaApi;
    private StravaService stravaService;

    private Usuario usuarioAluno;
    private Aluno aluno;
    private Usuario usuarioPersonal;
    private PersonalTrainer personal;

    @BeforeEach
    void setUp() {
        stravaProperties = new StravaProperties();
        stravaProperties.setClientId("12345");
        stravaProperties.setClientSecret("secret-abc");
        stravaProperties.setRedirectUri("http://localhost:5173/strava/callback");
        stravaProperties.setMockEnabled(false);

        objectMapper = new ObjectMapper();
        stravaService = criarService(Clock.fixed(AGORA, ZoneOffset.UTC));

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Aluno Teste")
                .email("aluno@teste.com")
                .role(Role.ALUNO)
                .build();

        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Personal Teste")
                .email("personal@teste.com")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personal = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .build();

        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .personal(personal)
                .build();
    }

    private StravaService criarService(Clock clock) {
        RestClient.Builder builder = RestClient.builder();
        stravaApi = MockRestServiceServer.bindTo(builder).build();
        return new StravaService(integracaoStravaRepository, alunoRepository, notificacaoService,
                stravaProperties, objectMapper, builder, clock);
    }

    private IntegracaoStrava integracaoReal(OffsetDateTime expiraEm) {
        return IntegracaoStrava.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .accessToken("access-abc")
                .refreshToken("refresh-abc")
                .expiraEm(expiraEm)
                .atletaId(12345678L)
                .nomeAtleta("Atleta Real")
                .escopo("read,activity:read_all")
                .build();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(AGORA, ZoneOffset.UTC);
    }

    private String stateDe(StravaService service, Usuario usuario) {
        String authUrl = service.obterAuthUrl(usuario).authUrl();
        return authUrl.substring(authUrl.indexOf("&state=") + "&state=".length());
    }

    private String respostaToken(long atletaId) {
        return """
                {"token_type":"Bearer","access_token":"novo-access","refresh_token":"novo-refresh",
                 "expires_at":%d,"athlete":{"id":%d,"firstname":"Ana","lastname":"Souza"}}
                """.formatted(AGORA.plus(Duration.ofHours(6)).getEpochSecond(), atletaId);
    }

    // ---------------------------------------------------------------- auth-url

    @Test
    @DisplayName("US-14 C1: Retorna URL de autorização real com escopo de atividades e state")
    void obterAuthUrl_quandoConfigurado_retornaUrlOAuthReal() {
        StravaAuthUrlResponseDTO response = stravaService.obterAuthUrl(usuarioAluno);

        assertThat(response.mock()).isFalse();
        assertThat(response.authUrl()).contains("https://www.strava.com/oauth/authorize");
        assertThat(response.authUrl()).contains("client_id=12345");
        assertThat(response.authUrl()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fstrava%2Fcallback");
        assertThat(response.authUrl()).contains("scope=read,activity:read_all");
        assertThat(response.authUrl()).contains("&state=");
    }

    @Test
    @DisplayName("US-14 C1: Retorna URL de sandbox quando mock habilitado")
    void obterAuthUrl_quandoMockAtivo_retornaUrlMock() {
        stravaProperties.setMockEnabled(true);

        StravaAuthUrlResponseDTO response = stravaService.obterAuthUrl(usuarioAluno);

        assertThat(response.mock()).isTrue();
        assertThat(response.authUrl()).contains("code=mock_code_" + usuarioAluno.getId());
    }

    @Test
    @DisplayName("US-14 C4: Sem credenciais e com mock desligado, não cai em modo simulado")
    void obterAuthUrl_semCredenciaisEComMockDesligado_lancaErroDeConexao() {
        stravaProperties.setClientId("");
        stravaProperties.setClientSecret("");

        assertThatThrownBy(() -> stravaService.obterAuthUrl(usuarioAluno))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");
    }

    @Test
    @DisplayName("US-14: Bloqueia acesso de usuário que não seja aluno ao Strava")
    void obterAuthUrl_quandoNaoAluno_lancaBusinessException() {
        assertThatThrownBy(() -> stravaService.obterAuthUrl(usuarioPersonal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apenas alunos podem vincular sua conta do Strava.");
    }

    // ---------------------------------------------------------------- conectar

    @Test
    @DisplayName("US-14 C1: Com mock habilitado, conectar salva integração simulada")
    void conectar_comMockAtivo_salvaIntegracaoMock() {
        stravaProperties.setMockEnabled(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.empty());

        StravaStatusResponseDTO status = stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("mock_code_123", null, null));

        assertThat(status.conectado()).isTrue();
        assertThat(status.mock()).isTrue();
        assertThat(status.expirado()).isFalse();

        ArgumentCaptor<IntegracaoStrava> captor = ArgumentCaptor.forClass(IntegracaoStrava.class);
        verify(integracaoStravaRepository).save(captor.capture());
        IntegracaoStrava salvo = captor.getValue();
        assertThat(salvo.getAccessToken()).startsWith("mock_access_token_");
        assertThat(salvo.getAtletaId()).isEqualTo(999000L);
    }

    @Test
    @DisplayName("US-14: Code com prefixo 'mock' não cria integração simulada quando o mock está desligado")
    void conectar_comCodigoMockSemModoMock_naoCriaIntegracaoFalsa() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("mock_code_123", "read,activity:read_all", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");

        verify(integracaoStravaRepository, never()).save(any());
        stravaApi.verify();
    }

    @Test
    @DisplayName("US-14 C1: Conectar sem código lança erro de validação")
    void conectar_comCodigoVazio_lancaBusinessException() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno, new StravaConectarRequestDTO("", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe o código de autorização retornado pelo Strava.");
    }

    @Test
    @DisplayName("US-14 C1: Troca o code por token e salva a integração com escopo e data da conexão")
    void conectar_comCodeValido_trocaTokenESalvaIntegracao() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.empty());
        String state = stateDe(stravaService, usuarioAluno);

        stravaApi.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=code-real")))
                .andRespond(withSuccess(respostaToken(777L), MediaType.APPLICATION_JSON));

        StravaStatusResponseDTO status = stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read,activity:read_all", state));

        stravaApi.verify();
        assertThat(status.conectado()).isTrue();
        assertThat(status.mock()).isFalse();
        assertThat(status.atletaId()).isEqualTo(777L);
        assertThat(status.nomeAtleta()).isEqualTo("Ana Souza");

        ArgumentCaptor<IntegracaoStrava> captor = ArgumentCaptor.forClass(IntegracaoStrava.class);
        verify(integracaoStravaRepository).save(captor.capture());
        IntegracaoStrava salvo = captor.getValue();
        assertThat(salvo.getAccessToken()).isEqualTo("novo-access");
        assertThat(salvo.getRefreshToken()).isEqualTo("novo-refresh");
        assertThat(salvo.getExpiraEm()).isEqualTo(agora().plusHours(6));
        assertThat(salvo.getEscopo()).isEqualTo("read,activity:read_all");
        assertThat(salvo.getConectadoEm()).isEqualTo(agora());
        assertThat(salvo.isReconexaoPendente()).isFalse();
        verify(notificacaoService).resolverStravaExpirado(usuarioAluno);
    }

    @Test
    @DisplayName("US-14: Recusa callback com state gerado para outro usuário")
    void conectar_comStateDeOutroUsuario_lancaErroDeConexao() {
        Usuario outroAluno = Usuario.builder().id(UUID.randomUUID()).role(Role.ALUNO).build();
        String stateDoOutro = stateDe(stravaService, outroAluno);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read,activity:read_all", stateDoOutro)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");

        stravaApi.verify();
        verify(integracaoStravaRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-14: Recusa callback com state expirado")
    void conectar_comStateExpirado_lancaErroDeConexao() {
        String state = stateDe(stravaService, usuarioAluno);
        StravaService onzeMinutosDepois = criarService(Clock.fixed(AGORA.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> onzeMinutosDepois.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read,activity:read_all", state)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");
    }

    @Test
    @DisplayName("US-14: Recusa conexão quando o usuário não autoriza o acesso às atividades")
    void conectar_semPermissaoDeAtividades_lancaBusinessException() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        String state = stateDe(stravaService, usuarioAluno);

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read", state)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O acesso às atividades do Strava não foi autorizado. Reconecte sua conta e permita o acesso às atividades.");

        stravaApi.verify();
    }

    @Test
    @DisplayName("US-14 C4: Resposta de token sem access_token não grava integração")
    void conectar_comRespostaDeTokenIncompleta_lancaErroDeConexao() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        String state = stateDe(stravaService, usuarioAluno);
        stravaApi.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read,activity:read_all", state)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");

        verify(integracaoStravaRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-14 C4: Code recusado pelo Strava exibe a mensagem de erro de conexão")
    void conectar_comCodeRecusadoPeloStrava_lancaErroDeConexao() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        String state = stateDe(stravaService, usuarioAluno);
        stravaApi.expect(requestTo(TOKEN_URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-invalido", "read,activity:read_all", state)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro ao conectar conta Strava. Entre em contato com o administrador.");
    }

    @Test
    @DisplayName("US-14: Recusa conta Strava já vinculada a outro aluno")
    void conectar_comAtletaJaVinculadoAOutroAluno_recusaConexao() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.existsByAtletaIdAndAlunoIdNot(777L, aluno.getId())).thenReturn(true);
        String state = stateDe(stravaService, usuarioAluno);
        stravaApi.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(respostaToken(777L), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno,
                new StravaConectarRequestDTO("code-real", "read,activity:read_all", state)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta conta do Strava já está vinculada a outro aluno.");

        verify(integracaoStravaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- status

    @Test
    @DisplayName("US-14 C1: Retorna conectado=false quando aluno não possui integração")
    void obterStatus_quandoNaoConectado_retornaConectadoFalse() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.empty());

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.conectado()).isFalse();
        assertThat(status.expirado()).isFalse();
        assertThat(status.atletaId()).isNull();
    }

    @Test
    @DisplayName("US-14 C1: Retorna conectado=true com dados do atleta quando integrado")
    void obterStatus_quandoConectado_retornaDadosDoAtleta() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracaoReal(agora().plusDays(1))));

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.conectado()).isTrue();
        assertThat(status.expirado()).isFalse();
        assertThat(status.mock()).isFalse();
        assertThat(status.atletaId()).isEqualTo(12345678L);
        assertThat(status.nomeAtleta()).isEqualTo("Atleta Real");
    }

    @Test
    @DisplayName("US-14 C3: Com reconexão pendente, status informa expirado sem chamar o Strava nem notificar de novo")
    void obterStatus_comReconexaoPendente_retornaExpiradoSemChamarStrava() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        integracao.setReconexaoPendente(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.conectado()).isTrue();
        assertThat(status.expirado()).isTrue();
        stravaApi.verify();
        verifyNoInteractions(notificacaoService);
    }

    @Test
    @DisplayName("US-14 C3: Token vencido com refresh recusado marca reconexão pendente e notifica")
    void obterStatus_comRefreshRecusado_marcaReconexaoPendente() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withBadRequest());

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.expirado()).isTrue();
        assertThat(integracao.isReconexaoPendente()).isTrue();
        verify(notificacaoService).notificarStravaExpirado(usuarioAluno);
    }

    @Test
    @DisplayName("US-14: Integração simulada é removida quando o mock está desligado")
    void obterStatus_comIntegracaoMockEMockDesligado_removeIntegracao() {
        IntegracaoStrava integracaoMock = integracaoReal(agora().plusDays(10));
        integracaoMock.setAccessToken("mock_access_token_1");
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracaoMock));

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.conectado()).isFalse();
        verify(integracaoStravaRepository).delete(integracaoMock);
    }

    // ---------------------------------------------------------------- desconectar

    @Test
    @DisplayName("US-14: Desconectar revoga a autorização no Strava e remove a integração")
    void desconectar_revogaNoStravaERemoveIntegracao() {
        IntegracaoStrava integracao = integracaoReal(agora().plusDays(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(DEAUTHORIZE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("access_token=access-abc")))
                .andRespond(withSuccess());

        stravaService.desconectar(usuarioAluno);

        stravaApi.verify();
        verify(integracaoStravaRepository).delete(integracao);
        verify(notificacaoService).resolverStravaExpirado(usuarioAluno);
    }

    @Test
    @DisplayName("US-14: Desconectar com token vencido renova antes de revogar")
    void desconectar_comTokenVencido_renovaAntesDeRevogar() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(respostaToken(12345678L), MediaType.APPLICATION_JSON));
        stravaApi.expect(requestTo(DEAUTHORIZE_URL))
                .andExpect(content().string(containsString("access_token=novo-access")))
                .andRespond(withSuccess());

        stravaService.desconectar(usuarioAluno);

        stravaApi.verify();
        verify(integracaoStravaRepository).delete(integracao);
    }

    // ---------------------------------------------------------------- atividades

    @Test
    @DisplayName("US-14 C2: Listar atividades em mock retorna lista formatada de corridas/pedais")
    void listarAtividadesRecentes_quandoMock_retornaAtividadesFormatadas() {
        stravaProperties.setMockEnabled(true);
        IntegracaoStrava integracao = integracaoReal(agora().plusDays(10));
        integracao.setAccessToken("mock_token");

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        List<StravaAtividadeDTO> atividades = stravaService.listarAtividadesRecentes(usuarioAluno, 5);

        assertThat(atividades).hasSize(3);
        StravaAtividadeDTO corrida = atividades.get(0);
        assertThat(corrida.nome()).isEqualTo("Corrida Matinal no Parque");
        assertThat(corrida.distanciaKm()).isEqualTo(5.23);
        assertThat(corrida.tempoFormatado()).isEqualTo("27:15");
        assertThat(corrida.duracaoMinutos()).isEqualTo(27);
        assertThat(corrida.frequenciaCardiacaMedia()).isEqualTo(154);
        assertThat(corrida.ritmoMedio()).isEqualTo("05:13 min/km");
        assertThat(atividades.get(2).ritmoMedio()).isEqualTo("24,7 km/h");
    }

    @Test
    @DisplayName("US-14 C2: Converte atividades reais com data local, ritmo por modalidade e sport_type")
    void listarAtividadesRecentes_converteDadosDoStrava() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracaoReal(agora().plusDays(1))));
        String json = """
                [
                  {"id":1,"name":"Corrida noturna","type":"Run","sport_type":"Run",
                   "start_date":"2026-10-21T01:30:00Z","start_date_local":"2026-10-20T22:30:00Z","utc_offset":-10800.0,
                   "distance":5000.0,"moving_time":1500,"average_heartrate":150.4,"max_heartrate":171.6},
                  {"id":2,"name":"Piscina","type":"Swim","sport_type":"Swim",
                   "start_date":"2026-10-19T10:00:00Z","start_date_local":"2026-10-19T07:00:00Z","utc_offset":-10800.0,
                   "distance":1500.0,"moving_time":1800},
                  {"id":3,"name":"Trilha de bike","type":"Ride","sport_type":"MountainBikeRide",
                   "start_date":"2026-10-18T12:00:00Z","start_date_local":"2026-10-18T09:00:00Z","utc_offset":-10800.0,
                   "distance":30000.0,"moving_time":3600}
                ]
                """;
        stravaApi.expect(requestTo(ATIVIDADES_URL + "?per_page=10"))
                .andExpect(header("Authorization", "Bearer access-abc"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<StravaAtividadeDTO> atividades = stravaService.listarAtividadesRecentes(usuarioAluno, null);

        stravaApi.verify();
        assertThat(atividades).hasSize(3);

        StravaAtividadeDTO corrida = atividades.get(0);
        assertThat(corrida.data()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(corrida.dataHora().getOffset()).isEqualTo(ZoneOffset.ofHours(-3));
        assertThat(corrida.dataHora().getHour()).isEqualTo(22);
        assertThat(corrida.ritmoMedio()).isEqualTo("05:00 min/km");
        assertThat(corrida.distanciaFormatada()).isEqualTo("5,00 km");
        assertThat(corrida.frequenciaCardiacaMedia()).isEqualTo(150);
        assertThat(corrida.frequenciaCardiacaMaxima()).isEqualTo(172);
        assertThat(corrida.calorias()).isNull();

        StravaAtividadeDTO natacao = atividades.get(1);
        assertThat(natacao.esporte()).isEqualTo("Natação");
        assertThat(natacao.ritmoMedio()).isEqualTo("02:00 min/100m");
        assertThat(natacao.frequenciaCardiacaMedia()).isNull();

        StravaAtividadeDTO bike = atividades.get(2);
        assertThat(bike.tipo()).isEqualTo("MountainBikeRide");
        assertThat(bike.esporte()).isEqualTo("Ciclismo");
        assertThat(bike.ritmoMedio()).isEqualTo("30,0 km/h");
    }

    @Test
    @DisplayName("US-14: Limite acima do máximo é ajustado para 30")
    void listarAtividadesRecentes_comLimiteAcimaDoMaximo_usa30() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracaoReal(agora().plusDays(1))));
        stravaApi.expect(requestTo(ATIVIDADES_URL + "?per_page=30"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(stravaService.listarAtividadesRecentes(usuarioAluno, 100)).isEmpty();
        stravaApi.verify();
    }

    @Test
    @DisplayName("US-14 C3: Refresh recusado na listagem informa expiração e notifica uma única vez")
    void listarAtividadesRecentes_comRefreshRecusado_informaExpiracaoENotificaUmaVez() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> stravaService.listarAtividadesRecentes(usuarioAluno, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O token de acesso do Strava expirou. Por favor, reconecte sua conta.");
        assertThat(integracao.isReconexaoPendente()).isTrue();

        // segunda tentativa: não chama o Strava nem notifica de novo
        assertThatThrownBy(() -> stravaService.listarAtividadesRecentes(usuarioAluno, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O token de acesso do Strava expirou. Por favor, reconecte sua conta.");

        stravaApi.verify();
        verify(notificacaoService, times(1)).notificarStravaExpirado(usuarioAluno);
    }

    @Test
    @DisplayName("US-14: Falha temporária na renovação não exige reconexão")
    void listarAtividadesRecentes_comFalhaTemporariaNaRenovacao_naoMarcaReconexao() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> stravaService.listarAtividadesRecentes(usuarioAluno, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Falha ao carregar atividades do Strava. Tente novamente mais tarde.");

        assertThat(integracao.isReconexaoPendente()).isFalse();
        verifyNoInteractions(notificacaoService);
    }

    @Test
    @DisplayName("US-14: Token renovado é gravado mesmo se a listagem falhar em seguida")
    void listarAtividadesRecentes_renovaTokenEGravaMesmoComFalhaNaListagem() {
        IntegracaoStrava integracao = integracaoReal(agora().minusHours(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(respostaToken(12345678L), MediaType.APPLICATION_JSON));
        stravaApi.expect(requestTo(ATIVIDADES_URL + "?per_page=10"))
                .andExpect(header("Authorization", "Bearer novo-access"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> stravaService.listarAtividadesRecentes(usuarioAluno, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Falha ao carregar atividades do Strava. Tente novamente mais tarde.");

        stravaApi.verify();
        assertThat(integracao.getAccessToken()).isEqualTo("novo-access");
        assertThat(integracao.getRefreshToken()).isEqualTo("novo-refresh");
        verify(integracaoStravaRepository).save(integracao);
    }

    @Test
    @DisplayName("US-14: 401 por falta de escopo informa a permissão e pede reconexão")
    void listarAtividadesRecentes_com401SemPermissao_informaPermissao() {
        IntegracaoStrava integracao = integracaoReal(agora().plusDays(1));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));
        stravaApi.expect(requestTo(ATIVIDADES_URL + "?per_page=10"))
                .andRespond(withUnauthorizedRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"message":"Authorization Error","errors":[{"resource":"AccessToken","field":"activity:read_permission","code":"missing"}]}
                        """));

        assertThatThrownBy(() -> stravaService.listarAtividadesRecentes(usuarioAluno, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O acesso às atividades do Strava não foi autorizado. Reconecte sua conta e permita o acesso às atividades.");

        assertThat(integracao.isReconexaoPendente()).isTrue();
        verify(notificacaoService).notificarStravaExpirado(usuarioAluno);
    }

    @Test
    @DisplayName("US-14 C3: Erros de negócio não desfazem token renovado nem notificação")
    void metodosQueChamamOStrava_naoFazemRollbackEmBusinessException() throws NoSuchMethodException {
        List<java.lang.reflect.Method> metodos = List.of(
                StravaService.class.getMethod("conectar", Usuario.class, StravaConectarRequestDTO.class),
                StravaService.class.getMethod("obterStatus", Usuario.class),
                StravaService.class.getMethod("obterStatusPorPersonal", UUID.class, UUID.class),
                StravaService.class.getMethod("desconectar", Usuario.class),
                StravaService.class.getMethod("listarAtividadesRecentes", Usuario.class, Integer.class),
                StravaService.class.getMethod("listarAtividadesPorPersonal", UUID.class, UUID.class, Integer.class));

        for (java.lang.reflect.Method metodo : metodos) {
            assertThat(metodo.getAnnotation(Transactional.class).noRollbackFor())
                    .as(metodo.getName())
                    .contains(BusinessException.class);
        }
    }

    // ---------------------------------------------------------------- personal

    @Test
    @DisplayName("US-14: Personal trainer visualiza status do Strava do seu aluno")
    void obterStatusPorPersonal_quandoAlunoPertenceAoPersonal_retornaStatus() {
        IntegracaoStrava integracao = integracaoReal(agora().plusDays(10));
        integracao.setAtletaId(8888L);

        when(alunoRepository.findById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        StravaStatusResponseDTO status = stravaService.obterStatusPorPersonal(aluno.getId(), usuarioPersonal.getId());

        assertThat(status.conectado()).isTrue();
        assertThat(status.atletaId()).isEqualTo(8888L);
    }

    @Test
    @DisplayName("US-14: Personal trainer não pode acessar Strava de aluno que não é seu (RN-005)")
    void obterStatusPorPersonal_quandoAlunoNaoPertence_lancaNotFound() {
        UUID outroPersonalId = UUID.randomUUID();
        when(alunoRepository.findById(aluno.getId())).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.obterStatusPorPersonal(aluno.getId(), outroPersonalId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado ou não pertence a este Personal Trainer.");
    }
}
