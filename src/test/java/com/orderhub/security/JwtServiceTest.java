package com.orderhub.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-32-bytes-long";

    private final UserDetails user = User.withUsername("ada@example.com").password("x").roles("USER").build();

    @Test
    void issuedToken_isValidAndCarriesSubject() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.generateToken(user);

        assertThat(jwt.extractValidSubject(token)).contains("ada@example.com");
        assertThat(jwt.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(jwt.extractValidSubject(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        String token = new JwtService("another-secret-which-is-also-32-bytes-long!!", 60).generateToken(user);

        assertThat(new JwtService(SECRET, 60).extractValidSubject(token)).isEmpty();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwt = new JwtService(SECRET, -1); // already expired when issued
        assertThat(jwt.extractValidSubject(jwt.generateToken(user))).isEmpty();
    }

    @Test
    void garbage_isRejected() {
        assertThat(new JwtService(SECRET, 60).extractValidSubject("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", 60)).isInstanceOf(IllegalStateException.class);
    }
}
