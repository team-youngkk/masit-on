package com.masiton.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * dependency-rules.md 8절의 목표 규칙이다.
 * 도메인 패키지는 후속 Task에서 생기므로 아직 대상 클래스가 없는 규칙도 실패로 보지 않는다.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT_PACKAGE)
class ArchitectureTest {

    static final String ROOT_PACKAGE = "com.masiton";

    @ArchTest
    static final ArchRule domain은_프레임워크에_의존하지_않는다 =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.google.api..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application은_infrastructure에_의존하지_않는다 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule presentation은_persistence에_접근하지_않는다 =
            noClasses().that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application은_스프링데이터와_영속성기술에_직접_의존하지_않는다 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.data.repository..",
                            "jakarta.persistence..",
                            "org.springframework.web.client..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule common은_도메인_패키지에_의존하지_않는다 =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT_PACKAGE + ".restaurant..",
                            ROOT_PACKAGE + ".creator..",
                            ROOT_PACKAGE + ".video..",
                            ROOT_PACKAGE + ".visit..",
                            ROOT_PACKAGE + ".orchestration..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule orchestration은_엔티티와_저장소를_소유하지_않는다 =
            noClasses().that()
                    .resideInAnyPackage(
                            ROOT_PACKAGE + ".orchestration.domain..",
                            ROOT_PACKAGE + ".orchestration.infrastructure.persistence..")
                    .should().beTopLevelClasses()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controller는_저장소에_직접_의존하지_않는다 =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Repository")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 패키지_순환_의존이_없다 =
            slices().matching(ROOT_PACKAGE + ".(*)..")
                    .should().beFreeOfCycles();
}
