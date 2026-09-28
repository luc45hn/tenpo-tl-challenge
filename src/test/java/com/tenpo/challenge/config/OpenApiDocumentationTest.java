package com.tenpo.challenge.config;

import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.controller.HistoryController;
import com.tenpo.challenge.controller.MockPercentageController;
import com.tenpo.challenge.exception.GlobalExceptionHandler;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated OpenAPI document and Swagger UI, on a minimal application with the real controllers
 * (services mocked), the springdoc configuration and the exception handler, without Redis or
 * Postgres.
 */
@SpringBootTest(classes = OpenApiDocumentationTest.TestApplication.class)
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculationService calculationService;

    @MockitoBean
    private HistoryService historyService;

    @MockitoBean
    private RandomGenerator randomGenerator;

    private JsonNode document;

    @BeforeEach
    void loadDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();
        document = JsonMapper.shared().readTree(json);
    }

    @Test
    void documentsThePublicEndpointsButNotTheMockService() {
        assertThat(fieldNames(document.path("paths")))
                .containsExactlyInAnyOrder("/api/v1/calculate", "/api/v1/history");
    }

    @Test
    void describesTheApiWithItsConfiguredBehavior() {
        assertThat(document.path("info").path("title").asString()).isEqualTo("Tenpo Challenge API");
        assertThat(document.path("info").path("description").asString())
                .contains("cached for 30 minutes")
                .contains("up to 3 attempts")
                .contains("at most 3 requests per minute");
    }

    @Test
    void documentsNum1AndNum2AsRequiredQueryParameters() {
        JsonNode calculate = operation("/api/v1/calculate");

        for (String name : List.of("num1", "num2")) {
            JsonNode parameter = parameter(calculate, name);
            assertThat(parameter.path("in").asString()).isEqualTo("query");
            assertThat(parameter.path("required").asBoolean()).as("%s required", name).isTrue();
            assertThat(parameter.path("description").asString()).isNotBlank();
            assertThat(parameter.path("example").isMissingNode()).as("%s example", name).isFalse();
        }
    }

    @Test
    void documentsTheRealPagingConstraintsAndDefaults() {
        JsonNode history = operation("/api/v1/history");
        JsonNode size = parameter(history, "size").path("schema");
        JsonNode page = parameter(history, "page").path("schema");

        assertThat(size.path("minimum").asInt()).isEqualTo(1);
        assertThat(size.path("maximum").asInt()).isEqualTo(100);
        assertThat(size.path("default").asInt()).isEqualTo(20);
        assertThat(page.path("minimum").asInt()).isZero();
        assertThat(page.path("default").asInt()).isZero();
        assertThat(parameter(history, "size").path("required").asBoolean()).isFalse();
    }

    @Test
    void documentsExactlyTheResponsesEachOperationCanReturn() {
        assertThat(fieldNames(operation("/api/v1/calculate").path("responses")))
                .containsExactly("200", "400", "429", "500", "503");
        assertThat(fieldNames(operation("/api/v1/history").path("responses")))
                .containsExactly("200", "400", "429", "500");
    }

    @Test
    void documentsEveryErrorAsAProblemDetail() {
        for (String path : List.of("/api/v1/calculate", "/api/v1/history")) {
            JsonNode responses = operation(path).path("responses");
            fieldNames(responses).stream().filter(code -> !code.startsWith("2")).forEach(code -> {
                JsonNode content = responses.path(code).path("content");
                assertThat(fieldNames(content)).as("%s %s media types", path, code)
                        .containsExactly("application/problem+json");
                assertThat(content.path("application/problem+json").path("schema").path("$ref").asString())
                        .as("%s %s schema", path, code).isEqualTo(PROBLEM_DETAIL_REF);
            });
        }
        assertThat(fieldNames(document.path("components").path("schemas").path("ProblemDetail").path("properties")))
                .contains("type", "title", "status", "detail", "instance", "errors");
    }

    @Test
    void documentsTheRetryAfterHeaderOfTheTooManyRequestsResponse() {
        for (String path : List.of("/api/v1/calculate", "/api/v1/history")) {
            JsonNode retryAfter = operation(path).path("responses").path("429").path("headers").path("Retry-After");
            assertThat(retryAfter.isMissingNode()).as("%s Retry-After", path).isFalse();
            assertThat(retryAfter.path("schema").path("type").asString()).isEqualTo("integer");
        }
    }

    @Test
    void documentsTheSuccessSchemas() {
        assertThat(operation("/api/v1/calculate").path("responses").path("200").path("content")
                .path("application/json").path("schema").path("$ref").asString())
                .isEqualTo("#/components/schemas/CalculationResponse");
        assertThat(operation("/api/v1/history").path("responses").path("200").path("content")
                .path("application/json").path("schema").path("$ref").asString())
                .isEqualTo("#/components/schemas/HistoryPageResponse");
    }

    @Test
    void servesTheSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
    }

    private JsonNode operation(String path) {
        return document.path("paths").path(path).path("get");
    }

    private static JsonNode parameter(JsonNode operation, String name) {
        return StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .filter(parameter -> name.equals(parameter.path("name").asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Parameter " + name + " is not documented"));
    }

    private static List<String> fieldNames(JsonNode node) {
        return List.copyOf(node.propertyNames());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            DataJdbcRepositoriesAutoConfiguration.class,
            DataRedisAutoConfiguration.class
    })
    @Import({OpenApiConfig.class, GlobalExceptionHandler.class, CalculationController.class,
            HistoryController.class, MockPercentageController.class})
    static class TestApplication {
    }
}
