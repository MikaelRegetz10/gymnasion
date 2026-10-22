package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Metrica;
import com.gymnasion.tcc.domain.Modalidade;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.domain.enums.CampoStrava;
import com.gymnasion.tcc.domain.enums.ResponsavelPreenchimento;
import com.gymnasion.tcc.domain.enums.TipoMetrica;
import com.gymnasion.tcc.dto.AtualizarMetricaRequestDTO;
import com.gymnasion.tcc.dto.CriarMetricaRequestDTO;
import com.gymnasion.tcc.dto.MensagemResponseDTO;
import com.gymnasion.tcc.dto.MetricaResponseDTO;
import com.gymnasion.tcc.exceptions.BusinessException;
import com.gymnasion.tcc.exceptions.DuplicateResourceException;
import com.gymnasion.tcc.exceptions.NotFoundException;
import com.gymnasion.tcc.repository.MetricaRepository;
import com.gymnasion.tcc.repository.ModalidadeRepository;
import com.gymnasion.tcc.repository.PersonalTrainerRepository;
import com.gymnasion.tcc.repository.RegistroMetricaRepository;
import com.gymnasion.tcc.repository.RotinaTreinoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricaServiceTest {

    @Mock
    private MetricaRepository metricaRepository;

    @Mock
    private ModalidadeRepository modalidadeRepository;

    @Mock
    private PersonalTrainerRepository personalTrainerRepository;

    @Mock
    private RegistroMetricaRepository registroMetricaRepository;

    @Mock
    private RotinaTreinoRepository rotinaTreinoRepository;

    @InjectMocks
    private MetricaService metricaService;

    private Usuario usuarioLogado;
    private PersonalTrainer personalTrainer;
    private Modalidade modalidade;

    @BeforeEach
    void setUp() {
        usuarioLogado = Usuario.builder()
                .id(UUID.randomUUID())
                .email("personal@gymnasion.com")
                .nome("Treinador Silva")
                .build();

        personalTrainer = PersonalTrainer.builder()
                .id(UUID.randomUUID())
                .usuario(usuarioLogado)
                .build();

        modalidade = Modalidade.builder()
                .id(1L)
                .nome("Natação")
                .build();
    }

    @Test
    @DisplayName("US18 Cenário 1: Criar métrica com sucesso com todos os campos válidos")
    void US18_cenario1_criarMetricaComSucesso() {
        // Dado: um personal autenticado e dados válidos para criação de métrica
        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(
                personalTrainer.getId(), 1L, "Tempo 100m")).thenReturn(false);

        CriarMetricaRequestDTO dto = new CriarMetricaRequestDTO(
                1L,
                "Tempo 100m",
                TipoMetrica.TEMPO,
                true,
                ResponsavelPreenchimento.AMBOS
        );

        when(metricaRepository.save(any(Metrica.class))).thenAnswer(invocation -> {
            Metrica m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Quando: cria a métrica
        MetricaResponseDTO response = metricaService.criarMetrica(usuarioLogado, dto);

        // Então: métrica é salva com todos os atributos e retorna DTO com sucesso
        assertThat(response).isNotNull();
        assertThat(response.titulo()).isEqualTo("Tempo 100m");
        assertThat(response.tipo()).isEqualTo(TipoMetrica.TEMPO);
        assertThat(response.obrigatoria()).isTrue();
        assertThat(response.quemPreenche()).isEqualTo(ResponsavelPreenchimento.AMBOS);
        assertThat(response.modalidadeId()).isEqualTo(1L);
        assertThat(response.nomeModalidade()).isEqualTo("Natação");
        assertThat(response.ativo()).isTrue();
        assertThat(response.quantidadeAlunosComRegistros()).isEqualTo(0L);

        verify(metricaRepository, times(1)).save(any(Metrica.class));
    }

    @Test
    @DisplayName("US18 Cenário 2: Campos obrigatórios ausentes são bloqueados pelas anotações de validação")
    void US18_cenario2_validacaoCamposObrigatorios() {
        jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        jakarta.validation.Validator validator = factory.getValidator();

        // DTO com todos os campos nulos
        CriarMetricaRequestDTO dtoInvalido = new CriarMetricaRequestDTO(
                null,
                "  ",
                null,
                null,
                null
        );

        var violations = validator.validate(dtoInvalido);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().equals("A modalidade é obrigatória."));
        assertThat(violations).anyMatch(v -> v.getMessage().equals("O título é obrigatório."));
        assertThat(violations).anyMatch(v -> v.getMessage().equals("O tipo de métrica é obrigatório."));
        assertThat(violations).anyMatch(v -> v.getMessage().equals("A indicação de obrigatoriedade é obrigatória."));
        assertThat(violations).anyMatch(v -> v.getMessage().equals("O responsável pelo preenchimento é obrigatório."));
    }

    @Test
    @DisplayName("US18 Cenário 3: Título duplicado para o mesmo personal e modalidade lança RN-016")
    void US18_cenario3_tituloDuplicadoMesmaModalidadeEPersonal() {
        // Dado: já existe métrica com o mesmo título na mesma modalidade
        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(
                personalTrainer.getId(), 1L, "Tempo 100m")).thenReturn(true);

        CriarMetricaRequestDTO dto = new CriarMetricaRequestDTO(
                1L,
                "Tempo 100m",
                TipoMetrica.TEMPO,
                false,
                ResponsavelPreenchimento.ALUNO
        );

        // Quando & Então: tenta criar e lança DuplicateResourceException com mensagem exata do DRS
        assertThatThrownBy(() -> metricaService.criarMetrica(usuarioLogado, dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Já existe uma métrica com esse nome nessa modalidade.");

        verify(metricaRepository, never()).save(any(Metrica.class));
    }

    @Test
    @DisplayName("US18 Cenário 4: Listagem por modalidade retorna título, tipo, obrigatoriedade, responsáveis e contagem de alunos")
    void US18_cenario4_listarMetricasPorModalidade() {
        // Dado: duas métricas cadastradas para a modalidade
        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));

        UUID metricaId1 = UUID.randomUUID();
        Metrica m1 = Metrica.builder()
                .id(metricaId1)
                .titulo("Braçadas")
                .tipo(TipoMetrica.NUMERICO)
                .obrigatoria(true)
                .quemPreenche(ResponsavelPreenchimento.PERSONAL)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        UUID metricaId2 = UUID.randomUUID();
        Metrica m2 = Metrica.builder()
                .id(metricaId2)
                .titulo("Observações do nado")
                .tipo(TipoMetrica.TEXTO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.ALUNO)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        when(metricaRepository.findByPersonalTrainerIdAndModalidadeId(personalTrainer.getId(), 1L))
                .thenReturn(List.of(m1, m2));
        when(registroMetricaRepository.countAlunosComRegistrosByMetricaId(metricaId1)).thenReturn(5L);
        when(registroMetricaRepository.countAlunosComRegistrosByMetricaId(metricaId2)).thenReturn(0L);

        // Quando: lista as métricas
        List<MetricaResponseDTO> lista = metricaService.listarMetricasPorModalidade(usuarioLogado, 1L);

        // Então: retorna lista completa com campos do DRS v2
        assertThat(lista).hasSize(2);

        MetricaResponseDTO dto1 = lista.get(0);
        assertThat(dto1.titulo()).isEqualTo("Braçadas");
        assertThat(dto1.tipo()).isEqualTo(TipoMetrica.NUMERICO);
        assertThat(dto1.obrigatoria()).isTrue();
        assertThat(dto1.quemPreenche()).isEqualTo(ResponsavelPreenchimento.PERSONAL);
        assertThat(dto1.quantidadeAlunosComRegistros()).isEqualTo(5L);

        MetricaResponseDTO dto2 = lista.get(1);
        assertThat(dto2.titulo()).isEqualTo("Observações do nado");
        assertThat(dto2.tipo()).isEqualTo(TipoMetrica.TEXTO);
        assertThat(dto2.obrigatoria()).isFalse();
        assertThat(dto2.quemPreenche()).isEqualTo(ResponsavelPreenchimento.ALUNO);
        assertThat(dto2.quantidadeAlunosComRegistros()).isEqualTo(0L);
    }

    @Test
    @DisplayName("US18 Cenário 5 e 6: Métrica exclusiva do personal e obrigatória configurada corretamente")
    void US18_cenario5_cenario6_metricaExclusivaPersonalEObrigatoria() {
        // Dado: DTO com responsável exclusivo PERSONAL e obrigatoria=true
        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(any(), any(), any()))
                .thenReturn(false);

        CriarMetricaRequestDTO dto = new CriarMetricaRequestDTO(
                1L,
                "FC Pico",
                TipoMetrica.FREQUENCIA_CARDIACA,
                true,
                ResponsavelPreenchimento.PERSONAL
        );

        when(metricaRepository.save(any(Metrica.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Quando: cria a métrica
        MetricaResponseDTO response = metricaService.criarMetrica(usuarioLogado, dto);

        // Então: campos refletem exclusividade do personal e obrigatoriedade
        assertThat(response.obrigatoria()).isTrue();
        assertThat(response.quemPreenche()).isEqualTo(ResponsavelPreenchimento.PERSONAL);
        assertThat(response.tipo()).isEqualTo(TipoMetrica.FREQUENCIA_CARDIACA);
    }

    @Test
    @DisplayName("US19 Cenário 1: Atualizar métrica alterando título, obrigatoriedade e responsáveis")
    void US19_cenario1_atualizarMetricaSemHistorico() {
        // Dado: métrica existente sem histórico
        UUID metricaId = UUID.randomUUID();
        Metrica m = Metrica.builder()
                .id(metricaId)
                .titulo("Tempo 50m")
                .tipo(TipoMetrica.TEMPO)
                .obrigatoria(false)
                .quemPreenche(ResponsavelPreenchimento.AMBOS)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.findByIdAndPersonalTrainerId(metricaId, personalTrainer.getId()))
                .thenReturn(Optional.of(m));
        when(registroMetricaRepository.existsByMetricaId(metricaId)).thenReturn(false);
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCaseAndIdNot(
                personalTrainer.getId(), 1L, "Tempo 100m Livre", metricaId)).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registroMetricaRepository.countAlunosComRegistrosByMetricaId(metricaId)).thenReturn(0L);

        AtualizarMetricaRequestDTO dto = new AtualizarMetricaRequestDTO(
                "Tempo 100m Livre",
                TipoMetrica.TEMPO,
                true,
                ResponsavelPreenchimento.PERSONAL
        );

        // Quando: atualiza métrica
        MetricaResponseDTO response = metricaService.atualizarMetrica(metricaId, usuarioLogado, dto);

        // Então: título, obrigatoriedade e responsável são atualizados
        assertThat(response.titulo()).isEqualTo("Tempo 100m Livre");
        assertThat(response.obrigatoria()).isTrue();
        assertThat(response.quemPreenche()).isEqualTo(ResponsavelPreenchimento.PERSONAL);
    }

    @Test
    @DisplayName("US19 Cenário 2: Métrica com histórico não permite alterar tipo (RN-017)")
    void US19_cenario2_bloquearAlteracaoTipoComHistorico() {
        // Dado: métrica existente com histórico de registros
        UUID metricaId = UUID.randomUUID();
        Metrica m = Metrica.builder()
                .id(metricaId)
                .titulo("Frequência Cardíaca")
                .tipo(TipoMetrica.FREQUENCIA_CARDIACA)
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.findByIdAndPersonalTrainerId(metricaId, personalTrainer.getId()))
                .thenReturn(Optional.of(m));
        when(registroMetricaRepository.existsByMetricaId(metricaId)).thenReturn(true);

        AtualizarMetricaRequestDTO dto = new AtualizarMetricaRequestDTO(
                "Frequência Cardíaca",
                TipoMetrica.NUMERICO,
                false,
                ResponsavelPreenchimento.AMBOS
        );

        // Quando & Então: tenta mudar tipo para NUMERICO e lança BusinessException (RN-017)
        assertThatThrownBy(() -> metricaService.atualizarMetrica(metricaId, usuarioLogado, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível alterar o tipo de uma métrica com histórico de registros. Crie uma nova métrica.");
    }

    @Test
    @DisplayName("US19 Cenário 3: Excluir métrica com histórico realiza soft-delete e mantém histórico")
    void US19_cenario3_excluirComHistoricoDescontinua() {
        UUID metricaId = UUID.randomUUID();
        Metrica m = Metrica.builder()
                .id(metricaId)
                .titulo("Tempo 50m")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.findByIdAndPersonalTrainerId(metricaId, personalTrainer.getId()))
                .thenReturn(Optional.of(m));
        when(registroMetricaRepository.existsByMetricaId(metricaId)).thenReturn(true);

        // Quando: solicita exclusão
        MensagemResponseDTO response = metricaService.excluirMetrica(metricaId, usuarioLogado);

        // Então: marcado como inativo / descontinuado
        assertThat(m.getAtivo()).isFalse();
        assertThat(response.mensagem()).contains("descontinuada com sucesso");
        verify(metricaRepository, times(1)).save(m);
        verify(metricaRepository, never()).delete(any(Metrica.class));
    }

    @Test
    @DisplayName("US19 Cenário 3 / RN-017: Excluir métrica vinculada a rotina sem execuções descontinua logicamente")
    void US19_cenario3_excluirMetricaVinculadaARotinaDescontinua() {
        UUID metricaId = UUID.randomUUID();
        Metrica m = Metrica.builder()
                .id(metricaId)
                .titulo("Tempo 50m")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.findByIdAndPersonalTrainerId(metricaId, personalTrainer.getId()))
                .thenReturn(Optional.of(m));
        when(registroMetricaRepository.existsByMetricaId(metricaId)).thenReturn(false);
        when(rotinaTreinoRepository.existsByMetricaId(metricaId)).thenReturn(true);

        // Quando: solicita exclusão
        MensagemResponseDTO response = metricaService.excluirMetrica(metricaId, usuarioLogado);

        // Então: marcado como inativo / descontinuado preservando a integridade da rotina
        assertThat(m.getAtivo()).isFalse();
        assertThat(response.mensagem()).contains("descontinuada com sucesso");
        verify(metricaRepository, times(1)).save(m);
        verify(metricaRepository, never()).delete(any(Metrica.class));
    }

    @Test
    @DisplayName("US19 Cenário 4: Excluir métrica sem histórico realiza exclusão física")
    void US19_cenario4_excluirSemHistoricoRemocaoFisica() {
        UUID metricaId = UUID.randomUUID();
        Metrica m = Metrica.builder()
                .id(metricaId)
                .titulo("Tempo 50m")
                .modalidade(modalidade)
                .personalTrainer(personalTrainer)
                .ativo(true)
                .build();

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.findByIdAndPersonalTrainerId(metricaId, personalTrainer.getId()))
                .thenReturn(Optional.of(m));
        when(registroMetricaRepository.existsByMetricaId(metricaId)).thenReturn(false);
        when(rotinaTreinoRepository.existsByMetricaId(metricaId)).thenReturn(false);

        // Quando: solicita exclusão
        MensagemResponseDTO response = metricaService.excluirMetrica(metricaId, usuarioLogado);

        // Então: exclusão física executada e mensagem literal
        assertThat(response.mensagem()).isEqualTo("Métrica excluída com sucesso!");
        verify(metricaRepository, times(1)).delete(m);
    }

    @Test
    @DisplayName("US18: Criar métrica com campo do Strava compatível salva com sucesso")
    void US18_criarMetricaComCampoStravaCompativel() {
        CriarMetricaRequestDTO dto = new CriarMetricaRequestDTO(
                1L, "Cadência Média", TipoMetrica.NUMERICO, false, ResponsavelPreenchimento.PERSONAL, CampoStrava.CADENCIA_MEDIA
        );

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(
                personalTrainer.getId(), 1L, "Cadência Média")).thenReturn(false);
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MetricaResponseDTO response = metricaService.criarMetrica(usuarioLogado, dto);

        assertThat(response).isNotNull();
        assertThat(response.campoStrava()).isEqualTo(CampoStrava.CADENCIA_MEDIA);
        assertThat(response.titulo()).isEqualTo("Cadência Média");
    }

    @Test
    @DisplayName("US18: Criar métrica com campo do Strava incompatível com o tipo lança BusinessException")
    void US18_criarMetricaComCampoStravaIncompativel() {
        CriarMetricaRequestDTO dto = new CriarMetricaRequestDTO(
                1L, "Tempo Incompatível", TipoMetrica.TEMPO, true, ResponsavelPreenchimento.ALUNO, CampoStrava.DISTANCIA_KM
        );

        when(personalTrainerRepository.getByUsuario(usuarioLogado)).thenReturn(Optional.of(personalTrainer));
        when(metricaRepository.existsByPersonalTrainerIdAndModalidadeIdAndTituloIgnoreCase(
                personalTrainer.getId(), 1L, "Tempo Incompatível")).thenReturn(false);
        when(modalidadeRepository.findById(1L)).thenReturn(Optional.of(modalidade));

        assertThatThrownBy(() -> metricaService.criarMetrica(usuarioLogado, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O campo do Strava selecionado não é compatível com o tipo de métrica.");
    }
}
