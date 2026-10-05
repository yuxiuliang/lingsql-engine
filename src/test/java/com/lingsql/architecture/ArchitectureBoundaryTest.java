package com.lingsql.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * 架构边界校验：Agent 编排层不得直接依赖 Web 控制层，插件实现不得直接依赖编排实现。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@AnalyzeClasses(packages = "com.lingsql")
class ArchitectureBoundaryTest {

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule no_agent_impl_depends_on_web =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("..agent..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.lingsql.web..");

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule no_plugin_depends_on_executor =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("..agent.plugin..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..executor..");
}
