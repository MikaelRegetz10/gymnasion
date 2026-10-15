package com.gymnasion.tcc.infra.security;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private SecurityFilter securityFilter;

    private Usuario usuarioAluno;
    private Aluno alunoAtivo;
    private Aluno alunoInativo;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Aluno Teste")
                .email("aluno@teste.com")
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("US02 / RN-014: Aluno com status INATIVO enviando token JWT válido tem requisição rejeitada com 401 e mensagem 'Erro. Este aluno está inativo.'")
    void alunoInativo_tokenValido_rejeitadoImediatamente() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.validateToken("valid.token.jwt")).thenReturn("aluno@teste.com");
        when(usuarioRepository.findByEmail("aluno@teste.com")).thenReturn(Optional.of(usuarioAluno));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoInativo));

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Erro. Este aluno está inativo.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("US02: Aluno com status ATIVO enviando token JWT válido autentica com sucesso e continua a cadeia")
    void alunoAtivo_tokenValido_autenticaComSucesso() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.validateToken("valid.token.jwt")).thenReturn("aluno@teste.com");
        when(usuarioRepository.findByEmail("aluno@teste.com")).thenReturn(Optional.of(usuarioAluno));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(alunoAtivo));

        securityFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(usuarioAluno);
        verify(filterChain).doFilter(request, response);
    }
}
