package com.bardaghji.erpmigration.authvalidator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of auth-validator.
 * Must stay in the root package com.bardaghji.erpmigration.authvalidator:
 * component scanning only covers this package and its sub-packages.
 */
@SpringBootApplication
public class AuthValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthValidatorApplication.class, args);
    }
}
