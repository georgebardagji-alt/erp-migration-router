package com.bardaghji.erpmigration.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of reconciliation-service.
 * Must stay in the root package com.bardaghji.erpmigration.reconciliation:
 * component scanning only covers this package and its sub-packages.
 */
@SpringBootApplication
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}
