package com.edge.pulse.data.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateFormRequestTest {

    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void anonWindowMinutesZeroIsRejected() {
        Set<ConstraintViolation<CreateFormRequest>> violations =
                validator.validate(new CreateFormRequest("Title", null, 0));
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("anonWindowMinutes"));
    }

    @Test
    void anonWindowMinutesNullIsAllowed() {
        assertThat(validator.validate(new CreateFormRequest("Title", null, null))).isEmpty();
    }

    @Test
    void anonWindowMinutesPositiveIsAllowed() {
        assertThat(validator.validate(new CreateFormRequest("Title", null, 60))).isEmpty();
    }
}
