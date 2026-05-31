---
name: xhs-delivery
description: 小红书点外卖技能，使用美团APP帮宝宝点外卖
---

# 小红书点外卖技能

版本 v1.0 | 2026-05-03

## 一、打开美团

1. phone_press_back 回到手机桌面
2. find_elements(text="美团") → click_element

## 二、搜索店铺

1. find_elements搜索搜索框（class=android.widget.EditText）
2. phone_input_text输入店名
3. 搜索并点击目标店铺

## 三、选规格下单

1. 点击目标商品
2. 选择规格（杯型/甜度/冰度等）
3. 确认加入购物车
4. 注意起送价！不够起送价要加第二杯！

## 四、结算

1. 点击购物车/去结算
2. 确认地址
3. 确认订单并支付

## 五、禁忌

不点一杯不够起送价！
不选错规格！
不跳过确认地址！