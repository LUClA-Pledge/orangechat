// 净化备份插件 - 在内存中去除敏感信息，保留记忆和设定
// 由于插件沙箱限制，无法直接读写文件系统，需要用户传入 JSON 数据

/**
 * 净化备份数据
 * @param {Object} params - 参数
 * @param {string} params.action - 操作类型：purify_settings(净化设置)
 * @param {string} params.settings_json - settings.json 的 JSON 字符串
 * @param {string} params.options - 净化选项JSON字符串
 */
function purify_backup(params) {
  var action = params.action || "purify_settings";
  
  if (action === "purify_settings") {
    return purifySettingsData(params.settings_json, params.options);
  } else {
    return { success: false, error: "未知操作类型: " + action + "。支持的操作: purify_settings" };
  }
}

/**
 * 净化 Settings JSON 数据
 * @param {string} settingsJson - settings.json 的 JSON 字符串
 * @param {string} optionsJson - 净化选项JSON字符串
 */
function purifySettingsData(settingsJson, optionsJson) {
  if (!settingsJson) {
    return { 
      success: false, 
      error: "缺少 settings_json 参数。请将 settings.json 的内容作为字符串传入。" 
    };
  }
  
  var settings;
  try {
    settings = JSON.parse(settingsJson);
  } catch (e) {
    return { success: false, error: "settings_json 格式错误: " + e.message };
  }
  
  var options = {};
  try {
    if (optionsJson) {
      options = JSON.parse(optionsJson);
    }
  } catch (e) {
    return { success: false, error: "净化选项格式错误: " + e.message };
  }
  
  // 默认选项
  var defaultOptions = {
    remove_api_keys: true,
    remove_personal_info: true,
    keep_conversations: true,
    keep_assistants: true,
    keep_lorebooks: true,
    keep_mode_injections: true,
    keep_skills: true,
    remove_mcp_servers: true,
    remove_webdav_config: true,
    remove_s3_config: true,
    anonymize_user_data: true
  };
  
  // 合并选项
  for (var key in defaultOptions) {
    if (options[key] === undefined) {
      options[key] = defaultOptions[key];
    }
  }
  
  try {
    // 执行净化
    var purified = purifySettingsObject(settings, options);
    
    // 生成净化摘要
    var summary = generatePurifySummary(purified, settings, options);
    
    // 返回净化后的 JSON 字符串
    var purifiedJson = JSON.stringify(purified, null, 2);
    
    return {
      success: true,
      purified_settings: purified,
      purified_json: purifiedJson,
      summary: summary,
      message: "净化完成！已去除敏感信息，保留记忆和设定。请复制 purified_json 字段的内容保存为 settings.json 文件。"
    };
    
  } catch (e) {
    return { success: false, error: "净化失败: " + e.message };
  }
}

/**
 * 净化 Settings 对象
 * @param {Object} settings - 原始设置
 * @param {Object} options - 净化选项
 */
function purifySettingsObject(settings, options) {
  var purified = JSON.parse(JSON.stringify(settings));
  
  // 1. 去除 API Keys
  if (options.remove_api_keys && purified.providers) {
    purified.providers = purified.providers.map(function(provider) {
      var p = JSON.parse(JSON.stringify(provider));
      if (p.apiKey) p.apiKey = "";
      if (p.baseUrl) p.baseUrl = "";
      if (p.chatCompletionsPath) p.chatCompletionsPath = "";
      if (p.customHeaders) p.customHeaders = [];
      if (p.customBodies) p.customBodies = [];
      return p;
    });
  }
  
  // 2. 去除个人信息
  if (options.remove_personal_info) {
    if (purified.displaySetting) {
      if (purified.displaySetting.userNickname) {
        purified.displaySetting.userNickname = "用户";
      }
      if (purified.displaySetting.userAvatar) {
        purified.displaySetting.userAvatar = null;
      }
    }
  }
  
  // 3. 去除 WebDAV 配置
  if (options.remove_webdav_config && purified.webDavConfig) {
    purified.webDavConfig = {
      url: "",
      username: "",
      password: "",
      path: "rikkahub_backups",
      items: []
    };
  }
  
  // 4. 去除 S3 配置
  if (options.remove_s3_config && purified.s3Config) {
    purified.s3Config = {
      endpoint: "",
      accessKeyId: "",
      secretAccessKey: "",
      bucket: "",
      region: "auto",
      pathStyle: true,
      items: []
    };
  }
  
  // 5. 去除 MCP 服务器配置
  if (options.remove_mcp_servers && purified.mcpServers) {
    purified.mcpServers = [];
  }
  
  // 6. 匿名化用户数据
  if (options.anonymize_user_data) {
    if (purified.searchServices) {
      purified.searchServices = [];
    }
    if (purified.searchCommonOptions) {
      purified.searchCommonOptions = { resultSize: 10 };
    }
    if (purified.searchServiceSelected !== undefined) {
      purified.searchServiceSelected = 0;
    }
    
    // 去除 TTS 配置中的敏感信息
    if (purified.ttsProviders) {
      purified.ttsProviders = purified.ttsProviders.map(function(t) {
        var tts = JSON.parse(JSON.stringify(t));
        if (tts.apiKey) tts.apiKey = "";
        if (tts.baseUrl) tts.baseUrl = "";
        return tts;
      });
    }
    
    // 去除 ASR 配置中的敏感信息
    if (purified.asrProviders) {
      purified.asrProviders = purified.asrProviders.map(function(a) {
        var asr = JSON.parse(JSON.stringify(a));
        if (asr.apiKey) asr.apiKey = "";
        if (asr.websocketUrl) asr.websocketUrl = "";
        return asr;
      });
    }
  }
  
  // 7. 保留但清理 Assistant 设定
  if (options.keep_assistants && purified.assistants) {
    purified.assistants = purified.assistants.map(function(assistant) {
      var a = JSON.parse(JSON.stringify(assistant));
      // 去除头像中的文件路径
      if (a.avatar && a.avatar.url && a.avatar.url.startsWith("file://")) {
        a.avatar = { type: "me.rerere.rikkahub.data.model.Avatar.Dummy" };
      }
      // 去除背景图片路径
      if (a.background && a.background.startsWith("file://")) {
        a.background = null;
      }
      // 去除 MCP 服务器引用
      if (a.mcpServers) {
        a.mcpServers = [];
      }
      return a;
    });
  }
  
  return purified;
}

/**
 * 生成净化摘要
 */
function generatePurifySummary(purified, original, options) {
  var summary = [];
  
  if (options.remove_api_keys) {
    summary.push("已去除所有 Provider 的 API Keys");
  }
  if (options.remove_personal_info) {
    summary.push("已去除用户昵称和头像");
  }
  if (options.remove_webdav_config) {
    summary.push("已去除 WebDAV 配置");
  }
  if (options.remove_s3_config) {
    summary.push("已去除 S3 配置");
  }
  if (options.remove_mcp_servers) {
    summary.push("已去除 MCP 服务器配置");
  }
  if (options.keep_assistants && original.assistants) {
    summary.push("保留了 " + original.assistants.length + " 个 Assistant 设定");
  }
  if (options.keep_lorebooks && original.lorebooks) {
    summary.push("保留了 " + original.lorebooks.length + " 个 Lorebook");
  }
  if (options.keep_mode_injections && original.modeInjections) {
    summary.push("保留了 " + original.modeInjections.length + " 个 Mode Injection");
  }
  
  return summary;
}

exports.purify_backup = purify_backup;