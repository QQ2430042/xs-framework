package com.xsframework.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.xsframework");

    @Test
    void apiLayerIsPureJava() {
        classes()
                .that().resideInAPackage("com.xsframework.api..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "com.xsframework.api..",
                        "com.xsframework.common..",
                        "java..")
                .check(CLASSES);
    }

    @Test
    void commonLayerHasNoFrameworkDependency() {
        classes()
                .that().resideInAPackage("com.xsframework.common..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "com.xsframework.common..",
                        "java..")
                .check(CLASSES);
    }

    @Test
    void businessModulesDoNotDependOnEachOther() {
        slices()
                .matching("com.xsframework.module.(*)..")
                .should().notDependOnEachOther()
                .check(CLASSES);
    }

    @Test
    void applicationAssembliesDoNotDependOnEachOther() {
        slices()
                .matching("com.xsframework.app.(*)..")
                .should().notDependOnEachOther()
                .check(CLASSES);
    }
}
