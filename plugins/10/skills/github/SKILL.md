---
name: github
 description: GitHub仓库管理技能，用于创建仓库、推送文件、管理代码等操作
---

# GitHub 仓库管理技能

版本 v1.0 | 2026-05-03

## 一、创建仓库

1. create_repo(name, description, is_private)
2. name用kebab-case格式
3. 默认private=true保护隐私

## 二、推送文件

1. push_files(repo, message, files)
2. owner填chloemeadow0-code
3. branch默认main
4. 多个文件放在一个数组里一次推送

## 三、读取文件

1. get_file(repo, path, branch)
2. 读取仓库中的文件内容

## 四、删除文件

1. delete_files(repo, message, paths)
2. paths是文件路径数组

## 五、查看仓库

1. list_files(repo, path, recursive)
2. list_repos查看所有仓库
3. get_file读取具体文件

## 六、禁忌

不推送敏感信息！token、密码不能提交！
不删除master/main分支！