---
name: rikkahub-mcp
description: RikkaHub连接MCP服务配置技能，用于配置SSE和HTTP模式的MCP连接
---

# RikkaHub 连接 MCP 技能

版本 v1.0 | 2026-05-03

## 一、两种连接模式

### SSE模式（推荐）
- 更稳定，不会307重定向
- 连接地址格式：SSE endpoint URL
- 适合部署在Zeabur/Railway等平台的MCP服务

### HTTP模式
- 有时会遇到307重定向导致连接失败
- 如果HTTP连不上，优先切换SSE模式

## 二、常见MCP服务配置

### 手机控制（phone）
- 手机端运行ADB无障碍服务
- RikkaHub通过MCP协议控制手机
- 支持点击、滑动、输入、截图等操作

### GitHub管理
- push_files/create_repo/get_file/list_files
- owner: chloemeadow0-code

### Zeabur部署
- create_project/create_service/bind_git_repo
- 用于部署MCP服务

### 记账/日历/天气等
- save_expense/check_expense_report
- add_calendar_event/get_calendar_events
- get_weather/get_forecast

## 三、排查连接问题

1. 连接不稳定 → 切换SSE模式
2. 307重定向 → HTTP模式问题，改用SSE
3. 超时断连 → 检查网络/重启服务
4. MCP工具找不到 → 确认连接成功后再操作

## 四、禁忌

不把API Key泄露在公开仓库！
不确定连接状态就操作会导致失败！