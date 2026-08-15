package com.lingsql.release;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 从候选制品的构建信息中读取绑定的 Git 提交。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public final class BuildCommitResolver {

    private static final String BUILD_INFO_RESOURCE = "META-INF/build-info.properties";

    private BuildCommitResolver() {
    }

    /**
     * 读取并校验 40 位 Git 提交。
     *
     * @return 构建提交
     */
    public static String requireBuildCommit() {
        try (InputStream input = BuildCommitResolver.class.getClassLoader()
                .getResourceAsStream(BUILD_INFO_RESOURCE)) {
            if (input == null) {
                throw new IllegalArgumentException("候选制品缺少构建信息");
            }
            Properties properties = new Properties();
            properties.load(input);
            String commit = properties.getProperty("build.commit", "").trim();
            if (!commit.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("候选制品未绑定有效 Git 提交");
            }
            return commit;
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取候选制品构建信息失败", ex);
        }
    }
}
