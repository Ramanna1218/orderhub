package com.orderhub.auth;

import com.orderhub.auth.AuthDtos.AuthResponse;
import com.orderhub.auth.AuthDtos.LoginRequest;
import com.orderhub.auth.AuthDtos.RegisterRequest;
import com.orderhub.common.exception.DuplicateResourceException;
import com.orderhub.security.JwtService;
import com.orderhub.user.AppUser;
import com.orderhub.user.Role;
import com.orderhub.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    /** Self-service registration always creates a USER; admins are provisioned via configuration. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password()), Role.USER));
        return toResponse(userDetailsService.loadUserByUsername(user.getEmail()), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return toResponse((UserDetails) auth.getPrincipal(), user.getRole());
    }

    private AuthResponse toResponse(UserDetails details, Role role) {
        return new AuthResponse(jwtService.generateToken(details), "Bearer",
                jwtService.getExpirationSeconds(), details.getUsername(), role.name());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
