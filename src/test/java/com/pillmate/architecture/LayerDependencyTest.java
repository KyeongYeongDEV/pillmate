package com.pillmate.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerDependencyTest {

    static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().importPackages("com.pillmate");
    }

    @Test
    @DisplayName("domain 패키지는 presentation/application/infrastructure를 의존하지 않는다")
    void domain_should_not_depend_on_outer_layers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..presentation..", "..application..", "..infrastructure..");

        rule.check(classes);
    }

    @Test
    @DisplayName("presentation 패키지는 infrastructure를 직접 의존하지 않는다")
    void presentation_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

        rule.check(classes);
    }

    @Test
    @DisplayName("application 패키지는 infrastructure 구현체를 직접 의존하지 않는다 (Port 인터페이스만 허용)")
    void application_should_not_depend_on_infrastructure_impl() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..application..")
            .and().doNotHaveSimpleName("port")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

        rule.check(classes);
    }
}
