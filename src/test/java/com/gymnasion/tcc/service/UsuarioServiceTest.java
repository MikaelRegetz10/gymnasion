package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.PreferenciaUsuario;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private ModalidadeRepository modalidadeRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private PreferenciaUsuarioRepository preferenciaUsuarioRepository;

    @Mock
    private TermoAceiteRepository termoAceiteRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuarioPersonal;
    private Usuario usuarioAluno;
    private PersonalTrainer personalTrainer;
    private Modalidade modalidade;

    @BeforeEach
    void setUp() {
        modalidade = Modalidade.builder().id(1L).nome("Musculação").build();

        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Amanda Souza")
                .email("amanda@gymnasion.com")
                .cpf("12345678901")
                .celular("11987654321")
                .senhaHash("hashAntigo")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personalTrainer = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .modalidade(modalidade)
                .build();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Carlos Silva")
                .email("carlos@gymnasion.com")
                .cpf("98765432100")
                .celular("11912345678")
                .senhaHash("hashAluno")
                .role(Role.ALUNO)
                .build();
    }

    @Test
    @DisplayName("Obter dados cadastrais do Personal Trainer autenticado com modalidade principal")
    void obterMeuPerfilPersonal() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));

        UsuarioPerfilResponseDTO response = usuarioService.obterMeuPerfil(usuarioPersonal);

        assertThat(response.id()).isEqualTo(usuarioPersonal.getId());
        assertThat(response.nome()).isEqualTo("Amanda Souza");
        assertThat(response.modalidadePrincipal()).isEqualTo("Musculação");
        assertThat(response.modalidadePrincipalId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Obter dados cadastrais do Aluno autenticado com modalidades")
    void obterMeuPerfilAluno() {
        Aluno aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .modalidades(Set.of(modalidade))
                .build();

        when(usuarioRepository.findById(usuarioAluno.getId())).thenReturn(Optional.of(usuarioAluno));
        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        UsuarioPerfilResponseDTO response = usuarioService.obterMeuPerfil(usuarioAluno);

        assertThat(response.id()).isEqualTo(usuarioAluno.getId());
        assertThat(response.nome()).isEqualTo("Carlos Silva");
        assertThat(response.modalidadesAluno()).contains("Musculação");
    }

    @Test
    @DisplayName("Atualizar dados cadastrais com sucesso limpando máscara do celular")
    void atualizarDadosCadastraisSucesso() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personalTrainer));

        AtualizarUsuarioRequestDTO dto = new AtualizarUsuarioRequestDTO("Amanda Souza Silva", "(11) 99999-8888");
        UsuarioPerfilResponseDTO response = usuarioService.atualizarDadosCadastrais(usuarioPersonal, dto);

        assertThat(response.nome()).isEqualTo("Amanda Souza Silva");
        assertThat(response.celular()).isEqualTo("11999998888");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Atualizar dados cadastrais rejeita celular com formato inválido")
    void atualizarDadosCadastraisCelularInvalido() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));

        AtualizarUsuarioRequestDTO dto = new AtualizarUsuarioRequestDTO("Amanda", "123");

        assertThatThrownBy(() -> usuarioService.atualizarDadosCadastrais(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe um número de celular válido com DDD.");
    }

    @Test
    @DisplayName("Obter preferências de notificações retorna valores padrão se ainda não configurado")
    void obterPreferenciasPadrao() {
        when(preferenciaUsuarioRepository.findByUsuarioId(usuarioPersonal.getId())).thenReturn(Optional.empty());

        PreferenciaUsuarioResponseDTO response = usuarioService.obterPreferencias(usuarioPersonal);

        assertThat(response.showNotificationBadge()).isTrue();
        assertThat(response.notificacoesEmail()).isFalse();
        assertThat(response.notificacoesPush()).isFalse();
    }

    @Test
    @DisplayName("Atualizar preferências persiste e retorna novas opções salvas")
    void atualizarPreferenciasSucesso() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(preferenciaUsuarioRepository.findByUsuarioId(usuarioPersonal.getId())).thenReturn(Optional.empty());
        when(preferenciaUsuarioRepository.save(any(PreferenciaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        PreferenciaUsuarioRequestDTO dto = new PreferenciaUsuarioRequestDTO(false, true, false);
        PreferenciaUsuarioResponseDTO response = usuarioService.atualizarPreferencias(usuarioPersonal, dto);

        assertThat(response.showNotificationBadge()).isFalse();
        assertThat(response.notificacoesEmail()).isTrue();
        assertThat(response.notificacoesPush()).isFalse();
    }

    @Test
    @DisplayName("Alterar senha com sucesso seguindo política de senha forte RN-002")
    void alterarSenhaSucesso() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaAtual123!", "hashAntigo")).thenReturn(true);
        when(passwordEncoder.encode("NovaSenha@2026")).thenReturn("novoHash");

        AlterarSenhaRequestDTO dto = new AlterarSenhaRequestDTO("SenhaAtual123!", "NovaSenha@2026", "NovaSenha@2026");
        MensagemResponseDTO response = usuarioService.alterarSenha(usuarioPersonal, dto);

        assertThat(response.mensagem()).isEqualTo("Senha alterada com sucesso!");
        assertThat(usuarioPersonal.getSenhaHash()).isEqualTo("novoHash");
        verify(usuarioRepository).save(usuarioPersonal);
    }

    @Test
    @DisplayName("Alterar senha rejeita quando senha atual informada está incorreta")
    void alterarSenhaAtualIncorreta() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaErrada123!", "hashAntigo")).thenReturn(false);

        AlterarSenhaRequestDTO dto = new AlterarSenhaRequestDTO("SenhaErrada123!", "NovaSenha@2026", "NovaSenha@2026");

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A senha atual informada está incorreta.");
    }

    @Test
    @DisplayName("Alterar senha rejeita quando confirmação não coincide")
    void alterarSenhaConfirmacaoDivergente() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaAtual123!", "hashAntigo")).thenReturn(true);

        AlterarSenhaRequestDTO dto = new AlterarSenhaRequestDTO("SenhaAtual123!", "NovaSenha@2026", "Diferente@2026");

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A confirmação da nova senha não coincide com a nova senha.");
    }

    @Test
    @DisplayName("Alterar senha rejeita quando nova senha é idêntica à senha atual")
    void alterarSenhaIdenticaAtual() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaAtual123!", "hashAntigo")).thenReturn(true);

        AlterarSenhaRequestDTO dto = new AlterarSenhaRequestDTO("SenhaAtual123!", "SenhaAtual123!", "SenhaAtual123!");

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A nova senha deve ser diferente da senha atual.");
    }

    @Test
    @DisplayName("Alterar senha rejeita senha fraca fora da política RN-002")
    void alterarSenhaFracaRN002() {
        when(usuarioRepository.findById(usuarioPersonal.getId())).thenReturn(Optional.of(usuarioPersonal));
        when(passwordEncoder.matches("SenhaAtual123!", "hashAntigo")).thenReturn(true);

        // Sem caractere especial
        AlterarSenhaRequestDTO dto = new AlterarSenhaRequestDTO("SenhaAtual123!", "SenhaSemEspecial12", "SenhaSemEspecial12");

        assertThatThrownBy(() -> usuarioService.alterarSenha(usuarioPersonal, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A senha deve conter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial.");
    }

    @Test
    @DisplayName("US-01 C1 & US-17 C1: Cadastrar personal com sucesso, senha forte e registro de aceite dos termos")
    void criarPersonalSucesso() {
        when(usuarioRepository.findByEmail("novo@gymnasion.com")).thenReturn(Optional.empty());
        when(usuarioRepository.existsByCpf("11122233344")).thenReturn(false);
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));
        when(passwordEncoder.encode("Forte@2026")).thenReturn("hashForte");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegistroPersonalCompletoDTO dto = new RegistroPersonalCompletoDTO(
                "Professor Novo",
                "novo@gymnasion.com",
                "Forte@2026",
                "111.222.333-44",
                "11988887777",
                1L,
                true
        );

        UsuarioResponseDTO response = usuarioService.criarPersonal(dto);

        assertThat(response).isNotNull();
        assertThat(response.nome()).isEqualTo("Professor Novo");
        assertThat(response.email()).isEqualTo("novo@gymnasion.com");
        verify(usuarioRepository).save(any(Usuario.class));
        verify(personalTrainerRepository).save(any(PersonalTrainer.class));
        verify(termoAceiteRepository).save(any(com.gymnasion.tcc.domain.TermoAceite.class));
    }

    @Test
    @DisplayName("US-17 C2 / RN-013: Bloquear cadastro de personal quando não aceitou termos")
    void criarPersonalRejeitaSemAceiteTermos() {
        RegistroPersonalCompletoDTO dto = new RegistroPersonalCompletoDTO(
                "Professor Novo",
                "novo@gymnasion.com",
                "Forte@2026",
                "111.222.333-44",
                "11988887777",
                1L,
                false
        );

        assertThatThrownBy(() -> usuarioService.criarPersonal(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("É obrigatório aceitar os Termos de Uso e Política de Privacidade para criar sua conta");

        verifyNoInteractions(usuarioRepository, personalTrainerRepository, termoAceiteRepository);
    }

    @Test
    @DisplayName("US-01 C1 / RN-002: Bloquear cadastro de personal com senha fraca")
    void criarPersonalRejeitaSenhaFraca() {
        RegistroPersonalCompletoDTO dto = new RegistroPersonalCompletoDTO(
                "Professor Novo",
                "novo@gymnasion.com",
                "senha123", // sem maiúscula nem caractere especial
                "111.222.333-44",
                "11988887777",
                1L,
                true
        );

        assertThatThrownBy(() -> usuarioService.criarPersonal(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A senha deve conter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial.");

        verifyNoInteractions(usuarioRepository, personalTrainerRepository, termoAceiteRepository);
    }
}
