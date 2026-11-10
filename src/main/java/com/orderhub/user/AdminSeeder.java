package com.orderhub.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Creates the initial ADMIN account from configuration so a fresh deployment is usable. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("No admin credentials configured; skipping admin seeding");
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!users.existsByEmailIgnoreCase(normalized)) {
            users.save(new AppUser(normalized, passwordEncoder.encode(password), Role.ADMIN));
            log.info("Seeded admin account {}", normalized);
        }
    }
}
