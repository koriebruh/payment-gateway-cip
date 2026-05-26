package com.koriebruh.paymentgatewaycip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentGatewayCipApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewayCipApplication.class, args);
    }
}
