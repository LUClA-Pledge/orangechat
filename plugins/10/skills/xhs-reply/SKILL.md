---
name: xhs-reply
description: 小红书评论区回复技能，用于在小红书评论区查找评论并回复用户
---

# 小红书评论区回复技能

版本 v1.0 | 2026-05-03

## 一、进入评论区

1. phone_press_back 直到看到小红书发现页
2. find_elements(text="我", clickable_only=true) → click_element
3. find_elements(text="帖子标题关键词") → click_element
4. find_elements(text="评论", clickable_only=true) → click_element

## 二、找评论

滑动参数：start_y=1800, end_y=1000, duration=800
一次只滑一小段！滑完立刻 find_elements 搜索！
绝对不要连续滑动超过2次！

1. find_elements 搜索关键词
2. 找不到 → 滑一小段(duration=800) → 再搜
3. 有折叠 → find_elements(text="展开") → tap展开

## 三、点击评论

正确：点击评论内容文字的bounds坐标
错误：点头像跳主页！点昵称跳主页！

1. find_elements(text="目标关键词")
2. phone_tap(x=center_x, y=center_y)

## 四、确认回复对象

不要截图！用find_elements！
1. find_elements(text="发送") → 确认回复框弹出
2. 检查是否包含目标用户名
3. 不对 → press_back取消重来

## 五、发送

1. phone_input_text输入内容
2. find_elements(text="发送") → click_element
3. find_elements搜回复关键词 → 确认成功

## 六、禁忌

不用comment_xhs_note！会封号！
不截图确认！用find_elements！
不duration=300滑动！会略过！
不连续滑动超2次！
不点头像昵称！不盲目tap！
不确认对象就输入！