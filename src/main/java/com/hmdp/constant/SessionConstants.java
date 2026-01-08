package com.hmdp.constant;

/**
 * 会话（Session）相关常量管理类
 * 统一管理Session中存储数据的Key、超时时间等常量，避免魔法值
 * @author 5Hz
 * @date 2025-12-19
 */
public class SessionConstants {

    // ========== 核心设计：私有构造器，防止类被实例化 ==========
    // 常量类只用于存放静态常量，不需要创建对象，因此私有化构造器
    private SessionConstants() {
        // 可选：抛出异常，强化禁止实例化的语义
        throw new AssertionError("禁止实例化常量类 SessionConstants");
    }

    // ========== 会话Key常量（按业务分类，加注释说明） ==========
    /**
     * 短信验证码的Session Key
     */
    public static final String SMS_CODE_KEY = "code";
}