package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.LoginRequestDTO;
import com.gymnasion.tcc.dto.TokenResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.ContaBloqueadaException;
import com.gymnasion.tcc.infra.security.TokenService;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository userRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioPersonal;
    private Usuario usuarioAluno;
    private Aluno alunoAtivo;
    private Aluno alunoInativo;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Personal Carlos")
                .email("carlos@personal.com")
                .senhaHash("hash123")
                .role(Role.PERSONAL_TRAINER)
                .build();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Aluno Lucas")
                .email("lucas@aluno.com")
                .senhaHash("hash456")
                .role(Role.ALUNO)
                .build();

        alunoAtivo = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .status(StatusConvite.ATIVO)
                .build();

        alunoInativo = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .status(StatusConvite.INATIVO)
                .build();
    }

    // =========================================================================
    // US-02: Login com Verificação - Cenários de Sucesso
    // =========================================================================

    @Test
    @DisplayName("US02 Cenário 1: Personal Trainer realiza login com sucesso com credenciais válidas")
    void US02_cenario1_loginComSucessoPersonal() {
        LoginRequestDTO dto = new LoginRequestDTO("carlos@personal.com", "SenhaForte1!");

        when(loginAttemptService.isBlocked("carlos@personal.com")).thenReturn(false);
        when(userRepository.findByEmail("carlos@personal.com")).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaForte1!", "hash123")).thenReturn(true);
        when(tokenService.generateToken(usuarioPersonal)).thenReturn("jwt.token.personal");

        TokenResponseDTO response = authService.login(dto);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt.token.personal");
        assertThat(response.id()).isEqualTo(usuarioPersonal.getId());
        assertThat(response.role()).isEqualTo(Role.PERSONAL_TRAINER);

        verify(loginAttemptService).loginSucceeded("carlos@personal.com");
    }

    @Test
    @DisplayName("US02 Cenário 1: Aluno com status ATIVO realiza login com sucesso")
    void US02_cenario1_loginComSucessoAlunoAtivo() {
        LoginRequestDTO dto = new LoginRequestDTO("lucas@aluno.com", "SenhaAluno1!");

        when(loginAttemptService.isBlocked("lucas@aluno.com")).thenReturn(false);
        when(userRepository.findByEmail("lucas@aluno.com")).thenReturn(Optional.of(usuarioAluno));
        when(passwordEncoder.matches("SenhaAluno1!", "hash456")).thenReturn(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoAtivo));
        when(tokenService.generateToken(usuarioAluno)).thenReturn("jwt.token.aluno");

        TokenResponseDTO response = authService.login(dto);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt.token.aluno");
        assertThat(response.role()).isEqualTo(Role.ALUNO);

        verify(loginAttemptService).loginSucceeded("lucas@aluno.com");
    }

    // =========================================================================
    // US-02 Cenário 3: Credenciais Inválidas
    // =========================================================================

    @Test
    @DisplayName("US02 Cenário 3: Senha incorreta lança BadCredentialsException e registra tentativa falha")
    void US02_cenario3_credenciaisInvalidas_senhaIncorreta() {
        LoginRequestDTO dto = new LoginRequestDTO("carlos@personal.com", "SenhaErrada");

        when(loginAttemptService.isBlocked("carlos@personal.com")).thenReturn(false);
        when(userRepository.findByEmail("carlos@personal.com")).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaErrada", "hash123")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("E-mail ou senha inválidos.");

        verify(loginAttemptService).loginFailed("carlos@personal.com");
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    @DisplayName("US02 Cenário 3: E-mail inexistente lança BadCredentialsException e registra tentativa falha")
    void US02_cenario3_credenciaisInvalidas_emailInexistente() {
        LoginRequestDTO dto = new LoginRequestDTO("inexistente@email.com", "Senha123!");

        when(loginAttemptService.isBlocked("inexistente@email.com")).thenReturn(false);
        when(userRepository.findByEmail("inexistente@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("E-mail ou senha inválidos.");

        verify(loginAttemptService).loginFailed("inexistente@email.com");
        verify(tokenService, never()).generateToken(any());
    }

    // =========================================================================
    // RN-010: Bloquear login após 5 tentativas inválidas por 5 minutos
    // =========================================================================

    @Test
    @DisplayName("US02 / RN-010: Conta bloqueada após 5 tentativas inválidas impede tentativa de login")
    void US02_rn010_bloqueioApos5TentativasInvalidas() {
        LoginRequestDTO dto = new LoginRequestDTO("carlos@personal.com", "QualquerSenha");

        when(loginAttemptService.isBlocked("carlos@personal.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(ContaBloqueadaException.class)
                .hasMessage("Conta temporariamente bloqueada após 5 tentativas inválidas. Tente novamente em 5 minutos.");

        // Nem consulta o banco nem valida senha quando bloqueado
        verify(userRepository, never()).findByEmail(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenService, never()).generateToken(any());
    }

    // =========================================================================
    // RN-014: Manutenção e restrição de alunos inativos: perdem login imediatamente
    // =========================================================================

    @Test
    @DisplayName("US02 / RN-014: Aluno inativo é impedido de realizar login com mensagem 'Erro. Este aluno está inativo.'")
    void US02_rn014_alunoInativo_bloqueiaLoginImediatamente() {
        LoginRequestDTO dto = new LoginRequestDTO("lucas@aluno.com", "SenhaAluno1!");

        when(loginAttemptService.isBlocked("lucas@aluno.com")).thenReturn(false);
        when(userRepository.findByEmail("lucas@aluno.com")).thenReturn(Optional.of(usuarioAluno));
        when(passwordEncoder.matches("SenhaAluno1!", "hash456")).thenReturn(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoInativo));

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");

        verify(tokenService, never()).generateToken(any());
        verify(loginAttemptService, never()).loginSucceeded(anyString());
    }

    @Test
    @DisplayName("US02 / US-04 C2: Aluno pendente de aprovação é impedido de fazer login")
    void US02_alunoPendente_bloqueiaLoginAteAprovacao() {
        Aluno alunoPendente = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .status(StatusConvite.PENDENTE)
                .build();

        LoginRequestDTO dto = new LoginRequestDTO("lucas@aluno.com", "SenhaAluno1!");

        when(loginAttemptService.isBlocked("lucas@aluno.com")).thenReturn(false);
        when(userRepository.findByEmail("lucas@aluno.com")).thenReturn(Optional.of(usuarioAluno));
        when(passwordEncoder.matches("SenhaAluno1!", "hash456")).thenReturn(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoPendente));

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Aguardando a aprovação do personal trainer para liberar o acesso.");

        verify(tokenService, never()).generateToken(any());
    }

    @Test
    @DisplayName("US02 / US-03 C4: Aluno com cadastro recusado é impedido de fazer login")
    void US02_alunoRecusado_bloqueiaLogin() {
        Aluno alunoRecusado = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .status(StatusConvite.RECUSADO)
                .build();

        LoginRequestDTO dto = new LoginRequestDTO("lucas@aluno.com", "SenhaAluno1!");

        when(loginAttemptService.isBlocked("lucas@aluno.com")).thenReturn(false);
        when(userRepository.findByEmail("lucas@aluno.com")).thenReturn(Optional.of(usuarioAluno));
        when(passwordEncoder.matches("SenhaAluno1!", "hash456")).thenReturn(true);
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoRecusado));

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cadastro recusado pelo personal trainer.");

        verify(tokenService, never()).generateToken(any());
    }
}
