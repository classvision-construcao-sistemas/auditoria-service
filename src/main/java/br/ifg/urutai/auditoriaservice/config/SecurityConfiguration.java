package br.ifg.urutai.auditoriaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Valida o JWT emitido pelo cv-authentication-service (mesmo JWT_SECRET) e
 * restringe as rotas por papel. Mesmo padrão do turma-service.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter conversorJwt) {

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger: documentação pública.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // RN-01: só coordenador realiza e corrige auditoria (administrador geral também).
                        .requestMatchers(HttpMethod.POST, "/auditorias/previa", "/auditorias", "/auditorias/*/correcoes")
                        .hasAnyRole("COORDENADOR", "ADMINISTRADOR")
                        // Histórico: qualquer usuário autenticado.
                        .requestMatchers(HttpMethod.GET, "/auditorias", "/auditorias/*").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorJwt)));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter conversorJwt() {
        // A claim "roles" já vem com o prefixo ROLE_.
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("roles");
        autoridades.setAuthorityPrefix("");

        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String segredo) {

        byte[] chave = segredo.getBytes(StandardCharsets.UTF_8);

        if (chave.length < 32) {
            throw new IllegalStateException("A chave JWT deve ter pelo menos 32 bytes");
        }

        MacAlgorithm algoritmo;
        String nomeAlgoritmo;

        if (chave.length >= 64) {
            algoritmo = MacAlgorithm.HS512;
            nomeAlgoritmo = "HmacSHA512";
        } else if (chave.length >= 48) {
            algoritmo = MacAlgorithm.HS384;
            nomeAlgoritmo = "HmacSHA384";
        } else {
            algoritmo = MacAlgorithm.HS256;
            nomeAlgoritmo = "HmacSHA256";
        }

        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(chave, nomeAlgoritmo)).macAlgorithm(algoritmo).build();
    }
}
