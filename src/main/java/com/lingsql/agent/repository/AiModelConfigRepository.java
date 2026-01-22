package com.lingsql.agent.repository;

import com.lingsql.agent.model.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {
    // 查询所有启用的模型，用于负载均衡
    List<AiModelConfig> findByIsActive(Integer isActive);

    // 查询需要自动恢复的模型
    List<AiModelConfig> findByIsActiveAndAutoRecover(Integer isActive, Integer autoRecover);
}
