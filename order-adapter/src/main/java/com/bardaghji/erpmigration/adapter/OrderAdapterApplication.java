package com.bardaghji.erpmigration.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of order-adapter.
 * Must stay in the root package com.bardaghji.erpmigration.adapter:
 * component scanning only covers this package and its sub-packages.
 */
@SpringBootApplication
public class OrderAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderAdapterApplication.class, args);
    }
}
