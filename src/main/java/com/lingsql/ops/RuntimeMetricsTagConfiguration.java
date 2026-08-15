package com.lingsql.ops;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/**
 * 为运行指标绑定不可变发布身份，避免不同候选的指标被聚合为同一证据。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Configuration
public class RuntimeMetricsTagConfiguration {

    private static final String UNSET = "UNSET";
    private static final Pattern BUILD_COMMIT_PATTERN = Pattern.compile("[0-9a-f]{40}|local|UNSET");
    private static final Pattern SLOT_PATTERN = Pattern.compile("blue|green|standalone|UNSET");
    private static final Pattern RELEASE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}|UNSET");

    /**
     * 将制品提交、部署槽位和发布编号固化为所有 Micrometer 指标的公共标签。
     *
     * @param buildPropertiesProvider 制品构建信息
     * @param expectedBuildCommit 启动器声明的制品提交
     * @param slot 部署槽位
     * @param releaseId 发布编号
     * @return 指标注册表定制器
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> runtimeIdentityMetricsTags(
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            @Value("${lingsql.runtime.build-commit:UNSET}") String expectedBuildCommit,
            @Value("${lingsql.runtime.slot:UNSET}") String slot,
            @Value("${lingsql.runtime.release-id:UNSET}") String releaseId) {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        String artifactCommit = buildProperties == null ? UNSET : normalize(buildProperties.get("commit"));
        String normalizedExpectedCommit = normalize(expectedBuildCommit);
        String normalizedSlot = normalize(slot);
        String normalizedReleaseId = normalize(releaseId);

        requireMatch("制品提交", artifactCommit, BUILD_COMMIT_PATTERN);
        requireMatch("启动器声明的制品提交", normalizedExpectedCommit, BUILD_COMMIT_PATTERN);
        requireMatch("部署槽位", normalizedSlot, SLOT_PATTERN);
        requireMatch("发布编号", normalizedReleaseId, RELEASE_ID_PATTERN);
        if (!UNSET.equals(normalizedExpectedCommit) && !normalizedExpectedCommit.equals(artifactCommit)) {
            throw new IllegalStateException("启动器声明的制品提交与 JAR 构建信息不一致");
        }

        return registry -> registry.config().commonTags(
                "build_commit", artifactCommit,
                "slot", normalizedSlot,
                "release_id", normalizedReleaseId
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? UNSET : value.trim();
    }

    private static void requireMatch(String name, String value, Pattern pattern) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalStateException(name + "格式非法");
        }
    }
}
