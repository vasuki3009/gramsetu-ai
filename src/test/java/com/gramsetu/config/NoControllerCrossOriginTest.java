package com.gramsetu.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare @CrossOrigin allows every origin and, because Spring merges it with the global
 * configuration, silently defeats the CORS_ALLOWED_ORIGINS allowlist in CorsConfig.
 * CORS must be configured in one place only.
 */
class NoControllerCrossOriginTest {

    @Test
    void noControllerOrHandlerUsesCrossOrigin() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.gramsetu")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            scanned++;
            if (type.isAnnotationPresent(CrossOrigin.class)) offenders.add(type.getSimpleName());
            for (Method m : type.getDeclaredMethods()) {
                if (m.isAnnotationPresent(CrossOrigin.class)) offenders.add(type.getSimpleName() + "#" + m.getName());
            }
        }
        assertTrue(scanned >= 5, "expected to find the application's controllers, found " + scanned);
        assertTrue(offenders.isEmpty(), "Remove @CrossOrigin (use CorsConfig): " + offenders);
    }
}
