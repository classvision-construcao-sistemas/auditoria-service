package br.ifg.urutai.auditoriaservice.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

// Cria tokens JWT assinados para usar nos testes.
public final class TokensTeste {

    public static final String SEGREDO = "segredo-de-teste-com-32-bytes-ok";

    private TokensTeste() {
    }

    public static String coordenador(long id) {
        return gerar(SEGREDO, JWSAlgorithm.HS256, id, List.of("ROLE_COORDENADOR"), Instant.now().plusSeconds(3600));
    }

    public static String comPapel(long id, String papel) {
        return gerar(SEGREDO, JWSAlgorithm.HS256, id, List.of(papel), Instant.now().plusSeconds(3600));
    }

    public static String gerar(String segredo, JWSAlgorithm algoritmo, Long id, List<String> papeis, Instant expira) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("usuario@ifg.edu.br")
                .issueTime(new Date())
                .expirationTime(Date.from(expira))
                .claim("roles", papeis);
        if (id != null) {
            claims.claim("id", id);
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(algoritmo), claims.build());
            jwt.sign(new MACSigner(segredo.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
