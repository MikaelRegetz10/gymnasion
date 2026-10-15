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
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {

    private final UsuarioRepository userRepository;
    private final AlunoRepository alunoRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public TokenResponseDTO login(LoginRequestDTO dto) {
        String email = dto.email().toLowerCase().trim();

        // RN-010: Bloquear login após 5 tentativas inválidas por 5 minutos
        if (loginAttemptService.isBlocked(email)) {
            throw new ContaBloqueadaException("Conta temporariamente bloqueada após 5 tentativas inválidas. Tente novamente em 5 minutos.");
        }

        Usuario user;
        try {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));

            if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
                throw new BadCredentialsException("E-mail ou senha inválidos.");
            }
        } catch (BadCredentialsException e) {
            loginAttemptService.loginFailed(email);
            throw e;
        }

        // RN-014: Manutenção e restrição de alunos inativos: perdem login imediatamente
        if (user.getRole() == Role.ALUNO) {
            Aluno aluno = alunoRepository.findByUsuario(user).orElse(null);
            if (aluno != null) {
                if (aluno.getStatus() == StatusConvite.INATIVO) {
                    throw new BusinessException("Erro. Este aluno está inativo.");
                }
                if (aluno.getStatus() == StatusConvite.PENDENTE) {
                    throw new BusinessException("Aguardando a aprovação do personal trainer para liberar o acesso.");
                }
                if (aluno.getStatus() == StatusConvite.RECUSADO) {
                    throw new BusinessException("Cadastro recusado pelo personal trainer.");
                }
            }
        }

        // Login realizado com sucesso -> reseta tentativas inválidas
        loginAttemptService.loginSucceeded(email);

        String token = tokenService.generateToken(user);
        return TokenResponseDTO.of(token, user.getId(), user.getRole());
    }
}
