package com.bardaghji.erpmigration.mocks4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of mock-s4.
 * Must stay in the root package com.bardaghji.erpmigration.mocks4:
 * component scanning only covers this package and its sub-packages.
 */
@SpringBootApplication
public class MockS4Application {

    public static void main(String[] args) {
        SpringApplication.run(MockS4Application.class, args);
    }
}
