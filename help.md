### 日志记录模块

```text
本版本发布时间为 2021-05-01  适配jdk版本为 1.8
```

#### 1 配置
##### 1.1 添加依赖
```
<dependency>
    <groupId>com.na</groupId>
    <artifactId>na-log</artifactId>
    <version>1.0.0</version>
</dependency>
        
或者

<dependency>
    <groupId>com.na</groupId>
    <artifactId>na-log</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/../lib/na-log-1.0.0.jar</systemPath>
</dependency>

相关依赖

<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
</dependency>
```

##### 1.2 配置
```yaml
na:
  log:
# 是否开启日志记录功能，默认开启
    enabled: true
    key: 秘钥需要申请
```

##### 1.3 使用
```java
/**
 * 接口上使用注解
 */
@OperationLog(formatter = UserTreeFormatter.class)


/**
 * 自定义model枚举
 */
import com.na.syslog.enums.INaModelProvider;

public enum MyModuleEnum implements INaModelProvider {
    USER_MANAGEMENT("USER_MANAGEMENT", "用户管理"),
    ;

    private final String code;
    private final String desc;

    private MyModuleEnum(String code, String desc) {
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


/**
 * 自定义operate枚举
 */

import com.na.syslog.enums.INaOperateProvider;

public enum MyOperateEnum implements INaOperateProvider {
    QUERY("QUERY", "查询"),
    ;

    private final String code;
    private final String desc;

    private MyOperateEnum(String code, String desc) {
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


/**
 * 具体实现
 */
@Component
public class UserTreeFormatter implements ILogFormatter {
    @Override
    public void beforeContext(LogFormatterContext context) {
//        Object[] parameters = context.getParameters();
//        Principal principal = (Principal) parameters[0];
        // 若未使用auth模块，请使用其他自定义方式获取当前用户
        AuthUserModel model = NaAuthUtil.getCurrentUser();
        String content = "【" + model.getUsername() + "】 查询个人路由树";
        context.setContent(content);
    }

    @Override
    public void afterContext(LogFormatterContext context) {

    }

    @Override
    public INaModelProvider module() {
        return MyModuleEnum.USER_MANAGEMENT;
    }

    @Override
    public INaOperateProvider operate() {
        return MyOperateEnum.QUERY;
    }
}


/**
 * 日志监听 写入到数据库
 */
@Slf4j
@Component
public class SysLogEventListener {
    @Autowired
    private ISysLogMapper sysLogMapper;

    private static final Integer STR_MAX_LENGTH = 16383;

    @Async("sysLogThreadPool")
    @EventListener(NaSysLogEvent.class)
    public void handleSysLogEvent(NaSysLogEvent event) {
        if (event == null || event.getSource() == null) {
            return;
        }
        NaSysLogDto sysLogDto;
        try {
            sysLogDto = (NaSysLogDto) event.getSource();
        } catch (ClassCastException e) {
            // 如果类型不匹配，直接不处理
            return;
        }
        System.out.println("Received sysLogDto: " + sysLogDto);
        String requestUri = sysLogDto.getRequestUri();
        String result = sysLogDto.getResult();
        String exDetail = sysLogDto.getExDetail();


        SysLogEntity entity = new SysLogEntity();
        entity.setId(sysLogDto.getId() == null ? NaIDUtil.ID(17) : sysLogDto.getId());
        entity.setUserId(StringUtils.isEmpty(sysLogDto.getUserId()) ? 0L : Long.parseLong(sysLogDto.getUserId()));
        entity.setRequestIp(Objects.toString(sysLogDto.getRequestIp(), ""));
        entity.setType(Objects.toString(sysLogDto.getType(), ""));
        entity.setDescription(Objects.toString(sysLogDto.getDescription(), ""));
        entity.setClassPath(Objects.toString(sysLogDto.getClassPath(), ""));
        entity.setActionMethod(Objects.toString(sysLogDto.getActionMethod(), ""));
        entity.setRequestUri(org.springframework.util.StringUtils.isEmpty(requestUri) ? "" :
                requestUri.substring(0, Math.min(requestUri.length(), STR_MAX_LENGTH)));
        entity.setHttpMethod(Objects.toString(sysLogDto.getHttpMethod(), ""));
        String paramsJson = JSONObject.toJSONString(sysLogDto.getParams());
        if (paramsJson != null && paramsJson.length() > 1000) {
            paramsJson = paramsJson.substring(0, 1000) + "...(truncated)";
        }
        entity.setParams(Objects.toString(paramsJson, ""));
        entity.setResult(org.springframework.util.StringUtils.isEmpty(result) ? "" :
                result.substring(0, Math.min(result.length(), STR_MAX_LENGTH)));
        entity.setExDesc(Objects.toString(sysLogDto.getExDesc(), ""));
        entity.setExDetail(org.springframework.util.StringUtils.isEmpty(exDetail) ? "" :
                exDetail.substring(0, Math.min(exDetail.length(), STR_MAX_LENGTH)));
        entity.setStartTime(sysLogDto.getStartTime());
        entity.setFinishTime(sysLogDto.getFinishTime());
        entity.setConsumingTime(sysLogDto.getConsumingTime());
        entity.setModule(Objects.toString(sysLogDto.getModule(), ""));
        entity.setOperate(Objects.toString(sysLogDto.getOperate(), ""));
        entity.setContent(Objects.toString(sysLogDto.getContent(), ""));
        entity.setUa(Objects.toString(sysLogDto.getUa(), ""));
        entity.setOperateTime(sysLogDto.getOperateTime());
        HttpServletRequest request = sysLogDto.getRequest();
        entity.setUserId(TokenUtil.userId());
        try {
            sysLogMapper.insert(entity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


```


# 【注意】启动类配置
```
如果你的包名不是以com.na开头的，需要配置
@ComponentScan(basePackages = {"com.na", "com.ziji.baoming"}) // 扫描多个包路径
```
