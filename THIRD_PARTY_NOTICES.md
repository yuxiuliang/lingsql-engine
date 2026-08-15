# 第三方组件声明

LingSQL Engine 使用若干第三方开源组件。第三方组件不受根目录 `LICENSE` 的商业专有条款替代，仍分别适用其原始许可证。

每个正式发布包必须包含：

- 后端 CycloneDX SBOM；
- 前端 CycloneDX SBOM；
- 与发布 Git commit 和 JAR SHA-256 绑定的 `release-supply-chain-manifest.json`；
- 由 SBOM 生成的 `THIRD_PARTY_COMPONENTS.md` 组件与许可证索引；
- 本声明与根目录 `LICENSE`。

组件清单以对应发布包中的 SBOM 为准，不以本文档中的示例或开发分支依赖为准。许可证文本、版权声明及上游 `NOTICE` 如有要求，交付方必须随对应组件一并保留，不得删除或改写。

当前许可证策略允许的许可证标识记录在 `docs/supply-chain-license-policy.json`。策略允许仅表示已完成当前产品分发场景下的初步工程审查，不替代律师对具体交付方式、动态链接、修改、再分发、SaaS 或嵌入式交付的法律判断。出现新许可证、无许可证组件、强 Copyleft、非商业限制或自定义条款时，发布门禁必须失败并转人工复核。

发现声明遗漏或许可证冲突时，应停止发布并通过项目安全渠道报告，不得通过公开 Issue 提交客户数据、密钥或未披露漏洞细节。
