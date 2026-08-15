#!/usr/bin/env node

import { readFileSync } from "node:fs";

function fail(file, index, message, statement) {
  const compact = statement.replace(/\s+/g, " ").trim().slice(0, 240);
  process.stderr.write(`[ERROR] ${file} 第 ${index} 条 SQL ${message}: ${compact}\n`);
  process.exitCode = 2;
}

function splitStatements(source) {
  const statements = [];
  let current = "";
  let state = "normal";

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    const next = source[index + 1] ?? "";

    if (state === "line-comment") {
      if (char === "\n") {
        state = "normal";
        current += " ";
      }
      continue;
    }
    if (state === "block-comment") {
      if (char === "*" && next === "/") {
        state = "normal";
        current += " ";
        index += 1;
      }
      continue;
    }
    if (state === "single-quote" || state === "double-quote") {
      const delimiter = state === "single-quote" ? "'" : '"';
      if (char === "\\" && next) {
        index += 1;
      } else if (char === delimiter) {
        if (next === delimiter) {
          index += 1;
        } else {
          state = "normal";
        }
      }
      continue;
    }
    if (state === "backtick") {
      current += char;
      if (char === "`" && next === "`") {
        current += next;
        index += 1;
      } else if (char === "`") {
        state = "normal";
      }
      continue;
    }

    if (char === "-" && next === "-") {
      state = "line-comment";
      index += 1;
    } else if (char === "#") {
      state = "line-comment";
    } else if (char === "/" && next === "*") {
      state = "block-comment";
      index += 1;
    } else if (char === "'") {
      state = "single-quote";
      current += "''";
    } else if (char === '"') {
      state = "double-quote";
      current += '""';
    } else if (char === "`") {
      state = "backtick";
      current += char;
    } else if (char === ";") {
      if (current.trim()) statements.push(current.trim());
      current = "";
    } else {
      current += char;
    }
  }

  if (state !== "normal" && state !== "line-comment") {
    throw new Error("SQL 包含未闭合的字符串、标识符或块注释");
  }
  if (current.trim()) statements.push(current.trim());
  return statements;
}

function splitTopLevelClauses(value) {
  const clauses = [];
  let current = "";
  let depth = 0;
  let quote = "";
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    const next = value[index + 1] ?? "";
    if (quote) {
      current += char;
      if (char === "\\" && next) {
        current += next;
        index += 1;
      } else if (char === quote) {
        if (next === quote) {
          current += next;
          index += 1;
        } else {
          quote = "";
        }
      }
      continue;
    }
    if (char === "'" || char === '"' || char === "`") {
      quote = char;
      current += char;
    } else if (char === "(") {
      depth += 1;
      current += char;
    } else if (char === ")") {
      depth -= 1;
      if (depth < 0) throw new Error("ALTER TABLE 括号不平衡");
      current += char;
    } else if (char === "," && depth === 0) {
      clauses.push(current.trim());
      current = "";
    } else {
      current += char;
    }
  }
  if (depth !== 0 || quote) throw new Error("ALTER TABLE 结构无法安全解析");
  if (current.trim()) clauses.push(current.trim());
  return clauses;
}

function isSafeAddClause(clause) {
  if (/^ADD\s+(?:UNIQUE\s+)?(?:INDEX|KEY)\b/i.test(clause)) return true;
  if (/^ADD\s+(?:CONSTRAINT\s+(?:`[^`]+`|[A-Za-z0-9_$]+)\s+)?(?:PRIMARY\s+KEY|FOREIGN\s+KEY|CHECK)\b/i.test(clause)) {
    return true;
  }
  const column = clause.match(/^ADD\s+(?:COLUMN\s+)?(?:`[^`]+`|[A-Za-z_$][A-Za-z0-9_$]*)\s+(.+)$/is);
  if (!column) return false;
  const definition = column[1];
  if (/\b(?:FIRST|AFTER)\b/i.test(definition)) return false;
  if (/\bNOT\s+NULL\b/i.test(definition) && !/\bDEFAULT\b/i.test(definition)) return false;
  return !/\b(?:AUTO_INCREMENT|PRIMARY\s+KEY|UNIQUE\b)\b/i.test(definition);
}

function validateStatement(file, index, statement) {
  const normalized = statement.replace(/\s+/g, " ").trim();
  if (/\b(?:DROP|RENAME|CHANGE|TRUNCATE)\b/i.test(normalized)
      || /\bMODIFY\b/i.test(normalized)
      || /\bALTER\s+COLUMN\b/i.test(normalized)) {
    fail(file, index, "包含破坏性或列类型变更", normalized);
    return;
  }
  if (/^CREATE\s+TABLE\b/i.test(normalized)) {
    if (/\bAS\s+SELECT\b/i.test(normalized)) fail(file, index, "包含无法自动判定的数据复制", normalized);
    return;
  }
  if (/^CREATE\s+(?:UNIQUE\s+)?INDEX\b/i.test(normalized)) return;

  const alter = normalized.match(/^ALTER\s+TABLE\s+(?:`[^`]+`|[A-Za-z0-9_$]+)(?:\.(?:`[^`]+`|[A-Za-z0-9_$]+))?\s+(.+)$/is);
  if (alter) {
    let clauses;
    try {
      clauses = splitTopLevelClauses(alter[1]);
    } catch (error) {
      fail(file, index, error.message, normalized);
      return;
    }
    if (clauses.length === 0 || clauses.some((clause) => !isSafeAddClause(clause))) {
      fail(file, index, "不是可证明向前兼容的纯 ADD 变更", normalized);
    }
    return;
  }
  fail(file, index, "不在自动发布允许的语句白名单内", normalized);
}

const files = process.argv.slice(2);
if (files.length === 0) {
  process.stderr.write("[ERROR] 至少需要一个待扫描迁移文件\n");
  process.exit(1);
}

for (const file of files) {
  let statements;
  try {
    statements = splitStatements(readFileSync(file, "utf8"));
  } catch (error) {
    process.stderr.write(`[ERROR] 无法解析迁移 ${file}: ${error.message}\n`);
    process.exitCode = 2;
    continue;
  }
  if (statements.length === 0) {
    process.stderr.write(`[ERROR] 待执行迁移为空: ${file}\n`);
    process.exitCode = 2;
    continue;
  }
  statements.forEach((statement, index) => validateStatement(file, index + 1, statement));
}

if (!process.exitCode) process.stdout.write(`[INFO] ${files.length} 个 pending Flyway 迁移通过向前兼容扫描\n`);
