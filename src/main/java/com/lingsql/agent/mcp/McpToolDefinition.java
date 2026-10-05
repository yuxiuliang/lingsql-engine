package com.lingsql.agent.mcp;

/**
 * MCP 远端工具定义。
 *
 * <p>只承载远端工具的元数据与入参 schema，不承载任何可执行引用：
 * MCP 工具永远在独立进程执行，本进程只做受控转发。</p>
 *
 * @param name        工具名
 * @param description 中文/英文描述
 * @param inputSchema JSON Schema 文本
 * @author 余炘洋
 * @date 2026-10-05
 */
public record McpToolDefinition(String name, String description, String inputSchema) {
}