package com.na.syslog.enums;



public enum NaOperateEnum implements INaOperateProvider{
    /**
     * 默认操作
     */
    DEF_OPT("DEF_OPT","默认操作"),
    /**
     * 测试操作
     */
    TEST_OPT("TEST_OPT","测试操作"),
    /**
     * 添加
     */
    ADD("ADD", "添加"),
    /**
     * 查询
     */
    QUERY("QUERY", "查询"),

    /**
     * 编辑
     */
    UPDATE("UPDATE", "编辑"),
    /**
     * 删除
     */
    DELETE("DELETE", "删除"),
    /**
     * 检测
     */
    CHECK("CHECK", "检测"),
    /**
     * 登录
     */
    LOGIN("LOGIN", "登录"),
    /**
     * 登出
     */
    LOGOUT("LOGOUT", "登出"),

    UPLOAD("UPLOAD","上传"),

    DOWNLOAD("DOWNLOAD","下载"),

    CONVERSION("CONVERSION","数据转换"),

    BIND("BIND","绑定"),

    UN_BIND("UNBIND","解绑"),

    AUTH("AUTH","授权"),

    UN_AUTH("UNAUTH","取消授权"),
    ;

    private final String code;
    private final String desc;

    private NaOperateEnum(String code, String desc) {
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