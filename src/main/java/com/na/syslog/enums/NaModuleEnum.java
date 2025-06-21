package com.na.syslog.enums;



public enum NaModuleEnum implements INaModelProvider {
    /**
     * 默认模块
     */
    DEF_MODEL("DEF_MODEL","默认模块"),
    /**
     * TEST模块
     */
    TEST("TEST","测试"),
    /**
     * 登录登出
     */
    LOGIN_OUT("LOGIN_OUT", "登录登出"),
    /**
     * 租户管理
     */
    TENANT_MANAGEMENT("TENANT_MANAGEMENT", "租户管理"),

    /**
     * 用户管理
     */
    USER_MANAGEMENT("USER_MANAGEMENT", "用户管理"),

    /**
     * 角色管理
     */
    ROLE_MANAGEMENT("ROLE_MANAGEMENT", "角色管理"),

    /**
     * 菜单 - 路由管理
     */
    MENU_MANAGEMENT("MENU_MANAGEMENT","菜单（路由）管理"),

    /**
     * 权限资源管理
     */
    AUTH_RESOURCE_MANAGEMENT("AUTH_RESOURCE_MANAGEMENT","权限资源管理"),

    MODEL("MODEL","模型管理"),

    /**
     * 日志管理
     */
    LOG_MANAGEMENT("LOG_MANAGEMENT","日志管理"),

    REMOTE_API("REMOTE_API","第三方模块"),

    IMG("IMG","图片模块"),

    BING("BING","绑定模块"),

    UN_BIND("UN_BIND","解绑模块"),

    AUTH("AUTH","认证模块"),

    CONVERSION("CONVERSION","转换模块"),
    ;

    private final String code;
    private final String desc;

    private NaModuleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

}
