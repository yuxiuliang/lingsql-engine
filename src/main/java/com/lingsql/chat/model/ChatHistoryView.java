package com.lingsql.chat.model;

import com.lingsql.chat.entity.ChatHistory;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 聊天历史记录视图对象。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class ChatHistoryView {

    private Long id;
    private String conversationId;
    private String question;
    private String answer;
    private String sql;
    private LocalDateTime createTime;

    /**
     * 构建视图对象。
     *
     * @param history 历史记录
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static ChatHistoryView from(ChatHistory history) {
        ChatHistoryView view = new ChatHistoryView();
        view.setId(history.getId());
        view.setConversationId(resolveConversationId(history));
        view.setQuestion(resolveConversationTitle(history));
        view.setAnswer(history.getAnswer());
        view.setSql(history.getSqlText());
        view.setCreateTime(history.getCreateTime());
        return view;
    }

    private static String resolveConversationId(ChatHistory history) {
        if (StringUtils.hasText(history.getConversationId())) {
            return history.getConversationId();
        }
        return "legacy-" + history.getId();
    }

    private static String resolveConversationTitle(ChatHistory history) {
        if (StringUtils.hasText(history.getConversationTitle())) {
            return history.getConversationTitle();
        }
        return history.getQuestion();
    }
}

