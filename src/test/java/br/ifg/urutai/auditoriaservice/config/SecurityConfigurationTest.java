package br.ifg.urutai.auditoriaservice.config;

import br.ifg.urutai.auditoriaservice.support.TokensTeste;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuracao = new SecurityConfiguration();

    private static final String CHAVE_32 = "a".repeat(32);
    private static final String CHAVE_48 = "b".repeat(48);
    private static final String CHAVE_64 = "c".repeat(64);

    // Chave com menos de 32 bytes impede a aplicação de subir.
    @Test
    void chaveCurtaImpedeASubida() {
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> configuracao.jwtDecoder("a".repeat(31)));
        assertEquals("A chave JWT deve ter pelo menos 32 bytes", erro.getMessage());
    }

    // O algoritmo do token depende do tamanho da chave.
    @ParameterizedTest
    @CsvSource({"32, HS256", "47, HS256", "48, HS384", "63, HS384", "64, HS512", "100, HS512"})
    void algoritmoPeloTamanhoDaChave(int tamanho, String algoritmo) {
        String chave = "k".repeat(tamanho);
        JwtDecoder decoder = configuracao.jwtDecoder(chave);

        Jwt jwt = decoder.decode(TokensTeste.gerar(chave, JWSAlgorithm.parse(algoritmo), 5L, List.of("ROLE_ALUNO"), Instant.now().plusSeconds(60)));

        assertEquals(5L, ((Number) jwt.getClaim("id")).longValue());
        assertEquals(algoritmo, jwt.getHeaders().get("alg").toString());
    }

    // Recusa token assinado com outro algoritmo.
    @Test
    void recusaAlgoritmoDiferenteDoEsperado() {
        JwtDecoder decoder = configuracao.jwtDecoder(CHAVE_64);
        String hs256 = TokensTeste.gerar(CHAVE_64, JWSAlgorithm.HS256, 1L, List.of(), Instant.now().plusSeconds(60));

        assertThrows(JwtException.class, () -> decoder.decode(hs256));
    }

    // Recusa token assinado com outra chave.
    @Test
    void recusaAssinaturaDeOutraChave() {
        JwtDecoder decoder = configuracao.jwtDecoder(CHAVE_32);
        String outraChave = TokensTeste.gerar("z".repeat(32), JWSAlgorithm.HS256, 1L, List.of(), Instant.now().plusSeconds(60));

        assertThrows(JwtException.class, () -> decoder.decode(outraChave));
    }

    // Recusa token vencido.
    @Test
    void recusaTokenExpirado() {
        JwtDecoder decoder = configuracao.jwtDecoder(CHAVE_48);
        String expirado = TokensTeste.gerar(CHAVE_48, JWSAlgorithm.HS384, 1L, List.of(), Instant.now().minusSeconds(3600));

        assertThrows(JwtException.class, () -> decoder.decode(expirado));
    }

    // Recusa texto que não é um token.
    @Test
    void recusaTokenMalformado() {
        assertThrows(JwtException.class, () -> configuracao.jwtDecoder(CHAVE_32).decode("nao-e-um-jwt"));
    }

    // Os papéis do usuário vêm do campo "roles" do token.
    @Test
    void conversorUsaClaimRolesSemPrefixoExtra() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256")
                .claim("roles", List.of("ROLE_COORDENADOR", "ROLE_ADMINISTRADOR")).build();

        assertEquals(List.of("ROLE_ADMINISTRADOR", "ROLE_COORDENADOR"), papeis(jwt));
    }

    // Token sem "roles" fica sem papéis.
    @Test
    void semClaimRolesNaoTemPapel() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").claim("id", 1).build();

        assertTrue(papeis(jwt).isEmpty());
    }

    // O Swagger mostra que a API usa token Bearer.
    @Test
    void openApiDeclaraBearer() {
        var openApi = new OpenApiConfiguracao().configurarDocumentacao();

        assertEquals("bearer", openApi.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
        assertTrue(openApi.getSecurity().getFirst().containsKey("bearerAuth"));
    }

    // Pega só os papéis (ROLE_...), ignorando outras permissões que o Spring adiciona.
    private List<String> papeis(Jwt jwt) {
        return configuracao.conversorJwt().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(a -> a.startsWith("ROLE_")).sorted().toList();
    }
}
