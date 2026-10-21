package com.gymnasion.tcc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.IntegracaoStrava;
import com.gymnasion.tcc.domain.PersonalTrainer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaServiceTest {

    @Mock
    private IntegracaoStravaRepository integracaoStravaRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    private StravaProperties stravaProperties;
    private ObjectMapper objectMapper;
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

        stravaService = new StravaService(
                integracaoStravaRepository,
                alunoRepository,
                notificacaoService,
                stravaProperties,
                objectMapper
        );

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

    @Test
    @DisplayName("US-14 C1: Retorna URL de autorização real quando configurado")
    void obterAuthUrl_quandoConfigurado_retornaUrlOAuthReal() {
        StravaAuthUrlResponseDTO response = stravaService.obterAuthUrl(usuarioAluno);

        assertThat(response.mock()).isFalse();
        assertThat(response.authUrl()).contains("https://www.strava.com/oauth/authorize");
        assertThat(response.authUrl()).contains("client_id=12345");
        assertThat(response.authUrl()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fstrava%2Fcallback");
        assertThat(response.authUrl()).contains("scope=read,activity:read_all");
    }

    @Test
    @DisplayName("US-14 C1: Retorna URL de sandbox quando mock habilitado ou credenciais ausentes")
    void obterAuthUrl_quandoMockAtivo_retornaUrlMock() {
        stravaProperties.setMockEnabled(true);

        StravaAuthUrlResponseDTO response = stravaService.obterAuthUrl(usuarioAluno);

        assertThat(response.mock()).isTrue();
        assertThat(response.authUrl()).contains("code=mock_code_" + usuarioAluno.getId());
    }

    @Test
    @DisplayName("US-14: Bloqueia acesso de usuário que não seja aluno ao Strava")
    void obterAuthUrl_quandoNaoAluno_lancaBusinessException() {
        assertThatThrownBy(() -> stravaService.obterAuthUrl(usuarioPersonal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apenas alunos podem vincular sua conta do Strava.");
    }

    @Test
    @DisplayName("US-14 C1: Conectar com mock code salva integração sandbox com sucesso")
    void conectar_comMockCode_salvaIntegracaoMock() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.empty());

        StravaStatusResponseDTO status = stravaService.conectar(usuarioAluno, "mock_code_123");

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
    @DisplayName("US-14 C1: Conectar sem código lança erro de validação")
    void conectar_comCodigoVazio_lancaBusinessException() {
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.conectar(usuarioAluno, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe o código de autorização ('code') ou os tokens de acesso do Strava.");
    }

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
        IntegracaoStrava integracao = IntegracaoStrava.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .accessToken("mock_token")
                .refreshToken("mock_refresh")
                .expiraEm(OffsetDateTime.now().plusDays(10))
                .atletaId(12345678L)
                .nomeAtleta("Atleta Real")
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        StravaStatusResponseDTO status = stravaService.obterStatus(usuarioAluno);

        assertThat(status.conectado()).isTrue();
        assertThat(status.expirado()).isFalse();
        assertThat(status.atletaId()).isEqualTo(12345678L);
        assertThat(status.nomeAtleta()).isEqualTo("Atleta Real");
    }

    @Test
    @DisplayName("US-14: Desconectar remove a integração do aluno")
    void desconectar_removeIntegracao() {
        IntegracaoStrava integracao = IntegracaoStrava.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .accessToken("mock_token")
                .refreshToken("mock_refresh")
                .expiraEm(OffsetDateTime.now().plusDays(10))
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        stravaService.desconectar(usuarioAluno);

        verify(integracaoStravaRepository).delete(integracao);
    }

    @Test
    @DisplayName("US-14 C2: Listar atividades recentes em mock retorna lista formatada de corridas/pedais")
    void listarAtividadesRecentes_quandoMock_retornaAtividadesFormatadas() {
        IntegracaoStrava integracao = IntegracaoStrava.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .accessToken("mock_token")
                .refreshToken("mock_refresh")
                .expiraEm(OffsetDateTime.now().plusDays(10))
                .build();

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
        assertThat(corrida.ritmoMedioMinKm()).isEqualTo("05:13 min/km");
    }

    @Test
    @DisplayName("US-14: Personal trainer visualiza status do Strava do seu aluno")
    void obterStatusPorPersonal_quandoAlunoPertenceAoPersonal_retornaStatus() {
        IntegracaoStrava integracao = IntegracaoStrava.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .accessToken("mock_token")
                .refreshToken("mock_refresh")
                .expiraEm(OffsetDateTime.now().plusDays(10))
                .atletaId(8888L)
                .nomeAtleta("Aluno Atleta")
                .build();

        when(alunoRepository.findById(aluno.getId())).thenReturn(Optional.of(aluno));
        when(integracaoStravaRepository.findByAluno(aluno)).thenReturn(Optional.of(integracao));

        StravaStatusResponseDTO status = stravaService.obterStatusPorPersonal(aluno.getId(), usuarioPersonal.getId());

        assertThat(status.conectado()).isTrue();
        assertThat(status.atletaId()).isEqualTo(8888L);
    }

    @Test
    @DisplayName("US-14: Personal trainer não pode acessar Strava de aluno que não é seu")
    void obterStatusPorPersonal_quandoAlunoNaoPertence_lancaBusinessException() {
        UUID outroPersonalId = UUID.randomUUID();
        when(alunoRepository.findById(aluno.getId())).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> stravaService.obterStatusPorPersonal(aluno.getId(), outroPersonalId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Acesso negado: este aluno não pertence ao seu perfil.");
    }
}
