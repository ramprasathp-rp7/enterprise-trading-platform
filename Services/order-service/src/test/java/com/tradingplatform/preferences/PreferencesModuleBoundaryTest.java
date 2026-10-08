package com.tradingplatform.preferences;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The preferences module boundary, enforced at build time because the compiler no longer does it.
 */
@AnalyzeClasses(packages = "com.tradingplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class PreferencesModuleBoundaryTest {

    /** Other modules may use PreferenceResolver and its types, and nothing else of this module. */
    @ArchTest
    static final ArchRule internalsAreReachableOnlyFromInsideTheModule =
            noClasses().that().resideOutsideOfPackage("com.tradingplatform.preferences..")
                    .should().dependOnClassesThat().resideInAPackage("com.tradingplatform.preferences.internal..")
                    .because("only the PreferenceResolver interface is published to other modules");

    /** The resolution path stays off the wire: no controller anywhere can serve it. */
    @ArchTest
    static final ArchRule resolverIsNotReachableFromAnyRoute =
            noClasses().that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                    .should().dependOnClassesThat().areAssignableTo(PreferenceResolver.class)
                    .because("a resolution route reachable with a customer token would leak contact details");

    /** Same for the admin resolution path. */
    @ArchTest
    static final ArchRule adminResolverIsNotReachableFromAnyRoute =
            noClasses().that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                    .should().dependOnClassesThat().areAssignableTo(AdminPreferenceResolver.class)
                    .because("a resolution route would leak an admin's contact details");

    /** The module reads its own table only, and other modules' data through their services. */
    @ArchTest
    static final ArchRule moduleDoesNotUseOtherModulesMappers =
            noClasses().that().resideInAPackage("com.tradingplatform.preferences..")
                    .should().dependOnClassesThat().resideInAPackage("com.tradingplatform.api.mappers..")
                    .because("accounts are reached through AccountService, the layer that owns them");
}
