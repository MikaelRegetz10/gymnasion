package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.ObservacaoFeedback;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.Role;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.domain.enums.TipoRegistroPerfil;
import com.gymnasion.tcc.dto.CriarObservacaoFeedbackRequestDTO;
import com.gymnasion.tcc.dto.ObservacaoFeedbackResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.AlunoRepository;
import com.gymnasion.tcc.repository.ObservacaoFeedbackRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class ObservacaoFeedbackServiceTest {

    @Mock
    private ObservacaoFeedbackRepository observacaoFeedbackRepository;

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @InjectMocks
    private ObservacaoFeedbackService observacaoFeedbackService;

    private Usuario usuarioPersonal;
    private PersonalTrainer personal;

    private Usuario usuarioAluno;
    private Aluno aluno;

    private Usuario usuarioOutroPersonal;
    private PersonalTrainer outroPersonal;

    @BeforeEach
    void setUp() {
        usuarioPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Treinador Carlos")
                .email("carlos@personal.com")
                .role(Role.PERSONAL_TRAINER)
                .build();

        personal = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioPersonal)
                .build();

        usuarioAluno = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Aluno Lucas")
                .email("lucas@aluno.com")
                .role(Role.ALUNO)
                .build();

        aluno = Aluno.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioAluno)
                .personal(personal)
                .status(StatusConvite.ATIVO)
                .build();

        usuarioOutroPersonal = Usuario.builder()
                .id(UUID.randomUUID())
                .nome("Treinador Roberto")
                .email("roberto@personal.com")
                .role(Role.PERSONAL_TRAINER)
                .build();

        outroPersonal = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioOutroPersonal)
                .build();
    }

    // =========================================================================
    // US-26 Cenário 2: Registrar feedback como personal
    // =========================================================================

    @Test
    @DisplayName("US26 Cenário 2: Personal responsável registra feedback no perfil do aluno com data e tipo FEEDBACK")
    void US26_cenario2_registrarFeedbackComoPersonal() {
        CriarObservacaoFeedbackRequestDTO dto = new CriarObservacaoFeedbackRequestDTO(
                "Excelente evolução nos treinos de ritmo e velocidade desta semana."
        );

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personal.getId())).thenReturn(Optional.of(aluno));
        when(observacaoFeedbackRepository.save(any(ObservacaoFeedback.class)))
                .thenAnswer(invocation -> {
                    ObservacaoFeedback entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        ObservacaoFeedbackResponseDTO response = observacaoFeedbackService.registrarFeedbackPeloPersonal(
                usuarioPersonal, aluno.getId(), dto
        );

        assertThat(response).isNotNull();
        assertThat(response.conteudo()).isEqualTo("Excelente evolução nos treinos de ritmo e velocidade desta semana.");
        assertThat(response.tipo()).isEqualTo(TipoRegistroPerfil.FEEDBACK);
        assertThat(response.papelAutor()).isEqualTo(Role.PERSONAL_TRAINER);
        assertThat(response.nomeAutor()).isEqualTo("Treinador Carlos");
        assertThat(response.alunoId()).isEqualTo(aluno.getId());
        assertThat(response.dataCriacao()).isNotNull();

        ArgumentCaptor<ObservacaoFeedback> captor = ArgumentCaptor.forClass(ObservacaoFeedback.class);
        verify(observacaoFeedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoRegistroPerfil.FEEDBACK);
        assertThat(captor.getValue().getAutor()).isEqualTo(usuarioPersonal);
        assertThat(captor.getValue().getAluno()).isEqualTo(aluno);
    }

    // =========================================================================
    // US-26 Cenário 3: Registrar observação como aluno
    // =========================================================================

    @Test
    @DisplayName("US26 Cenário 3: Aluno registra observação sobre sua experiência com data e tipo OBSERVACAO")
    void US26_cenario3_registrarObservacaoComoAluno() {
        CriarObservacaoFeedbackRequestDTO dto = new CriarObservacaoFeedbackRequestDTO(
                "Senti um pouco de dor no ombro direito na última série de peito."
        );

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(observacaoFeedbackRepository.save(any(ObservacaoFeedback.class)))
                .thenAnswer(invocation -> {
                    ObservacaoFeedback entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        ObservacaoFeedbackResponseDTO response = observacaoFeedbackService.registrarObservacaoPeloAluno(
                usuarioAluno, dto
        );

        assertThat(response).isNotNull();
        assertThat(response.conteudo()).isEqualTo("Senti um pouco de dor no ombro direito na última série de peito.");
        assertThat(response.tipo()).isEqualTo(TipoRegistroPerfil.OBSERVACAO);
        assertThat(response.papelAutor()).isEqualTo(Role.ALUNO);
        assertThat(response.nomeAutor()).isEqualTo("Aluno Lucas");
        assertThat(response.alunoId()).isEqualTo(aluno.getId());
        assertThat(response.dataCriacao()).isNotNull();

        ArgumentCaptor<ObservacaoFeedback> captor = ArgumentCaptor.forClass(ObservacaoFeedback.class);
        verify(observacaoFeedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoRegistroPerfil.OBSERVACAO);
        assertThat(captor.getValue().getAutor()).isEqualTo(usuarioAluno);
        assertThat(captor.getValue().getAluno()).isEqualTo(aluno);
    }

    // =========================================================================
    // US-26 Cenários 1 e 4: Consultar histórico com distinção de tipo e autor
    // =========================================================================

    @Test
    @DisplayName("US26 Cenários 1 e 4: Personal consulta histórico distinguindo feedbacks de observações do aluno")
    void US26_cenario1_e_4_personalConsultaHistorico() {
        ObservacaoFeedback f1 = ObservacaoFeedback.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .autor(usuarioPersonal)
                .tipo(TipoRegistroPerfil.FEEDBACK)
                .conteudo("Parabéns pelo empenho!")
                .dataCriacao(OffsetDateTime.now().minusDays(1))
                .build();

        ObservacaoFeedback o1 = ObservacaoFeedback.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .autor(usuarioAluno)
                .tipo(TipoRegistroPerfil.OBSERVACAO)
                .conteudo("Treino concluído com boa recuperação.")
                .dataCriacao(OffsetDateTime.now().minusDays(2))
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personal.getId())).thenReturn(Optional.of(aluno));
        when(observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(aluno.getId()))
                .thenReturn(List.of(f1, o1));

        List<ObservacaoFeedbackResponseDTO> resultado = observacaoFeedbackService.listarObservacoesEFeedbacksPeloPersonal(
                usuarioPersonal, aluno.getId()
        );

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).tipo()).isEqualTo(TipoRegistroPerfil.FEEDBACK);
        assertThat(resultado.get(0).nomeAutor()).isEqualTo("Treinador Carlos");
        assertThat(resultado.get(1).tipo()).isEqualTo(TipoRegistroPerfil.OBSERVACAO);
        assertThat(resultado.get(1).nomeAutor()).isEqualTo("Aluno Lucas");
    }

    @Test
    @DisplayName("US26 Cenários 1 e 4: Aluno consulta o histórico de registros do seu próprio perfil")
    void US26_cenario1_e_4_alunoConsultaHistoricoProprio() {
        ObservacaoFeedback f1 = ObservacaoFeedback.builder()
                .id(UUID.randomUUID())
                .aluno(aluno)
                .autor(usuarioPersonal)
                .tipo(TipoRegistroPerfil.FEEDBACK)
                .conteudo("Mantenha o foco!")
                .dataCriacao(OffsetDateTime.now().minusDays(1))
                .build();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));
        when(observacaoFeedbackRepository.findByAlunoIdOrderByDataCriacaoDesc(aluno.getId()))
                .thenReturn(List.of(f1));

        List<ObservacaoFeedbackResponseDTO> resultado = observacaoFeedbackService.listarObservacoesEFeedbacksPeloAluno(
                usuarioAluno
        );

        assertThat(resultado).singleElement().satisfies(item -> {
            assertThat(item.tipo()).isEqualTo(TipoRegistroPerfil.FEEDBACK);
            assertThat(item.conteudo()).isEqualTo("Mantenha o foco!");
            assertThat(item.nomeAutor()).isEqualTo("Treinador Carlos");
        });
    }

    // =========================================================================
    // US-26 Cenário 6: Conteúdo obrigatório
    // =========================================================================

    @Test
    @DisplayName("US26 Cenário 6: Tentativa de registrar feedback com conteúdo vazio/em branco é impedida")
    void US26_cenario6_validarConteudoObrigatorio_personal() {
        CriarObservacaoFeedbackRequestDTO dtoVazio = new CriarObservacaoFeedbackRequestDTO("   ");

        when(personalTrainerRepository.getByUsuario(usuarioPersonal)).thenReturn(Optional.of(personal));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), personal.getId())).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> observacaoFeedbackService.registrarFeedbackPeloPersonal(usuarioPersonal, aluno.getId(), dtoVazio))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O conteúdo é obrigatório.");

        verify(observacaoFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("US26 Cenário 6: Tentativa do aluno registrar observação com conteúdo nulo é impedida")
    void US26_cenario6_validarConteudoObrigatorio_aluno() {
        CriarObservacaoFeedbackRequestDTO dtoNulo = new CriarObservacaoFeedbackRequestDTO(null);

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> observacaoFeedbackService.registrarObservacaoPeloAluno(usuarioAluno, dtoNulo))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O conteúdo é obrigatório.");

        verify(observacaoFeedbackRepository, never()).save(any());
    }

    // =========================================================================
    // RN-005 / RN-006: Isolamento de dados entre treinadores e alunos
    // =========================================================================

    @Test
    @DisplayName("US26 / RN-005 / RN-006: Personal não responsável não pode registrar feedback em aluno de outro personal")
    void US26_rn005_rn006_personalNaoResponsavelNaoRegistra() {
        CriarObservacaoFeedbackRequestDTO dto = new CriarObservacaoFeedbackRequestDTO("Bom treino");

        when(personalTrainerRepository.getByUsuario(usuarioOutroPersonal)).thenReturn(Optional.of(outroPersonal));
        when(alunoRepository.findByIdAndPersonalId(aluno.getId(), outroPersonal.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> observacaoFeedbackService.registrarFeedbackPeloPersonal(usuarioOutroPersonal, aluno.getId(), dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Aluno não encontrado ou não pertence a este Personal Trainer.");

        verify(observacaoFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("US26 / RN-006: Aluno não pode registrar observação no perfil de outro aluno")
    void US26_rn006_alunoNaoRegistraEmOutroAluno() {
        UUID outroAlunoId = UUID.randomUUID();
        CriarObservacaoFeedbackRequestDTO dto = new CriarObservacaoFeedbackRequestDTO("Minha observação");

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> observacaoFeedbackService.registrar(usuarioAluno, outroAlunoId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Você não tem permissão para registrar observação no perfil de outro aluno.");

        verify(observacaoFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("US26 / RN-006: Aluno não pode listar observações do perfil de outro aluno")
    void US26_rn006_alunoNaoListaOutroAluno() {
        UUID outroAlunoId = UUID.randomUUID();

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> observacaoFeedbackService.listarPorAluno(usuarioAluno, outroAlunoId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Você não tem permissão para visualizar as observações de outro aluno.");

        verify(observacaoFeedbackRepository, never()).findByAlunoIdOrderByDataCriacaoDesc(any());
    }

    // =========================================================================
    // RN-014: Manutenção e restrição de alunos inativos
    // =========================================================================

    @Test
    @DisplayName("US26 / RN-014: Aluno inativo é impedido de registrar nova observação")
    void US26_rn014_alunoInativoNaoRegistra() {
        aluno.setStatus(StatusConvite.INATIVO);
        CriarObservacaoFeedbackRequestDTO dto = new CriarObservacaoFeedbackRequestDTO("Tentando registrar");

        when(alunoRepository.findByUsuario(usuarioAluno)).thenReturn(Optional.of(aluno));

        assertThatThrownBy(() -> observacaoFeedbackService.registrarObservacaoPeloAluno(usuarioAluno, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Erro. Este aluno está inativo.");

        verify(observacaoFeedbackRepository, never()).save(any());
    }
}
