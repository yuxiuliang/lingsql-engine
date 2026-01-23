import {request} from '@/utils/request';

/**
 * 元数据表结构定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface MetadataTableInfo {
  schemaName?: string;
  tableName: string;
  tableComment?: string;
  columns?: Array<{
    columnName: string;
    dataType?: string;
    columnComment?: string;
    primaryKey?: boolean;
  }>;
}

/**
 * 元数据字段详情定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface MetadataFieldView {
  columnName: string;
  dataType?: string;
  columnComment?: string;
  businessComment?: string;
  primaryKey?: boolean;
  isolatedField?: boolean;
}

/**
 * 表详情定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface MetadataTableView {
  schemaName?: string;
  tableName: string;
  tableComment?: string;
  columns: MetadataFieldView[];
}

/**
 * AI 语义解析请求字段。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface AiSuggestField {
  name: string;
  comment?: string;
}

/**
 * AI 语义解析响应。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
export interface AiSuggestResponse {
  suggestions: Array<{columnName: string; comment: string}>;
}

/**
 * 获取缓存 Schema 列表。
 *
 * @param tenantId 租户标识
 * @return 表结构列表
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchMetadataSchema = async (tenantId: string): Promise<MetadataTableInfo[]> => {
  return request<MetadataTableInfo[]>(`/api/v1/admin/metadata/schema?tenantId=${encodeURIComponent(tenantId)}`);
};

/**
 * 获取表字段详情。
 *
 * @param tenantId 租户标识
 * @param tableName 表名
 * @return 表详情
 * @author 余炘洋
 * @date 2026-01-23
 */
export const fetchMetadataTable = async (tenantId: string, tableName: string): Promise<MetadataTableView> => {
  const query = new URLSearchParams({
    tenantId,
    tableName
  });
  return request<MetadataTableView>(`/api/v1/admin/metadata/table?${query.toString()}`);
};

/**
 * 更新字段业务注释。
 *
 * @param tenantId 租户标识
 * @param tableName 表名
 * @param columnName 字段名
 * @param businessComment 业务注释
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateMetadataField = async (
  tenantId: string,
  tableName: string,
  columnName: string,
  businessComment: string
): Promise<string> => {
  return request<string>('/api/v1/admin/metadata/field', {
    method: 'POST',
    body: JSON.stringify({
      tenantId,
      tableName,
      columnName,
      businessComment
    })
  });
};

/**
 * 更新隔离字段。
 *
 * @param tenantId 租户标识
 * @param tableName 表名
 * @param columnName 字段名
 * @param isolated 是否隔离
 * @return 更新结果
 * @author 余炘洋
 * @date 2026-01-23
 */
export const updateIsolationField = async (
  tenantId: string,
  tableName: string,
  columnName: string,
  isolated: boolean
): Promise<string> => {
  return request<string>('/api/v1/admin/metadata/isolated', {
    method: 'PUT',
    body: JSON.stringify({
      tenantId,
      tableName,
      columnName,
      isolated
    })
  });
};

/**
 * AI 语义解析。
 *
 * @param tenantId 租户标识
 * @param tableName 表名
 * @param fields 字段列表
 * @return 解析建议
 * @author 余炘洋
 * @date 2026-01-23
 */
export const aiSuggestMetadata = async (
  tenantId: string,
  tableName: string,
  fields: AiSuggestField[]
): Promise<AiSuggestResponse> => {
  return request<AiSuggestResponse>('/api/v1/admin/metadata/ai-suggest', {
    method: 'POST',
    body: JSON.stringify({
      tenantId,
      tableName,
      fields
    })
  });
};

