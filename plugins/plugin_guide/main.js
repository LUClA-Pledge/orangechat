// OrangeChat 插件开发指南 - 让 AI 掌握插件开发知识

function get_plugin_docs(params) {
  var topic = (params.topic || "quickstart").toLowerCase();
  var docs = {
    quickstart: QUICKSTART,
    manifest: MANIFEST_DOC,
    mainjs: MAINJS_DOC,
    sandbox: SANDBOX_DOC,
    memory_bank: MEMORY_BANK_DOC,
    hooks: HOOKS_DOC,
    declarative_ui: UI_DOC,
    ui_components: UI_COMPONENTS_DOC,
    ui_actions: UI_ACTIONS_DOC,
    ui_queries: UI_QUERIES_DOC,
    config: CONFIG_DOC,
    prompt: PROMPT_DOC,
    full_spec: FULL_SPEC
  };
  var content = docs[topic];
  if (!content) {
    content = "未知主题: " + topic + "\n可用主题: quickstart, manifest, mainjs, sandbox, memory_bank, hooks, declarative_ui, ui_components, ui_actions, ui_queries, config, prompt, full_spec";
  }
  return { success: true, topic: topic, content: content };
}

var QUICKSTART = "# OrangeChat 插件快速开始\n\n## 插件是什么？\n插件是扩展 AI 能力的模块，让 AI 可以调用外部工具、获取实时数据、访问记忆库。用户只需把 manifest.json + main.js 打包成 zip 导入 App 即可。\n\n## 最简插件示例\n\n### manifest.json\n```json\n{\n  \"id\": \"com.example.hello\",\n  \"name\": \"打招呼\",\n  \"description\": \"简单打招呼插件\",\n  \"version\": \"1.0.0\",\n  \"author\": \"YourName\",\n  \"icon\": \"👋\",\n  \"entry\": \"main.js\",\n  \"tools\": [{\n    \"name\": \"say_hello\",\n    \"description\": \"向用户打招呼\",\n    \"parameters\": [\n      {\"name\": \"name\", \"type\": \"string\", \"required\": true, \"description\": \"用户名字\"}\n    ]\n  }]\n}\n```\n\n### main.js\n```javascript\nfunction say_hello(params) {\n  var name = params.name || 'World';\n  return { success: true, message: 'Hello, ' + name + '!' };\n}\nexports.say_hello = say_hello;\n```\n\n## 打包导入\n1. 创建文件夹，放入 manifest.json 和 main.js\n2. 压缩为 zip 文件\n3. 在 App 插件管理页导入 zip\n\n## 关键规则\n- manifest.tools[].name 必须与 exports.xxx 完全一致\n- fetch 是同步的，返回类浏览器 Response 对象，不需要 await\n- 不要用 async/await（沙箱会自动移除这些关键字）\n- 返回值建议包含 success 字段\n- 可通过 memoryBank API 访问记忆库\n- 可通过 hooks 监听消息事件和定时任务\n\n## 实用插件示例：净化备份\n\n以下是一个实际可用的插件示例，用于净化备份数据（去除敏感信息，保留记忆设定）：\n\n### manifest.json\n```json\n{\n  \"id\": \"com.orangechat.plugin.purify_backup\",\n  \"name\": \"净化备份\",\n  \"description\": \"去除敏感信息，保留记忆和设定\",\n  \"version\": \"1.0.0\",\n  \"author\": \"OrangeChat\",\n  \"icon\": \"🛡️\",\n  \"entry\": \"main.js\",\n  \"tools\": [{\n    \"name\": \"purify_backup\",\n    \"description\": \"净化备份数据，去除敏感信息\",\n    \"parameters\": [\n      {\"name\": \"action\", \"type\": \"string\", \"required\": true, \"description\": \"操作类型：purify_settings\"},\n      {\"name\": \"settings_json\", \"type\": \"string\", \"required\": false, \"description\": \"settings.json 的 JSON 字符串\"},\n      {\"name\": \"options\", \"type\": \"string\", \"required\": false, \"description\": \"净化选项JSON字符串\"}\n    ]\n  }]\n}\n```\n\n### main.js 核心逻辑\n```javascript\nfunction purify_backup(params) {\n  var action = params.action || \"purify_settings\";\n  if (action === \"purify_settings\") {\n    return purifySettingsData(params.settings_json, params.options);\n  }\n  return { success: false, error: \"未知操作类型\" };\n}\n\nfunction purifySettingsData(settingsJson, optionsJson) {\n  // 解析 JSON\n  var settings = JSON.parse(settingsJson);\n  var options = optionsJson ? JSON.parse(optionsJson) : {};\n  \n  // 深拷贝\n  var purified = JSON.parse(JSON.stringify(settings));\n  \n  // 去除 API Keys\n  if (purified.providers) {\n    purified.providers = purified.providers.map(function(p) {\n      p.apiKey = \"\";\n      p.baseUrl = \"\";\n      return p;\n    });\n  }\n  \n  // 去除个人信息\n  if (purified.displaySetting) {\n    purified.displaySetting.userNickname = \"用户\";\n    purified.displaySetting.userAvatar = null;\n  }\n  \n  // 去除 WebDAV/S3 配置\n  if (purified.webDavConfig) {\n    purified.webDavConfig = { url: \"\", username: \"\", password: \"\", path: \"\", items: [] };\n  }\n  if (purified.s3Config) {\n    purified.s3Config = { endpoint: \"\", accessKeyId: \"\", secretAccessKey: \"\", bucket: \"\", items: [] };\n  }\n  \n  // 去除 MCP 服务器配置\n  if (purified.mcpServers) {\n    purified.mcpServers = [];\n  }\n  \n  // Assistant 设定保留但清理路径\n  if (purified.assistants) {\n    purified.assistants = purified.assistants.map(function(a) {\n      if (a.avatar && a.avatar.url && a.avatar.url.startsWith(\"file://\")) {\n        a.avatar = { type: \"me.rerere.rikkahub.data.model.Avatar.Dummy\" };\n      }\n      if (a.background && a.background.startsWith(\"file://\")) {\n        a.background = null;\n      }\n      if (a.mcpServers) a.mcpServers = [];\n      return a;\n    });\n  }\n  \n  return {\n    success: true,\n    purified_json: JSON.stringify(purified, null, 2),\n    message: \"净化完成！请复制 purified_json 内容保存为 settings.json\"\n  };\n}\n\nexports.purify_backup = purify_backup;\n```\n";

var MANIFEST_DOC = "# manifest.json 完整规范\n\n## 必需字段\n- id: 唯一标识，反向域名格式如 com.example.plugin.name\n- name: 显示名称\n- description: 插件描述\n- version: 版本号如 1.0.0\n- author: 作者\n- icon: emoji 或 URL\n- entry: 入口文件路径，通常为 main.js\n\n## 可选字段\n- tools: 工具定义列表（让AI调用的函数）\n- config: 配置字段列表（用户设置参数）\n- promptTemplate: 提示词模板字符串\n- ui: 声明式UI定义（原生Compose渲染）\n- customPageWebView: WebView页面配置\n- customPage: 内置页面标识（如 \"memory_bank\"）\n- hooks: 事件钩子列表\n\n## tools 工具定义\n```json\n\"tools\": [{\n  \"name\": \"tool_name\",\n  \"description\": \"工具描述\",\n  \"parameters\": [{\n    \"name\": \"param_name\",\n    \"type\": \"string\",\n    \"required\": true,\n    \"description\": \"参数描述\"\n  }]\n}]\n```\n\n## hooks 事件钩子\n```json\n\"hooks\": [{\n  \"event\": \"message_sent\",\n  \"handler\": \"on_message\",\n  \"schedule\": \"0 3 * * *\"\n}]\n```\nevent类型: message_sent, message_received, daily_cron\nschedule仅daily_cron使用，为cron表达式\n\n## UI优先级\nui(声明式) > customPageWebView > customPage\n";

var MAINJS_DOC = "# main.js 编写规范\n\n## 基本结构\n```javascript\nfunction my_tool(params) {\n  var input = params.input || '';\n  var limit = config.max_results || 10;\n  return { success: true, data: '结果' };\n}\nexports.my_tool = my_tool;\n```\n\n## 多个工具\n```javascript\nfunction tool_a(params) { /* ... */ }\nfunction tool_b(params) { /* ... */ }\nexports.tool_a = tool_a;\nexports.tool_b = tool_b;\n```\n\n## 事件钩子处理函数\n```javascript\nfunction on_message(params) {\n  memoryBank.save({ content: params.content });\n  return { success: true };\n}\nexports.on_message = on_message;\n```\n\n## 返回值约定\n- 成功: { success: true, ... }\n- 失败: { success: false, error: '错误信息' }\n\n## 重要规则\n1. fetch是同步的，返回类浏览器Response对象\n2. 不要用async/await\n3. 函数名必须与manifest tools一致\n4. 使用var而非let/const\n5. 内置TextEncoder/TextDecoder/btoa/atob可用\n";

var SANDBOX_DOC = "# 沙箱API参考\n\n## fetch(url) - 同步HTTP GET\n```javascript\nvar response = fetch('https://api.example.com/data');\nif (response.ok) {\n  var data = response.json();\n}\n```\n\n## fetch(url, options) - 同步HTTP请求\n```javascript\nvar response = fetch('https://api.example.com/items', {\n  method: 'POST',\n  body: JSON.stringify({ name: 'test' }),\n  headers: {\n    'Content-Type': 'application/json',\n    'Authorization': 'Bearer ' + config.api_key\n  }\n});\n```\n\n## Response对象\n- response.ok: 是否成功(状态码200-299)\n- response.status: HTTP状态码\n- response.headers: 响应头对象\n- response.text(): 返回响应体文本\n- response.json(): 解析JSON响应体\n\n支持: GET/POST/PUT/DELETE，超时15秒\n\n## config - 用户配置对象\n```javascript\nvar apiKey = config.api_key;\nvar baseUrl = config.base_url;\nvar max = config.max_results || 10;\n```\n\n## 内置polyfill\n- TextEncoder/TextDecoder: UTF-8编解码\n- btoa/atob: Base64编解码\n- console.log/info/warn/error: 输出到Logcat\n";

var MEMORY_BANK_DOC = "# 记忆库API (memoryBank)\n\n插件可通过memoryBank对象访问App的记忆库功能。\n\n## memoryBank.recall(query, count)\n语义搜索回忆相关记忆。\n```javascript\nvar result = memoryBank.recall({\n  query: '用户喜欢的食物',\n  count: 5\n});\n// result: { success: true, memories: [{id, content, type, createdAt, role}, ...] }\n```\n\n## memoryBank.save(content)\n保存手动记忆。\n```javascript\nvar result = memoryBank.save({\n  content: '用户喜欢吃披萨'\n});\n// result: { success: true, id: 123, content: '...' }\n```\n\n## memoryBank.search(keyword, type, limit)\n按关键词搜索记忆。\n```javascript\nvar result = memoryBank.search({\n  keyword: '披萨',\n  type: '',\n  limit: 20\n});\n// result: { success: true, memories: [{id, content, type, createdAt, role}, ...] }\n```\n\n## memoryBank.delete(id)\n删除指定记忆。\n```javascript\nvar result = memoryBank.delete({ id: 123 });\n// result: { success: true, id: 123 }\n```\n";


var HOOKS_DOC = "# \u4e8b\u4ef6\u94a9\u5b50 (hooks)\n\n插件可监听App事件，在特定时机自动执行处理函数。\n\n## 支持的事件\n- message_sent: 用户发送消息时触发\n- message_received: 收到AI回复时触发\n- daily_cron: 定时任务，按cron表达式执行\n\n## 处理函数\nhooks中声明的handler函数必须在main.js中导出。\n";

var UI_DOC = "# \u58f0\u660e\u5f0fUI (ui字段)\n\n通过manifest.json的ui字段声明UI组件，App渲染为原生Compose Material 3界面。\n\n## 结构\nui: { title, queries, actions, components }\n\n可用子主题: ui_components / ui_actions / ui_queries\n";

var UI_COMPONENTS_DOC = "# UI组件参考\n\n支持组件: stats, search_bar, filter_bar, card_grid, card_list, button_row, dialog_form, section, text, empty_state\n";

var UI_ACTIONS_DOC = "# 操作定义 (actions)\n\n操作类型: dataStore_set, dataStore_delete, tool_call, file_write, file_delete, pick_image\n";

var UI_QUERIES_DOC = "# 查询定义 (queries)\n\n查询类型: dataStore_list, dataStore_get, dataStore_search\n";

var CONFIG_DOC = "# 配置字段 (config)\n\n在manifest.json中声明配置字段，用户可在插件设置页填写。\n\n## 字段类型\n- string: 文本输入\n- password: 密码输入\n- number: 数字输入\n- boolean: 开关\n- select: 下拉选择\n";

var PROMPT_DOC = "# 提示词模板 (promptTemplate)\n\n在manifest.json中设置promptTemplate字段，可为插件添加系统提示词。\n";

var FULL_SPEC = "# 完整插件规范\n\n请查询各子主题获取详细信息: quickstart, manifest, mainjs, sandbox, memory_bank, hooks, declarative_ui, ui_components, ui_actions, ui_queries, config, prompt\n";

exports.get_plugin_docs = get_plugin_docs;
