package com.lingsql.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天历史记录实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_chat_history")
public class ChatHistory {

    /**
     * 主键。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户标识。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 用户问题。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    /**
     * 回答内容。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    /**
     * SQL 文本。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "sql_text", columnDefinition = "TEXT")
    private String sqlText;

    /**
     * 原始数据 JSON。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    /**
     * 记录状态：1 正常，0 异常。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
