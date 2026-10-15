package com.gymnasion.tcc.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controle de tentativas de autenticação e bloqueio temporário (RN-010 / US-02).
 * Bloqueia o login após 5 tentativas inválidas consecutivas por 5 minutos.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_TENTATIVAS = 5;
    public static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(5);

    private final Map<String, TentativasInfo> tentativasMap = new ConcurrentHashMap<>();
    private Clock clock;

    public LoginAttemptService() {
        this(Clock.systemDefaultZone());
    }

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public synchronized void loginFailed(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String chave = normalizar(email);
        Instant agora = clock.instant();

        TentativasInfo info = tentativasMap.get(chave);
        if (info == null || info.isBloqueioExpirado(agora, TEMPO_BLOQUEIO)) {
            tentativasMap.put(chave, new TentativasInfo(1, agora, null));
            return;
        }

        int novasTentativas = info.tentativas() + 1;
        Instant bloqueadoAte = null;
        if (novasTentativas >= MAX_TENTATIVAS) {
            bloqueadoAte = agora.plus(TEMPO_BLOQUEIO);
        }
        tentativasMap.put(chave, new TentativasInfo(novasTentativas, agora, bloqueadoAte));
    }

    public synchronized void loginSucceeded(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        tentativasMap.remove(normalizar(email));
    }

    public synchronized boolean isBlocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String chave = normalizar(email);
        TentativasInfo info = tentativasMap.get(chave);
        if (info == null) {
            return false;
        }

        Instant agora = clock.instant();
        if (info.bloqueadoAte() != null) {
            if (agora.isBefore(info.bloqueadoAte())) {
                return true;
            } else {
                tentativasMap.remove(chave);
                return false;
            }
        }
        return false;
    }

    public synchronized int getTentativas(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        TentativasInfo info = tentativasMap.get(normalizar(email));
        return info != null ? info.tentativas() : 0;
    }

    public synchronized void reset(String email) {
        if (email != null) {
            tentativasMap.remove(normalizar(email));
        }
    }

    public synchronized void resetAll() {
        tentativasMap.clear();
    }

    private String normalizar(String email) {
        return email.toLowerCase().trim();
    }

    private record TentativasInfo(int tentativas, Instant ultimaTentativa, Instant bloqueadoAte) {
        boolean isBloqueioExpirado(Instant agora, Duration duracaoBloqueio) {
            if (bloqueadoAte != null) {
                return !agora.isBefore(bloqueadoAte);
            }
            return Duration.between(ultimaTentativa, agora).compareTo(duracaoBloqueio) >= 0;
        }
    }
}
