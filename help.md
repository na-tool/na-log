### 日志记录模块

```text
当前版本 3.0.0，要求 JDK 17 / Spring Boot 3.x（jakarta 命名空间）
本模块不依赖 na-common 或任何其他内部组件，接入只需引入本依赖。
```

#### 1 配置

##### 1.1 添加依赖

```xml
<dependency>
    <groupId>io.github.na-tool</groupId>
    <artifactId>na-log</artifactId>
    <version>3.0.0</version>
</dependency>
```

##### 1.2 宿主需要自带的组件

本模块自己的依赖全部是 `provided` 作用域，不会传递。宿主需要已有下列组件
（Spring Boot Web 应用通常都已经具备）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <!-- 必须 >= 3.18.0：3.0~3.17.x 受 CVE-2025-48924 影响 -->
    <version>3.18.0</version>
</dependency>
```

##### 1.3 配置项

```yaml
na:
  log:
    # 是否开启日志采集，默认 true
    enabled: true
    # 是否把模块/操作的中文描述而非编码写入日志，默认 false
    cn: false
```

> 早期版本的 `na.log.key` 已失效：授权校验代码已整体移除，配置该项不再有任何效果。

##### 1.4 装配

**不需要任何额外配置。** 本模块通过 Spring Boot 自动装配注册，宿主无需在自己的
启动类上扫描本模块的包，也不要求包名以 `com.na` 开头。

> `@EnableAsync` 随本模块的自动装配一同生效。若宿主代码中存在此前因缺少
> `@EnableAsync` 而静默失效的 `@Async` 方法，引入本模块后会开始真正异步执行。

##### 1.5 覆盖默认线程池

日志线程池的 Bean 名是 `sysLogThreadPool`，宿主声明同名 Bean 即可整体替换：

```java
@Configuration
public class MyLogConfig {

    @Bean(name = "sysLogThreadPool")
    public Executor myLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("my-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
```

Bean 名必须**完全一致**。用别的名字（如 `myLogExecutor`）不会覆盖，而是与新默认
线程池共存 —— `@Async("sysLogThreadPool")` 仍然解析到默认的那个，容易误以为改生效了。

同理，宿主提供自己的 `OperationLogAspect` 或 `LogEventUtil` 实现（**任何 Bean 名**）
也会让对应的默认实现自动让位。

覆盖在两种加载路径下都成立：无论本模块是通过自动装配引入，还是因为宿主的
`@ComponentScan` 范围包含 `com.na` 而被当作普通配置类拾取，结果一致。

#### 2 使用

##### 2.1 注解

```java
@OperationLog(formatter = UserTreeFormatter.class)
```

切点匹配 `com.na.controller` 包下的公有方法，或任意 `*controller*` 包内带该注解的公有方法。
**漏写注解不会报错，只会静默不记录。**

##### 2.2 自定义模块与操作枚举

```java
import com.na.syslog.enums.IModelProvider;

public enum MyModuleEnum implements IModelProvider {
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
```

```java
import com.na.syslog.enums.IOperateProvider;

public enum MyOperateEnum implements IOperateProvider {
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
```

`getCode()` 的返回值直接落库到日志的 `module` / `operate` 字段，改动会与历史记录语义不一致。
内置字典见 `ModuleEnum` / `OperateEnum`。

##### 2.3 自定义 Formatter

```java
@Component
public class UserTreeFormatter implements ILogFormatter {

    @Override
    public void beforeContext(LogFormatterContext context) {
        // 若未使用 auth 模块，请用其他方式获取当前用户
        context.setContent("查询个人路由树");
        context.setUserId("当前用户ID");
    }

    @Override
    public void afterContext(LogFormatterContext context) {
    }

    @Override
    public IModelProvider module() {
        return MyModuleEnum.USER_MANAGEMENT;
    }

    @Override
    public IOperateProvider operate() {
        return MyOperateEnum.QUERY;
    }
}
```

实现类必须注册为 Spring Bean。`beforeContext` 抛出的异常会被切面吞掉并降级为
`type=ex` 的记录，此时 `afterContext` 不再执行，业务方法仍正常返回。

`LogFormatterContext` 暴露的 servlet 类型是 **`jakarta.servlet.http.*`**：

```java
import jakarta.servlet.http.HttpServletRequest;

HttpServletRequest request = context.getRequest();
```

请求相关字段可直接读取，`getRequest()` 只在请求线程内有效。

##### 2.4 落库监听

```java
import com.na.syslog.dto.SysLogDto;
import com.na.syslog.event.SysLogEvent;

@Slf4j
@Component
public class SysLogListener {

    @Async("sysLogThreadPool")
    @EventListener(SysLogEvent.class)
    public void handleSysLogEvent(SysLogEvent event) {
        SysLogDto dto = (SysLogDto) event.getSource();
        // 这里把 dto 写入数据库
    }
}
```

线程池 `sysLogThreadPool` 由本模块自动装配提供，核心 5、最大 10、队列 25。
队列满时按"丢弃并告警"处理：丢弃本次日志，**每秒最多打一条 warn**（含累计丢弃数），
不会阻塞也不会打断业务请求。

> **`SysLogDto` 上不再有 `request` / `response` 字段。** 事件是异步消费的，回调时
> 请求线程早已返回，request 可能已被容器回收。本模块已把 IP、URI、HTTP 方法、UA、
> userId 抽成标量字段；需要更多请求信息请在 Formatter 内于请求线程中提取。
>
> 日志链路的任何失败（事件发布被拒、Formatter 异常等）都不会影响业务请求的返回值。

##### 2.5 异常如何记录

- **业务方法抛出异常**：先落一条 `type=ex` 的记录（`exDesc` 为异常类名加消息，
  `exDetail` 为完整堆栈），再把异常**原样抛出**，业务语义不变。
- **日志链路自身出错**（Formatter 异常、上下文构建失败）：降级为 `type=ex` 记录，
  `exDesc` 为"日志记录异常"。
- **不在 Web 请求上下文中**（定时任务调用带注解的方法）：静默跳过，只留一条 warn，
  不产生任何记录。

#### 3 从 1.x 升级

3.0.0 是破坏性大版本，接入方需要同步调整：

| 变更 | 需要做什么 |
|---|---|
| 坐标 `com.na:na-log:1.0.0` → `io.github.na-tool:na-log:3.0.0` | 改依赖坐标 |
| 注解 `@NaOperationLog` → `@OperationLog` | 全局替换注解 |
| `INaLogFormatter` → `ILogFormatter` | 全局替换类型名 |
| `NaLogFormatterContext` → `LogFormatterContext` | 全局替换类型名 |
| `INaModelProvider` / `INaOperateProvider` → `IModelProvider` / `IOperateProvider` | 全局替换类型名 |
| `NaModuleEnum` / `NaOperateEnum` → `ModuleEnum` / `OperateEnum` | 全局替换类型名 |
| `DefFormatterNa` → `DefFormatter` | 全局替换类型名 |
| servlet API 由 `javax` 改为 `jakarta` | 自定义 Formatter 的 import 改 jakarta |
| 事件与 DTO 从 na-common 迁入本模块 | 监听器改 import：`com.na.syslog.event.SysLogEvent`、`com.na.syslog.dto.SysLogDto` |
| 不再需要 `@ComponentScan("com.na")` | 可删掉该扫描（若还有其他组件依赖它则保留） |
| `NaSysLogEvent` / `NaSysLogDto` → `SysLogEvent` / `SysLogDto` | 监听器改类型 |
| `SysLogDto` 移除 `request` / `response` 字段 | 监听器若读过这两个 getter 需改用其他字段 |
| `actionMethod` 现在落的是**方法名** | 此前它落的是类名（历史 bug）。下游若有基于旧值的统计需适配 |
| `params` 落库格式改为逗号分隔、跳过 null 与空值 | 若下游有解析逻辑需适配。本模块不做长度截断，超长内容由宿主监听器在落库前自行截断 |
| `result` 落库：null 由字符串 `"null"` 变为真 null，数组由对象哈希变为 `{a,b}` | 同上 |
| 业务异常开始落库 | 失败请求会产生 `type=ex` 记录，注意数据量 |

已知限制：返回值若为未重写 `toString()` 的 POJO，日志里仍是对象哈希。彻底解决需要
JSON 序列化，而 Jackson 只是 `spring-boot-starter-web` 的传递依赖，本模块不引入该硬依赖。
