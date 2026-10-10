package com.polymarket.tma;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/**
 * Component scanning derives bean names from simple class names, so two {@code @Service}s called
 * {@code WithdrawService} in different packages crash startup with
 * {@code ConflictingBeanDefinitionException}. Unit tests don't boot the context (it needs Postgres
 * and Redis), so this guards the one failure mode that otherwise only shows up on deploy.
 */
class BeanNameUniquenessTest {

    @Test
    void scannedComponentsHaveUniqueBeanNames() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var registry = new SimpleBeanDefinitionRegistry();
        var names = new AnnotationBeanNameGenerator();

        Map<String, List<String>> classesByBeanName = new HashMap<>();
        for (var def : scanner.findCandidateComponents(PolymarketTmaApplication.class.getPackageName())) {
            classesByBeanName.computeIfAbsent(names.generateBeanName(def, registry), k -> new java.util.ArrayList<>())
                    .add(def.getBeanClassName());
        }

        Map<String, List<String>> duplicates = classesByBeanName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(classesByBeanName).as("scan found components").isNotEmpty();
        assertThat(duplicates).as("bean names shared by several classes").isEmpty();
    }
}
