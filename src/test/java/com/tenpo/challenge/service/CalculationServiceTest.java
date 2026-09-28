package com.tenpo.challenge.service;

import com.tenpo.challenge.exception.PercentageUnavailableException;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CalculationServiceTest {

    private final PercentageService percentageService = mock(PercentageService.class);
    private final CalculationService service = new CalculationService(percentageService);

    @Test
    void appliesTheResolvedPercentageToTheSum() {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));

        BigDecimal result = service.calculate(new BigDecimal("5"), new BigDecimal("5"));

        assertThat(result).isEqualTo(new BigDecimal("11.00"));
    }

    @Test
    void throwsWhenThePercentageCannotBeObtained() {
        ResourceAccessException cause = new ResourceAccessException("Connection refused");
        given(percentageService.getPercentage()).willReturn(Try.failure(cause));

        assertThatThrownBy(() -> service.calculate(new BigDecimal("5"), new BigDecimal("5")))
                .isInstanceOf(PercentageUnavailableException.class)
                .hasCause(cause);
    }
}
