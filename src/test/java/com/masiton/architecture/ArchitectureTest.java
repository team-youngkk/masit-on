package com.masiton.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * dependency-rules.md 8절의 목표 규칙이다.
 * 도메인 패키지는 후속 Task에서 생기므로 아직 대상 클래스가 없는 규칙도 실패로 보지 않는다.
 */
@AnalyzeClasses(
        packages = ArchitectureTest.ROOT_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class
)
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
    static final ArchRule application은_영속성과_HTTP기술에_직접_의존하지_않는다 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.data.repository..",
                            "jakarta.persistence..",
                            "org.springframework.web.client..",
                            "java.net.http..")
                    .allowEmptyShould(true);

    /**
     * 도메인을 나열하지 않고 "common 밖의 모든 com.masiton 패키지"로 막는다.
     * 새 최상위 도메인이 생겨도 규칙을 고치지 않아도 된다.
     */
    private static final DescribedPredicate<JavaClass> COMMON_밖의_애플리케이션_클래스 =
            resideInAPackage(ROOT_PACKAGE + "..")
                    .and(resideOutsideOfPackage(ROOT_PACKAGE + ".common.."))
                    .as("com.masiton 중 common 밖의 클래스");

    @ArchTest
    static final ArchRule common은_도메인_패키지에_의존하지_않는다 =
            noClasses().that().resideInAPackage(ROOT_PACKAGE + ".common..")
                    .should().dependOnClassesThat(COMMON_밖의_애플리케이션_클래스)
                    .allowEmptyShould(true);

    /**
     * `orchestration`은 Entity와 Repository를 소유하지 않는다.
     * dependency-rules.md 8절이 두 패키지의 생성 자체를 금지하므로 클래스가 하나라도 있으면 위반이다.
     */
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

    /**
     * dependency-rules.md 3절: 한 도메인의 JPA Entity가 다른 도메인의 JPA Entity를 객체 연관관계로
     * 참조하거나, 다른 도메인의 Spring Data Repository를 직접 주입하는 것을 금지한다.
     * 도메인 이름을 나열하지 않고 최상위 패키지 세그먼트를 비교해 새 도메인이 생겨도 규칙을
     * 고치지 않아도 되게 한다.
     */
    @ArchTest
    static final ArchRule 도메인간_persistence_직접의존을_금지한다 =
            classes().that().resideInAPackage(ROOT_PACKAGE + "..infrastructure.persistence..")
                    .should(new ArchCondition<JavaClass>(
                            "다른 도메인의 infrastructure.persistence 클래스에 의존하지 않는다") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            String originDomain = topLevelDomainOf(javaClass);
                            if (originDomain == null) {
                                return;
                            }
                            javaClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                                JavaClass target = dependency.getTargetClass();
                                if (!target.getPackageName().contains(".infrastructure.persistence")) {
                                    return;
                                }
                                String targetDomain = topLevelDomainOf(target);
                                if (targetDomain != null && !targetDomain.equals(originDomain)) {
                                    events.add(SimpleConditionEvent.violated(javaClass,
                                            javaClass.getFullName() + "가 다른 도메인 클래스 "
                                                    + target.getFullName() + "에 의존한다"));
                                }
                            });
                        }
                    });

    /** "com.masiton.restaurant.infrastructure.persistence.X" -> "restaurant" 처럼 최상위 도메인 세그먼트를 뽑는다. */
    @ArchTest
    static final ArchRule crossDomainInfrastructureDependenciesAreForbidden =
            classes().that().resideInAPackage(ROOT_PACKAGE + "..infrastructure..")
                    .should(new ArchCondition<JavaClass>(
                            "does not directly depend on another top-level domain infrastructure class") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            String originDomain = topLevelDomainOf(javaClass);
                            if (originDomain == null) {
                                return;
                            }
                            javaClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                                JavaClass target = dependency.getTargetClass();
                                if (!target.getPackageName().contains(".infrastructure.")) {
                                    return;
                                }
                                String targetDomain = topLevelDomainOf(target);
                                if (targetDomain != null && !targetDomain.equals(originDomain)) {
                                    events.add(SimpleConditionEvent.violated(javaClass,
                                            javaClass.getFullName() + " depends on " + target.getFullName()));
                                }
                            });
                        }
                    });

    private static String topLevelDomainOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = ROOT_PACKAGE + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String remainder = packageName.substring(prefix.length());
        int dotIndex = remainder.indexOf('.');
        return dotIndex == -1 ? remainder : remainder.substring(0, dotIndex);
    }
}
