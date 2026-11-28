import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { getMcpConfig, getMcpTools, getMcpLogs } from '../src/api/mcp'

vi.mock('@/utils/request', () => ({ default: vi.fn() }))

describe('MCP 管理接口', () => {
  beforeEach(() => {
    request.mockReset()
    request.mockResolvedValue({ data: {} })
  })

  it('读取连接配置和工具目录', async () => {
    await getMcpConfig()
    await getMcpTools()

    expect(request).toHaveBeenNthCalledWith(1, { url: '/gen/mcp/config', method: 'get' })
    expect(request).toHaveBeenNthCalledWith(2, { url: '/gen/mcp/tools', method: 'get' })
  })

  it('提交日志筛选和分页参数', async () => {
    const query = { pageNum: 2, pageSize: 20, toolName: 'codegen_pipeline_run', status: 'FAILED' }
    await getMcpLogs(query)

    expect(request).toHaveBeenCalledWith({ url: '/gen/mcp/logs', method: 'post', data: query })
  })
})
