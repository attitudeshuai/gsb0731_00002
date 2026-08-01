package com.dbmanager.config;

import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

@Component("utcDateTimeProvider")
public class UtcDateTimeProvider implements DateTimeProvider {

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(Instant.now());
    }
}
