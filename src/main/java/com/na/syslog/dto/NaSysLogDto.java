package com.na.syslog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaSysLogDto {
    private String id;
    private String requestIp;//IP地址
    private String type;//操作类型 OPT EX
    private String userId;
    private String tenantId;
    private String description;//api描述信息
    private String classPath;//类名
    private String actionMethod;//方法名
    private String requestUri;//请求api
    private String httpMethod;//请求方式
    private String params;//请求参数
    private String result;
    private String exDesc;//异常描述
    private String exDetail;//异常对象
    private LocalDateTime startTime;//请求时间
    private LocalDateTime finishTime;//响应时间
    private Long consumingTime;//消耗时间
    private String module;//模块名
    private String operate;//操作名
    private String content;//其他描述信息
    private String ua;
    private LocalDateTime operateTime;

    private String tenantName;
    private String userName;

}
