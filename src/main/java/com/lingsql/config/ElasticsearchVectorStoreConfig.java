package com.lingsql.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 向量存储装配。
 *
 * <p>仅当 {@code lingsql.knowledge.vector.enabled=true} 且配置了 {@code lingsql.knowledge.vector.uris} 时创建
 * VectorStore；未启用时不注册该 Bean，知识检索自动回落到数据库关键词检索。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Slf4j
@Configuration
public class ElasticsearchVectorStoreConfig {

    /**
     * 构建 Elasticsearch 向量存储。
     *
     * @param environment   配置环境
     * @param embeddingModel 可选的嵌入模型提供者（未配置模型时不装配）
     * @return VectorStore，未启用时返回 null
     */
    @Bean
    public VectorStore elasticsearchVectorStore(Environment environment, ObjectProvider<EmbeddingModel> embeddingModel) {
        boolean enabled = Boolean.parseBoolean(environment.getProperty("lingsql.knowledge.vector.enabled", "false"));
        String uris = environment.getProperty("lingsql.knowledge.vector.uris", "");
        if (!enabled || uris.isBlank()) {
            log.info("ES 向量检索未启用，知识检索将使用数据库关键词检索");
            return null;
        }
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            log.warn("已启用 ES 向量检索但未找到 EmbeddingModel，跳过装配");
            return null;
        }
        List<URI> nodes = new ArrayList<>();
        for (String raw : uris.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://" + value;
            }
            nodes.add(URI.create(value));
        }
        if (nodes.isEmpty()) {
            log.warn("lingsql.knowledge.vector.uris 未提供有效地址，跳过 ES 向量存储装配");
            return null;
        }
        Rest5Client restClient = Rest5Client.builder(nodes.toArray(new URI[0])).build();
        log.info("ES 向量存储已启用，节点数={}", nodes.size());
        return ElasticsearchVectorStore.builder(restClient, model).build();
    }
}