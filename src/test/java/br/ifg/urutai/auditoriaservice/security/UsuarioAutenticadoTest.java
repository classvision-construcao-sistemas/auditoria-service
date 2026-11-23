package br.ifg.urutai.auditoriaservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioAutenticadoTest {

    private final UsuarioAutenticado usuario = new UsuarioAutenticado();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static void logar(Object claimId) {
        Jwt.Builder jwt = Jwt.withTokenValue("abc.def.ghi").header("alg", "HS256").subject("coord@ifg.br");
        if (claimId != null) {
            jwt.claim("id", claimId);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt.build(), AuthorityUtils.createAuthorityList("ROLE_COORDENADOR")));
    }

    // Lê o id do usuário e o token do JWT.
    @Test
    void leIdETokenDoJwt() {
        logar(42);

        assertEquals(42L, usuario.id());
        assertEquals("abc.def.ghi", usuario.token());
    }

    // Aceita id maior que um int.
    @Test
    void aceitaIdLongo() {
        logar(9_000_000_000L);

        assertEquals(9_000_000_000L, usuario.id());
    }

    // Token sem id nega o acesso.
    @Test
    void semClaimIdNegaAcesso() {
        logar(null);

        AccessDeniedException erro = assertThrows(AccessDeniedException.class, usuario::id);
        assertEquals("Usuário não identificado no token", erro.getMessage());
    }

    // Id em texto nega o acesso.
    @Test
    void idTextualNegaAcesso() {
        logar("42");

        assertThrows(AccessDeniedException.class, usuario::id);
    }

    // Sem ninguém logado, nega o acesso.
    @Test
    void semAutenticacaoNegaAcesso() {
        assertThrows(AccessDeniedException.class, usuario::token);
        assertThrows(AccessDeniedException.class, usuario::id);
    }

    // Login que não é por JWT nega o acesso.
    @Test
    void autenticacaoQueNaoEJwtNegaAcesso() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("x", "y", List.of()));

        AccessDeniedException erro = assertThrows(AccessDeniedException.class, usuario::token);
        assertEquals("Usuário não autenticado", erro.getMessage());
    }

    // JWT não autenticado nega o acesso.
    @Test
    void jwtNaoAutenticadoNegaAcesso() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", 1).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);

        assertThrows(AccessDeniedException.class, usuario::id);
    }
}
