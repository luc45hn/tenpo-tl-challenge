package com.tenpo.challenge.config;

import com.tenpo.challenge.ratelimit.RateLimitProperties;
import com.tenpo.challenge.util.Durations;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

/**
 * OpenAPI document of the public API. The operations declare their own specific errors (e.g. 400,
 * 503) with just a description; the errors every operation can return (429, 500) and the problem
 * detail content of all errors are added here, once.
 */
@Configuration
@EnableConfigurationProperties({PercentageCacheProperties.class, PercentageRetryProperties.class,
        RateLimitProperties.class})
public class OpenApiConfig {

    static final String PROBLEM_JSON = "application/problem+json";
    static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

    private static final String API_PATHS = "/api/v1/";

    @Bean
    public OpenAPI openApi(PercentageCacheProperties cache, PercentageRetryProperties retry,
                           RateLimitProperties rateLimit) {
        String description = """
                Adds two numbers and applies a dynamic percentage obtained from an external service, \
                and exposes the history of calls.

                The percentage is cached for %s; if the external service fails, the call is retried \
                (up to %d attempts in total) and, if it still fails, the last cached percentage is used. \
                The whole API accepts at most %d requests per %s; beyond that it answers 429 with a \
                Retry-After header. Errors are RFC 9457 problem details (application/problem+json)."""
                .formatted(Durations.quantity(cache.ttl()), retry.maxAttempts(), rateLimit.maxRequests(),
                        Durations.per(rateLimit.window()));
        return new OpenAPI()
                .info(new Info()
                        .title("Tenpo Challenge API")
                        .version("v1")
                        .description(description));
    }

    /**
     * Adds the errors shared by every operation of the public API, gives every error response the
     * problem detail content, and lists the responses by status code.
     */
    @Bean
    public OpenApiCustomizer sharedErrorResponses(RateLimitProperties rateLimit) {
        return openApi -> {
            // Registered here, together with its references: springdoc drops unreferenced schemas
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema());
            addSharedErrorResponses(openApi, rateLimit);
        };
    }

    private static void addSharedErrorResponses(OpenAPI openApi, RateLimitProperties rateLimit) {
        openApi.getPaths().entrySet().stream()
                .filter(path -> path.getKey().startsWith(API_PATHS))
                .flatMap(path -> path.getValue().readOperations().stream())
                .forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    responses.addApiResponse("429", tooManyRequests(rateLimit));
                    responses.addApiResponse("500", new ApiResponse().description(
                            "Unexpected error. The body is generic and never exposes internal details."));
                    // Errors are always problem details; springdoc would otherwise give an error
                    // declared without content the success schema of the method
                    responses.forEach((code, response) -> {
                        if (isError(code)) {
                            response.setContent(problemDetailContent());
                        }
                    });
                    operation.setResponses(sortedByCode(responses));
                });
    }

    private static ApiResponse tooManyRequests(RateLimitProperties rateLimit) {
        return new ApiResponse()
                .description("Rate limit exceeded: the API accepts at most %d requests per %s in total."
                        .formatted(rateLimit.maxRequests(), Durations.per(rateLimit.window())))
                .addHeaderObject(HttpHeaders.RETRY_AFTER, new Header()
                        .description("Seconds to wait before a request can be accepted again.")
                        .schema(new IntegerSchema().minimum(BigDecimal.ONE)));
    }

    /**
     * RFC 9457 problem detail as the API returns it.
     */
    @SuppressWarnings("rawtypes")
    private static Schema problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem detail.")
                .addProperty("type", new StringSchema().format("uri").example("about:blank"))
                .addProperty("title", new StringSchema().description("Short summary of the problem.")
                        .example("Invalid request parameters"))
                .addProperty("status", new IntegerSchema().description("HTTP status code.").example(400))
                .addProperty("detail", new StringSchema().description("Explanation specific to this occurrence.")
                        .example("num1 must be a valid number"))
                .addProperty("instance", new StringSchema().format("uri-reference")
                        .description("Path of the request.").example("/api/v1/calculate"))
                .addProperty("errors", new ObjectSchema()
                        .description("Only for invalid parameters: the problem with each parameter.")
                        .additionalProperties(new StringSchema())
                        .example(Map.of("num1", "must be a valid number")));
    }

    private static Content problemDetailContent() {
        return new Content().addMediaType(PROBLEM_JSON, new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA)));
    }

    private static boolean isError(String code) {
        return code.startsWith("4") || code.startsWith("5");
    }

    private static ApiResponses sortedByCode(ApiResponses responses) {
        ApiResponses sorted = new ApiResponses();
        responses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> sorted.addApiResponse(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
