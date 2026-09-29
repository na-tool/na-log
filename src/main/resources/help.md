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

本模块只有两个配置项，写在宿主的 `application.yml` 里：

```yaml
na:
  log:
    # 是否开启日志采集，默认 true
    enabled: true
    # 是否把模块/操作的中文描述而非编码写入日志，默认 false
    cn: false
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `na.log.enabled` | `true` | 采集总开关。关掉后切面只做放行，不产生任何日志事件 |
| `na.log.cn` | `false` | `false` 时 `module` / `operate` 落库的是编码（如 `USER_MANAGEMENT`）；`true` 时落中文描述（如 `用户管理`） |

> 不写 `na.log` 段即为上表默认行为，这两个配置项都是可选的。
>
> 早期版本的 `na.log.key` 已失效：授权校验代码已整体移除，配置该项不再有任何效果。

##### 1.4 装配

**不需要任何额外配置。** 本模块通过 Spring Boot 自动装配注册，宿主无需在自己的
启动类上扫描本模块的包，也不要求包名以 `com.na` 开头。

> `@EnableAsync` 随本模块的自动装配一同生效。若宿主代码中存在此前因缺少
> `@EnableAsync` 而静默失效的 `@Async` 方法，引入本模块后会开始真正异步执行。

##### 1.5 定制与覆盖

本模块注册三个 Bean，**三个都可以被宿主替换**。做法都一样：在自己的
`@Configuration` 里声明对应的 Bean，不需要开关、不需要配置项。

| 要替换的东西 | 默认 Bean 名 | 覆盖时 Bean 名怎么起 |
|---|---|---|
| 日志线程池 | `sysLogThreadPool` | **必须完全一致** |
| 采集切面 `OperationLogAspect` | `operationLogAspect` | 任意 |
| 事件发布器 `LogEventUtil` | `logEventUtil` | 任意 |

切面与发布器按**类型**判断是否让位，所以名字随便起；线程池按**名字**判断 ——
宿主通常还有别的用途的 `Executor`，按类型让位会因为一个无关的线程池就把日志池
整个跳过，所以名字必须一致。

###### 调整日志线程池

最常见的是调线程池参数。下面是**完整可直接复制**的写法（含 import）：

```java
package com.yourpackage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MyLogConfig {

    /**
     * 替换 na-log 的日志落库线程池。
     * Bean 名必须叫 sysLogThreadPool —— 监听器上的 @Async("sysLogThreadPool")
     * 就是按这个名字解析执行器的。
     */
    @Bean("sysLogThreadPool")
    public Executor sysLogThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("my-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 不要手动调 initialize()：作为容器管理的 Bean，afterPropertiesSet() 会被自动调用
        return executor;
    }
}
```

三点注意：

1. **起作用的只有 `@Bean` 里的名字**。方法名叫 `sysLogThreadPool()` 还是
   `myLogExecutor()` 都无所谓，`@Bean("sysLogThreadPool")` 里的那串才是判据。
   用别的名字不会覆盖，而是与默认线程池**共存** —— `@Async("sysLogThreadPool")`
   仍然解析到默认那个，不报任何错。
2. **返回类型写 `Executor` 还是 `ThreadPoolTaskExecutor` 都能被解析**，但推荐用
   `ThreadPoolTaskExecutor` —— 它是 Spring 对 `Executor` 的封装，实现了
   `AsyncTaskExecutor`，并且带来了线程包装、优雅停机（容器关闭时等待队列中的任务）
   这些能力，裸 `ThreadPoolExecutor` 没有。
3. **这个 `@Configuration` 类要被 Spring 扫到**（放在宿主自己的包下、能被
   `@SpringBootApplication` 扫到即可），否则不会生效。

**怎么确认生效了**：启动后调一次带 `@OperationLog` 的接口，然后看落库线程的
名字 —— 应该出现 `my-log-1` 这样的线程名。如果看到的仍是 `sysLogThreadPool-1`，
说明 Bean 名没对上、覆盖没生效（此时默认线程池照常在跑，不会报错）。

默认参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `corePoolSize` | 5 | 核心线程数 |
| `maxPoolSize` | 10 | 最大线程数 |
| `queueCapacity` | 25 | 队列容量。核心 + 最大 + 队列 = 35，超过才开始触发拒绝策略 |
| `threadNamePrefix` | `sysLogThreadPool-` | 线程名前缀，排查日志落库积压时靠它认 |
| `rejectedExecutionHandler` | 丢弃并告警 | 不是 `AbortPolicy`。见下方说明 |

> **默认拒绝策略是"丢弃并告警"**：过载时丢本次日志，并打一条 warn（**每秒最多
> 一条**，带累计丢弃数）—— 既不阻塞业务线程，也不让告警本身变成新的负载。
> 如果你们的日志不允许丢，可以换成 `CallerRunsPolicy`（改由请求线程同步落库，
> 不丢但会拖慢请求）；换成 `AbortPolicy` 则会把异常抛给业务请求，不建议。

###### 替换采集切面或事件发布器

宿主如果有更复杂的采集或落库需求，可以整体替换（Bean 名随意）：

```java
@Configuration
public class MyLogConfig {

    @Bean
    public OperationLogAspect myOwnAspect(AutoLogConfig config,
                                          LogEventUtil util,
                                          ApplicationContext ctx) {
        return new OperationLogAspect(config, util, ctx);
    }
}
```

本模块检测到容器里已有同类型 Bean 时，会跳过自己的默认实现。

###### 关于加载路径

以上覆盖方式在两种加载路径下都成立：无论本模块是通过自动装配引入，还是因为宿主
的 `@ComponentScan` 范围包含 `com.na` 而被当作普通配置类拾取，结果一致。

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
