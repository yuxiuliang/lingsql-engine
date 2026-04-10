/**
 * MySQL 默认驱动类。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
export const DEFAULT_MYSQL_DRIVER_CLASS = 'com.mysql.cj.jdbc.Driver';

/**
 * MySQL 默认端口。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
export const DEFAULT_MYSQL_PORT = '3306';

/**
 * 新建数据源时默认带出的连接参数。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
export const DEFAULT_MYSQL_PARAMS = 'useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai';

const MYSQL_JDBC_PREFIX = 'jdbc:mysql://';
const JDBC_SCHEME_PREFIX = 'jdbc:';

/**
 * MySQL 连接表单定义。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
export interface MysqlConnectionForm {
  host: string;
  port: string;
  databaseName: string;
  params: string;
}

/**
 * JDBC 地址解析结果。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
export interface MysqlJdbcParseResult extends MysqlConnectionForm {
  manualMode: boolean;
}

/**
 * 创建空的 MySQL 连接表单。
 *
 * @return 表单初始值
 * @author 余炘洋
 * @date 2026-04-10
 */
export const createEmptyMysqlConnectionForm = (): MysqlConnectionForm => ({
  host: '',
  port: DEFAULT_MYSQL_PORT,
  databaseName: '',
  params: DEFAULT_MYSQL_PARAMS
});

/**
 * 解析 JDBC 地址并回填简化表单。
 *
 * @param jdbcUrl JDBC 地址
 * @return 解析结果
 * @author 余炘洋
 * @date 2026-04-10
 */
export const parseMysqlJdbcUrl = (jdbcUrl?: string): MysqlJdbcParseResult => {
  const raw = jdbcUrl?.trim() || '';
  if (!raw) {
    return {
      ...createEmptyMysqlConnectionForm(),
      manualMode: false
    };
  }
  if (!raw.toLowerCase().startsWith(MYSQL_JDBC_PREFIX)) {
    return {
      ...createEmptyMysqlConnectionForm(),
      manualMode: true
    };
  }
  try {
    const parsed = new URL(raw.substring(JDBC_SCHEME_PREFIX.length));
    return {
      host: parsed.hostname || '',
      port: parsed.port || DEFAULT_MYSQL_PORT,
      databaseName: decodeURIComponent(parsed.pathname.replace(/^\/+/, '')),
      params: parsed.search ? parsed.search.replace(/^\?/, '') : '',
      manualMode: false
    };
  } catch {
    return {
      ...createEmptyMysqlConnectionForm(),
      manualMode: true
    };
  }
};

/**
 * 根据简化表单拼装 MySQL JDBC 地址。
 *
 * @param form MySQL 连接表单
 * @return JDBC 地址
 * @author 余炘洋
 * @date 2026-04-10
 */
export const buildMysqlJdbcUrl = (form: MysqlConnectionForm): string => {
  const host = normalizeMysqlHost(form.host);
  const databaseName = form.databaseName.trim().replace(/^\/+/, '');
  if (!host || !databaseName) {
    return '';
  }
  const port = form.port.trim() || DEFAULT_MYSQL_PORT;
  const params = form.params.trim().replace(/^\?+/, '');
  const baseUrl = `${MYSQL_JDBC_PREFIX}${host}:${port}/${encodeURIComponent(databaseName)}`;
  return params ? `${baseUrl}?${params}` : baseUrl;
};

/**
 * 规范化主机地址，兼容 IPv6 写法。
 *
 * @param host 主机地址
 * @return 可用于 JDBC 的主机
 * @author 余炘洋
 * @date 2026-04-10
 */
const normalizeMysqlHost = (host: string): string => {
  const normalized = host.trim();
  if (!normalized) {
    return '';
  }
  if (normalized.includes(':') && !normalized.startsWith('[') && !normalized.endsWith(']')) {
    return `[${normalized}]`;
  }
  return normalized;
};
