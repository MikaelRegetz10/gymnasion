package com.gymnasion.tcc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;
    private MutableClock mutableClock;

    @BeforeEach
    void setUp() {
        mutableClock = new MutableClock(Instant.parse("2026-10-15T12:00:00Z"));
        loginAttemptService = new LoginAttemptService(mutableClock);
    }

    @Test
    @DisplayName("RN-010: Inicialmente usuário não possui tentativas e não está bloqueado")
    void inicial_naoBloqueado() {
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isFalse();
        assertThat(loginAttemptService.getTentativas("user@teste.com")).isZero();
    }

    @Test
    @DisplayName("RN-010: Registra falhas consecutivas até 4 sem bloquear")
    void ate4Tentativas_naoBloqueia() {
        for (int i = 1; i <= 4; i++) {
            loginAttemptService.loginFailed("user@teste.com");
            assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(i);
            assertThat(loginAttemptService.isBlocked("user@teste.com")).isFalse();
        }
    }

    @Test
    @DisplayName("RN-010: Na 5ª tentativa inválida consecutiva, bloqueia o usuário por 5 minutos")
    void na5aTentativa_bloqueiaPor5Minutos() {
        for (int i = 1; i <= 5; i++) {
            loginAttemptService.loginFailed("user@teste.com");
        }

        assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(5);
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isTrue();

        // 4 minutos e 59 segundos depois ainda permanece bloqueado
        mutableClock.plus(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isTrue();

        // Após transcorridos 5 minutos completos, o bloqueio expira automaticamente
        mutableClock.plus(Duration.ofSeconds(2));
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isFalse();
    }

    @Test
    @DisplayName("RN-010: Sucesso no login reseta contador de tentativas falhas")
    void loginComSucesso_resetaTentativas() {
        loginAttemptService.loginFailed("user@teste.com");
        loginAttemptService.loginFailed("user@teste.com");
        assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(2);

        loginAttemptService.loginSucceeded("user@teste.com");

        assertThat(loginAttemptService.getTentativas("user@teste.com")).isZero();
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isFalse();
    }

    @Test
    @DisplayName("RN-010: Normalização de e-mail (ignora maiúsculas e espaços)")
    void normalizacaoDeEmail() {
        loginAttemptService.loginFailed("User@Teste.com ");
        loginAttemptService.loginFailed(" USER@TESTE.COM");

        assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-010: Intervalo maior que 5 minutos entre falhas reseta contador antes de atingir 5 tentativas")
    void intervaloMaiorQue5Minutos_resetaContador() {
        loginAttemptService.loginFailed("user@teste.com");
        loginAttemptService.loginFailed("user@teste.com");
        assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(2);

        // Avança 6 minutos
        mutableClock.plus(Duration.ofMinutes(6));

        // Próxima falha reinicia a contagem
        loginAttemptService.loginFailed("user@teste.com");
        assertThat(loginAttemptService.getTentativas("user@teste.com")).isEqualTo(1);
        assertThat(loginAttemptService.isBlocked("user@teste.com")).isFalse();
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneId zone = ZoneId.of("UTC");

        public MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        public void plus(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
