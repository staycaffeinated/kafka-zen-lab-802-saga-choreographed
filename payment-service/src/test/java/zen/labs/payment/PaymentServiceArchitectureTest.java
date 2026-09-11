package zen.labs.payment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
        packages = "zen.labs.payment",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class PaymentServiceArchitectureTest {

    // ──────────────────────────────────────────────────────────────────────
    // Layer dependency rules
    //      domain → nothing
    //      application → domain only
    //      adapter → no infrastructure, can use domain and application
    //      infrastructure → no adapter
    // ──────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_must_not_depend_on_other_layers = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "zen.labs.payment.application..",
                    "zen.labs.payment.adapter..",
                    "zen.labs.payment.infrastructure..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_outer_layers = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("zen.labs.payment.adapter..", "zen.labs.payment.infrastructure..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_adapter_components = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("zen.labs.adapters..");

    @ArchTest
    static final ArchRule adapters_must_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.adapter..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("zen.labs.payment.infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_adapters = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("zen.labs.payment.adapter..");

    // ──────────────────────────────────────────────────────────────────────
    // Naming conventions
    // ──────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule rest_controllers_must_be_suffixed_controller =
            classes().that().areAnnotatedWith(RestController.class).should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule rest_controllers_must_reside_in_adapter_inbound = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("zen.labs.payment.adapter.inbound..");

    @ArchTest
    static final ArchRule controller_advice_must_reside_in_adapters_layer = classes()
            .that()
            .areAnnotatedWith(ControllerAdvice.class)
            .should()
            .resideInAPackage("zen.labs.adapters.common.handlers..")
            .as("@ControllerAdvice classes must reside in zen.labs.adapters.common.handlers — violating class(es):")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule configuration_classes_must_be_suffixed_configuration = classes()
            .that()
            .resideInAPackage("zen.labs.payment.infrastructure.config..")
            .and()
            .areAnnotatedWith(Configuration.class)
            .and()
            .areNotAnnotatedWith(SpringBootApplication.class)
            .should()
            .haveSimpleNameEndingWith("Configuration");

    // ──────────────────────────────────────────────────────────────────────
    // Spring best practices
    // ──────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule no_field_injection = noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("zen.labs.payment..")
            .should()
            .beAnnotatedWith(Autowired.class);

    // ──────────────────────────────────────────────────────────────────────
    // Domain purity
    // ──────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_must_not_depend_on_springframework = noClasses()
            .that()
            .resideInAPackage("zen.labs.payment.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..");
}
