package com.interviewprep.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.task.dto.CreateTaskRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockUsesIndiaTimeZone() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void dueDateTodayIsJudgedInIndiaTime() {
        // 2026-10-07T20:00Z is 2026-10-08 01:30 in India: the 7th is already past, the 8th is today.
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ClockConfig.BUSINESS_ZONE);
        try (ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .clockProvider(() -> clock)
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new CreateTaskRequest("Task", null, null, LocalDate.of(2026, 10, 7))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("dueDate");
            assertThat(validator.validate(new CreateTaskRequest("Task", null, null, LocalDate.of(2026, 10, 8))))
                    .isEmpty();
        }
    }
}
