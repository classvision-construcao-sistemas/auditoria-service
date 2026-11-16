package br.ifg.urutai.auditoriaservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Dados do usuário logado, lidos do JWT da requisição. */
@Component
public class UsuarioAutenticado {

    private JwtAuthenticationToken autenticacao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Usuário não autenticado");
        }
        return jwt;
    }

    /** Claim "id" do cv-authentication-service: vira usuario_id da auditoria/correção. */
    public Long id() {
        if (!(autenticacao().getToken().getClaim("id") instanceof Number numero)) {
            throw new AccessDeniedException("Usuário não identificado no token");
        }
        return numero.longValue();
    }

    /** Token bruto, repassado ao turma-service para ele aplicar a própria autorização. */
    public String token() {
        return autenticacao().getToken().getTokenValue();
    }
}
