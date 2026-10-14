package com.gymnasion.tcc.controller;

import com.gymnasion.tcc.domain.Usuario;
import com.gymnasion.tcc.dto.*;
import com.gymnasion.tcc.service.RotinaTreinoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personal-trainers/rotinas")
@AllArgsConstructor
@PreAuthorize("hasRole('PERSONAL_TRAINER')")
@Tag(name = "Personal Trainer - Rotinas de Treino", description = "Criação, consulta, vinculação e exclusão de rotinas de treino")
@SecurityRequirement(name = "bearerAuth")
public class RotinaTreinoController {

    private final RotinaTreinoService rotinaTreinoService;

    @PostMapping
    @Operation(summary = "Cadastrar rotina de treino (US-09)", description = "Cria uma nova rotina de treino vinculando modalidade, métricas personalizadas e alunos.")
    @ApiResponse(responseCode = "201", description = "Treino criado com sucesso!")
    @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes, frequência inválida ou sem métricas cadastradas.")
    public ResponseEntity<RotinaTreinoResponseDTO> criarRotina(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CriarRotinaTreinoRequestDTO dto) {
        RotinaTreinoResponseDTO response = rotinaTreinoService.criarRotinaTreino(usuario, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar todas as rotinas do Personal Trainer (US-09)", description = "Retorna todas as rotinas de treino criadas pelo Personal Trainer autenticado.")
    public ResponseEntity<List<RotinaTreinoResponseDTO>> listarMinhasRotinas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(rotinaTreinoService.listarPorPersonal(usuario));
    }

    @GetMapping("/aluno/{alunoId}")
    @Operation(summary = "Visualizar rotinas de treino do aluno (US-11)", description = "Retorna as rotinas associadas a um aluno específico.")
    public ResponseEntity<List<RotinaTreinoResponseDTO>> listarPorAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(rotinaTreinoService.listarPorAluno(usuario, alunoId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar rotina de treino por ID", description = "Retorna os detalhes completos de uma rotina de treino.")
    public ResponseEntity<RotinaTreinoResponseDTO> buscarPorId(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(rotinaTreinoService.buscarPorId(usuario, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir rotina de treino (US-12)", description = "Exclui uma rotina de treino do Personal Trainer.")
    @ApiResponse(responseCode = "200", description = "Rotina excluída com sucesso!")
    public ResponseEntity<MensagemResponseDTO> excluirRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID id) {
        rotinaTreinoService.excluirRotina(usuario, id);
        return ResponseEntity.ok(new MensagemResponseDTO("Rotina excluída com sucesso!"));
    }

    @PostMapping("/{id}/alunos")
    @Operation(summary = "Vincular alunos à rotina de treino (US-11)", description = "Vincula um ou mais alunos válidos a uma rotina de treino existente (C1). Bloqueia alunos já vinculados (C2) e aplica a trava de no máximo 1 rotina ativa por aluno.")
    @ApiResponse(responseCode = "200", description = "Treino vinculado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Aluno já vinculado, aluno inativo ou aluno já possui outra rotina ativa.")
    @ApiResponse(responseCode = "422", description = "Nenhum aluno informado (C3).")
    public ResponseEntity<RotinaTreinoResponseDTO> vincularAlunos(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId,
            @Valid @RequestBody VincularAlunosRotinaRequestDTO dto) {
        return ResponseEntity.ok(rotinaTreinoService.vincularAlunos(usuario, rotinaId, dto));
    }

    @DeleteMapping("/{id}/alunos/{alunoId}")
    @Operation(summary = "Desvincular aluno da rotina de treino", description = "Remove o vínculo de um aluno com a rotina de treino. Exige ao menos um aluno restante na rotina (RN-012).")
    @ApiResponse(responseCode = "200", description = "Aluno desvinculado com sucesso.")
    public ResponseEntity<RotinaTreinoResponseDTO> desvincularAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(rotinaTreinoService.desvincularAluno(usuario, rotinaId, alunoId));
    }

    @PatchMapping("/{id}/encerrar")
    @Operation(summary = "Encerrar rotina de treino (US-25 C6/C7)", description = "Encerra a rotina de treino ativa, liberando o aluno para receber uma nova rotina e removendo sessões futuras não realizadas.")
    @ApiResponse(responseCode = "200", description = "Rotina encerrada com sucesso.")
    public ResponseEntity<RotinaTreinoResponseDTO> encerrarRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId) {
        return ResponseEntity.ok(rotinaTreinoService.encerrarRotina(usuario, rotinaId));
    }


    @PostMapping("/{id}/sessoes")
    @Operation(summary = "Adicionar sessão ao planejamento da rotina (US-10 C1)", description = "Adiciona uma sessão de treino organizada por semana na rotina.")
    @ApiResponse(responseCode = "201", description = "Sessão de treino criada com sucesso.")
    public ResponseEntity<SessaoTreinoResponseDTO> adicionarSessao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId,
            @Valid @RequestBody CriarSessaoTreinoRequestDTO dto) {
        SessaoTreinoResponseDTO response = rotinaTreinoService.adicionarSessao(usuario, rotinaId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/planejamento")
    @Operation(summary = "Consultar planejamento por semanas (US-10 C1)", description = "Retorna o planejamento da rotina organizado por semanas com suas sessões e atividades.")
    public ResponseEntity<PlanejamentoRotinaResponseDTO> consultarPlanejamento(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId) {
        return ResponseEntity.ok(rotinaTreinoService.consultarPlanejamento(usuario, rotinaId));
    }

    @PutMapping("/sessoes/{sessaoId}")
    @Operation(summary = "Editar sessão futura (US-10 C2)", description = "Atualiza o planejamento de uma sessão não executada. Bloqueia a edição se a sessão já estiver concluída/executada.")
    @ApiResponse(responseCode = "200", description = "Sessão futura atualizada com sucesso.")
    @ApiResponse(responseCode = "422", description = "Não é possível editar uma sessão já executada.")
    public ResponseEntity<SessaoTreinoResponseDTO> editarSessaoFutura(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @Valid @RequestBody AtualizarSessaoTreinoRequestDTO dto) {
        return ResponseEntity.ok(rotinaTreinoService.editarSessaoFutura(usuario, sessaoId, dto));
    }

    @PostMapping("/sessoes/{sessaoId}/execucao")
    @Operation(summary = "Registrar resultados e confirmar execução (US-10 C3, C4, C5, C7)", description = "Registra os resultados das métricas e RPE, registra a data real de execução (preservando a planejada) e marca a sessão como concluída.")
    @ApiResponse(responseCode = "200", description = "Execução registrada com sucesso.")
    @ApiResponse(responseCode = "422", description = "Nenhum resultado de métrica informado, métrica exclusiva do aluno ou valores inválidos para o tipo configurado.")
    public ResponseEntity<SessaoTreinoResponseDTO> registrarExecucao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId,
            @Valid @RequestBody RegistrarExecucaoSessaoRequestDTO dto) {
        return ResponseEntity.ok(rotinaTreinoService.registrarExecucao(usuario, sessaoId, dto));
    }

    @PatchMapping("/sessoes/{sessaoId}/validar")
    @Operation(summary = "Validar execução do treino (US-10 C6)", description = "Personal valida a execução realizada pelo aluno. A conclusão do aluno permanece independente da validação.")
    @ApiResponse(responseCode = "200", description = "Sessão validada com sucesso.")
    public ResponseEntity<SessaoTreinoResponseDTO> validarExecucao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("sessaoId") UUID sessaoId) {
        return ResponseEntity.ok(rotinaTreinoService.validarExecucao(usuario, sessaoId));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Consultar histórico de treinos da rotina (US-10 C8)", description = "Retorna os treinos executados da rotina com data real, RPE e resultados das métricas.")
    public ResponseEntity<List<SessaoTreinoResponseDTO>> consultarHistoricoRotina(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("id") UUID rotinaId) {
        return ResponseEntity.ok(rotinaTreinoService.consultarHistoricoRotina(usuario, rotinaId));
    }

    @GetMapping("/aluno/{alunoId}/historico")
    @Operation(summary = "Consultar histórico de treinos do aluno (US-10 C8)", description = "Retorna todos os treinos realizados pelo aluno especificado.")
    public ResponseEntity<List<SessaoTreinoResponseDTO>> consultarHistoricoAluno(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable("alunoId") UUID alunoId) {
        return ResponseEntity.ok(rotinaTreinoService.consultarHistoricoAluno(usuario, alunoId));
    }
}