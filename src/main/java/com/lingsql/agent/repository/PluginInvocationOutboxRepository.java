package com.lingsql.agent.repository;

import com.lingsql.agent.entity.PluginInvocationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginInvocationOutboxRepository extends JpaRepository<PluginInvocationOutbox, Long> {
}
