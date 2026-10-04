package com.bardaghji.erpmigration.mockecc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of mock-ecc.
 * Must stay in the root package com.bardaghji.erpmigration.mockecc:
 * component scanning only covers this package and its sub-packages.
 */
@SpringBootApplication
public class MockEccApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockEccApplication.class, args);
    }
}
