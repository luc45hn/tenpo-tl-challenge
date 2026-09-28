package com.tenpo.challenge.exception;

import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.PercentageService;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CalculationController.class)
@Import({CalculationService.class, GlobalExceptionHandlerTest.TestOnlyController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String INTERNAL_MESSAGE = "SELECT password FROM users WHERE id = 42";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PercentageService percentageService;

    @Test
    void anUnknownPathUnderTheApiIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("No endpoint GET /api/v1/unknown."))
                .andExpect(jsonPath("$.instance").value("/api/v1/unknown"));
    }

    @Test
    void anUnknownPathOutsideTheApiIsNotFound() throws Exception {
        mockMvc.perform(get("/nothing/here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No endpoint GET /nothing/here."));
    }

    @Test
    void anUnsupportedMethodIsNotAllowedAndNamesTheAllowedOnes() throws Exception {
        mockMvc.perform(post("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.detail")
                        .value("Method POST is not supported for /api/v1/calculate. Supported methods: GET."));
    }

    @Test
    void anUnsupportedAcceptHeaderIsNotAcceptable() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void anUnsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "Content type text/plain is not supported. Supported content types: application/json."));
    }

    @Test
    void aMissingRequiredParameterUsesTheInvalidParametersFormat() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("value is required"))
                .andExpect(jsonPath("$.errors.value").value("is required"));
    }

    @Test
    void anUnexpectedErrorIsAGenericInternalServerErrorLoggedOnceWithItsStackTrace(CapturedOutput output)
            throws Exception {
        String body = mockMvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain(INTERNAL_MESSAGE)
                .doesNotContain(InternalDetailsException.class.getSimpleName())
                .doesNotContain("SELECT")
                .doesNotContain("at com.tenpo");
        assertThat(output.getOut()).containsOnlyOnce("Unexpected error handling GET /test/failure");
        assertThat(output.getOut()).contains("ERROR").contains(InternalDetailsException.class.getName())
                .contains("\tat ");
    }

    @Test
    void theInvalidParametersFormatIsUnchangedForANonNumericValue() throws Exception {
        mockMvc.perform(get("/api/v1/calculate?num1=abc&num2=5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num1 must be a valid number"))
                .andExpect(jsonPath("$.errors.num1").value("must be a valid number"))
                .andExpect(jsonPath("$.instance").value("/api/v1/calculate"));
    }

    @Test
    void theInvalidParametersFormatIsUnchangedForAMissingValue() throws Exception {
        mockMvc.perform(get("/api/v1/calculate?num1=5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num2 is required"))
                .andExpect(jsonPath("$.errors.num2").value("is required"))
                .andExpect(jsonPath("$.instance").value("/api/v1/calculate"));
    }

    @Test
    void theRateLimitFormatIsUnchanged() throws Exception {
        mockMvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.detail")
                        .value("Rate limit exceeded: at most 3 requests per minute. Try again in 42 seconds."));
    }

    @Test
    void theServiceUnavailableFormatIsUnchangedAndLoggedOnOneLineWithTheRootCause(CapturedOutput output)
            throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.failure(
                new ResourceAccessException("I/O error on GET request", new ConnectException("Connection refused"))));

        mockMvc.perform(get("/api/v1/calculate?num1=5&num2=5"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Percentage service unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        "The percentage could not be obtained from the external service. Please try again later."));

        assertThat(output.getOut())
                .contains("WARN")
                .contains("Percentage service unavailable: java.net.ConnectException: Connection refused")
                .doesNotContain("\tat ");
    }

    /**
     * Endpoints that exist only to trigger errors the real controllers cannot.
     */
    @RestController
    @RequestMapping("/test")
    static class TestOnlyController {

        @GetMapping("/failure")
        String failure() {
            throw new InternalDetailsException(INTERNAL_MESSAGE);
        }

        @GetMapping("/rate-limited")
        String rateLimited() {
            throw new RateLimitExceededException(3, Duration.ofMinutes(1), Duration.ofSeconds(42));
        }

        @GetMapping("/required")
        String required(@RequestParam int value) {
            return "ok";
        }

        @PostMapping(path = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody Map<String, Object> body) {
            return "ok";
        }
    }

    static class InternalDetailsException extends RuntimeException {

        InternalDetailsException(String message) {
            super(message);
        }
    }
}
