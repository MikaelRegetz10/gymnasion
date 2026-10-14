package com.gymnasion.tcc.service;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.Notificacao;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import com.gymnasion.tcc.event.LembreteAlunosPendentesEvent;
import com.gymnasion.tcc.infra.config.AlunosPendentesProperties;
import com.gymnasion.tcc.repository.AlunoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verifica personals com alunos aguardando aprovação há mais que o tempo limite (X)
 * e gera lembrete in-app + e-mail, respeitando o intervalo mínimo entre lembretes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LembreteAlunosPendentesService {

    private final AlunoRepository alunoRepository;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher eventPublisher;
    private final AlunosPendentesProperties properties;

    /**
     * @return quantidade de personals lembrados nesta execução
     */
    @Transactional
    public int processarLembretes() {
        if (!properties.enabled()) {
            return 0;
        }

        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime limiteCadastro = agora.minus(properties.tempoLimite());
        OffsetDateTime limiteUltimoLembrete = agora.minus(properties.intervaloLembrete());
        String tempoFormatado = NotificacaoService.formatarDuracao(properties.tempoLimite());

        Map<UUID, List<Aluno>> pendentesPorPersonal = alunoRepository
                .findByStatusCadastradosAntesDe(StatusConvite.PENDENTE, limiteCadastro)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPersonal().getId(), LinkedHashMap::new, Collectors.toList()));

        int lembrados = 0;
        for (List<Aluno> alunos : pendentesPorPersonal.values()) {
            PersonalTrainer personal = alunos.get(0).getPersonal();

            Optional<Notificacao> ultimoLembrete = notificacaoService.buscarLembreteAlunosPendentes(personal);
            if (ultimoLembrete.isPresent() && ultimoLembrete.get().getDataCriacao().isAfter(limiteUltimoLembrete)) {
                continue; // já lembrado recentemente
            }

            notificacaoService.registrarLembreteAlunosPendentes(personal, alunos.size(), properties.tempoLimite());

            List<String> nomes = alunos.stream().map(a -> a.getUsuario().getNome()).toList();
            eventPublisher.publishEvent(new LembreteAlunosPendentesEvent(
                    personal.getUsuario().getEmail(),
                    personal.getUsuario().getNome(),
                    nomes,
                    tempoFormatado
            ));
            lembrados++;
        }

        if (lembrados > 0) {
            log.info("Lembrete de alunos pendentes enviado para {} personal(is).", lembrados);
        }
        return lembrados;
    }
}
