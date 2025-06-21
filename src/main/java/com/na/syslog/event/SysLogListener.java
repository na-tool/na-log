//package com.na.syslog.event;
//
//import com.na.syslog.dto.SysLogDto;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.event.EventListener;
//import org.springframework.core.annotation.Order;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//
//import java.util.function.Consumer;
//
//
///**
// * 异步监听日志事件
// */
//@Slf4j
//@Component
//public class SysLogListener {
//    //private String database;
//    private Consumer<SysLogDto> consumer;
//
//    @Async("sysLogThreadPool")
//    @EventListener(SysLogEvent.class)
//    public void saveSysLog(SysLogEvent event) {
//        SysLogDto sysLogDto = (SysLogDto) event.getSource();
//        System.out.println("异步监听日志事件...");
//        SysLogEntity entity = new SysLogEntity();
//        entity.setId(sysLogDto.getId());
//        entity.setRequestIp(sysLogDto.getRequestIp());
//        entity.setType(sysLogDto.getType());
//        entity.setDescription(sysLogDto.getDescription());
//        entity.setClassPath(sysLogDto.getClassPath());
//        entity.setActionMethod(sysLogDto.getActionMethod());
//        entity.setRequestUri(sysLogDto.getRequestUri());
//        entity.setHttpMethod(sysLogDto.getHttpMethod());
//        entity.setParams(sysLogDto.getParams());
//        entity.setResult(sysLogDto.getResult());
//        entity.setExDesc(sysLogDto.getExDesc());
//        entity.setExDetail(sysLogDto.getExDetail());
//        entity.setStartTime(sysLogDto.getStartTime());
//        entity.setFinishTime(sysLogDto.getFinishTime());
//        entity.setConsumingTime(sysLogDto.getConsumingTime());
//        entity.setModule(sysLogDto.getModule());
//        entity.setOperate(sysLogDto.getOperate());
//        entity.setContent(sysLogDto.getContent());
//        entity.setUa(sysLogDto.getUa());
//        entity.setOperateTime(sysLogDto.getOperateTime());
//        AuthUserModel authUserModel = NaAuthUtil.getCurrentUser();
//        entity.setUserFrom();
//
//        entity.setUserId(authUserModel.getUserId());
//        entity.setTenantId(authUserModel.getTenantId());
//
//        // 如果 dto 中存在 tenantName 和 userName，可以设置 @TableField(exist = false) 的字段
//        entity.setTenantName(sysLogDto.getTenantName());
//        entity.setUserName(sysLogDto.getUserName());
//        sysLogMapper.insert(entity);
//    }
//}
