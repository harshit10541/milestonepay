package com.milestonepay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

    public static void main(String[] args) {
        // Timestamps are handled in UTC. This also avoids Postgres rejecting legacy zone names the JVM
        // may report (e.g. Asia/Calcutta) in the JDBC connection handshake.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BackendApplication.class, args);
    }
}
