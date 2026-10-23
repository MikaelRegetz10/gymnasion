package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.*;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.dto.AlunoResponseDTO;
import com.gymnasion.tcc.dto.ConviteResponseDTO;
import com.gymnasion.tcc.dto.GerarConviteRequestDTO;
import com.gymnasion.tcc.dto.UsuarioRequestDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalConviteServiceTest {

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private PersonalConvitesRepository personalConviteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ModalidadeRepository modalidadeRepository;

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private EmailService emailService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TermoAceiteRepository termoAceiteRepository;

    @InjectMocks
    private PersonalConviteService personalConviteService;

    private Usuario personalUsuario;
    private PersonalTrainer personalTrainer;
    private Modalidade modalidade;
    private PersonalConvites conviteAtivo;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(personalConviteService, "frontendUrl", "http://localhost:5173");

        modalidade = Modalidade.builder().id(1L).nome("Natação").build();

        personalUsuario = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Amanda Souza")
                .email("amanda@gymnasion.com")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personalTrainer = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(personalUsuario)
                .modalidade(modalidade)
                .build();

        conviteAtivo = PersonalConvites.builder()
                .id(UUID.randomUUID())
                .token("valid-token")
                .personalId(personalTrainer.getId())
                .modalidade(modalidade)
                .status(StatusConvite.ATIVO)
                .quantidadesUsuarios(0)
                .maximoUsuarios(30)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15))
                .build();
    }

    @Test
    @DisplayName("US-03 C1: gerarLinkConvite com e-mail envia convite direto ao aluno")
    void gerarLinkConviteComEmailEnviaEmail() {
        when(personalTrainerRepository.getByUsuario(personalUsuario)).thenReturn(Optional.of(personalTrainer));
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));

        GerarConviteRequestDTO dto = new GerarConviteRequestDTO(1L, "aluno@gymnasion.com");

        ConviteResponseDTO response = personalConviteService.gerarLinkConvite(personalUsuario, dto);

        assertThat(response.url()).contains("/cadastro-aluno?token=");
        assertThat(response.emailEnviado()).isEqualTo("aluno@gymnasion.com");
        verify(personalConviteRepository).save(any(PersonalConvites.class));
        verify(emailService).enviarConviteAluno(eq("aluno@gymnasion.com"), eq("Amanda Souza"), eq("Natação"), anyString());
    }

    @Test
    @DisplayName("US-03 C1: gerarLinkConvite sem e-mail não despacha email")
    void gerarLinkConviteSemEmailNaoDisparaEmail() {
        when(personalTrainerRepository.getByUsuario(personalUsuario)).thenReturn(Optional.of(personalTrainer));
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));

        GerarConviteRequestDTO dto = new GerarConviteRequestDTO(1L, null);

        ConviteResponseDTO response = personalConviteService.gerarLinkConvite(personalUsuario, dto);

        assertThat(response.url()).contains("/cadastro-aluno?token=");
        assertThat(response.emailEnviado()).isNull();
        verify(personalConviteRepository).save(any(PersonalConvites.class));
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("US-04 C2 & US-17 C1: registroAlunoConvite com sucesso, senha forte e aceite dos termos")
    void registroAlunoConviteSucesso() {
        when(personalConviteRepository.findByToken("valid-token")).thenReturn(Optional.of(conviteAtivo));
        when(personalTrainerRepository.getById(personalTrainer.getId())).thenReturn(personalTrainer);
        when(usuarioRepository.findByEmail("aluno@teste.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByCpf("99988877766")).thenReturn(false);
        when(passwordEncoder.encode("AlunoForte@2026")).thenReturn("hashAluno");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> {
            Aluno a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        UsuarioRequestDTO dto = new UsuarioRequestDTO(
                "Carlos Silva",
                "aluno@teste.com",
                "AlunoForte@2026",
                "999.888.777-66",
                "11999998888",
                true
        );

        AlunoResponseDTO response = personalConviteService.registroAlunoConvite(dto, "valid-token");

        assertThat(response).isNotNull();
        assertThat(response.usuario().nome()).isEqualTo("Carlos Silva");
        verify(usuarioService).validarPoliticaSenhaForte("AlunoForte@2026");
        verify(termoAceiteRepository).save(any(TermoAceite.class));
        verify(alunoRepository).save(any(Aluno.class));
        verify(notificacaoService).notificarNovaSolicitacao(any(), any());
    }

    @Test
    @DisplayName("US-17 C2 / RN-013: registroAlunoConvite bloqueia quando termos não foram aceitos")
    void registroAlunoConviteRejeitaSemAceiteTermos() {
        UsuarioRequestDTO dto = new UsuarioRequestDTO(
                "Carlos Silva",
                "aluno@teste.com",
                "AlunoForte@2026",
                "999.888.777-66",
                "11999998888",
                false
        );

        assertThatThrownBy(() -> personalConviteService.registroAlunoConvite(dto, "valid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("É obrigatório aceitar os Termos de Uso e Política de Privacidade para criar sua conta");

        verifyNoInteractions(personalConviteRepository, usuarioRepository, alunoRepository, termoAceiteRepository);
    }

    @Test
    @DisplayName("US-01 / RN-002: registroAlunoConvite propaga validação de senha forte")
    void registroAlunoConviteRejeitaSenhaFraca() {
        doThrow(new BusinessException("A senha deve conter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial."))
                .when(usuarioService).validarPoliticaSenhaForte("fraca");

        UsuarioRequestDTO dto = new UsuarioRequestDTO(
                "Carlos Silva",
                "aluno@teste.com",
                "fraca",
                "999.888.777-66",
                "11999998888",
                true
        );

        assertThatThrownBy(() -> personalConviteService.registroAlunoConvite(dto, "valid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A senha deve conter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial.");

        verifyNoInteractions(personalConviteRepository, usuarioRepository, alunoRepository, termoAceiteRepository);
    }
}
