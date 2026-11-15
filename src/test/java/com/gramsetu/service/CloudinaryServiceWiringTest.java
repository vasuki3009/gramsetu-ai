package com.gramsetu.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creates CloudinaryService the way the application does (through the Spring container, with
 * property placeholders). The plain unit tests construct it directly and cannot catch wiring
 * problems such as an ambiguous constructor, which crash the app at startup.
 */
class CloudinaryServiceWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CloudinaryService.class);

    @Test
    void beanIsCreatedFromEnvironmentStyleProperties() {
        runner.withPropertyValues(
                        "cloudinary.cloud-name=cloud",
                        "cloudinary.api-key=key-value",
                        "cloudinary.api-secret=secret-value")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CloudinaryService.class);
                });
    }

    @Test
    void missingConfigurationStopsStartupWithAMessageNamingTheVariable() {
        runner.withPropertyValues(
                        "cloudinary.cloud-name=cloud",
                        "cloudinary.api-key=",
                        "cloudinary.api-secret=secret-value")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(rootMessage(context.getStartupFailure())).contains("CLOUDINARY_API_KEY");
                    assertThat(rootMessage(context.getStartupFailure())).doesNotContain("secret-value");
                });
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}
