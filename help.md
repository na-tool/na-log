### 日志记录模块

```text
当前版本 3.0.0，要求 JDK 17 / Spring Boot 3.x（jakarta 命名空间）
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

本模块自己的依赖**全部是 provided 作用域，不会传递**，宿主必须自带下列组件：

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
    <groupId>io.github.na-tool</groupId>
    <artifactId>na-common</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
</dependency>
```

##### 1.2 配置项

```yaml
na:
  log:
    # 是否开启日志采集，默认 true
    enabled: true
    # 是否把模块/操作的中文描述而非编码写入日志，默认 false
    cn: false
```

> 旧版的 `na.log.key` 已失效：授权校验代码已整体移除，配置该项不再有任何效果。

##### 1.3 装配

na-log 通过 Spring Boot 自动装配注册（`META-INF/spring/...AutoConfiguration.imports`），
**不需要**在启动类上扫描本模块的包，宿主包名也不必以 `com.na` 开头。

但 `na-common` 未提供自动装配，宿主仍须扫描它：

```java
@ComponentScan(basePackages = {"com.na.common", "你自己的包"})
```

> `@EnableAsync` 随本模块的自动装配一同生效。若宿主代码中存在 `@Async` 方法，
> 升级后会开始真正异步执行。

#### 2 使用

##### 2.1 注解

```java
@OperationLog(formatter = UserTreeFormatter.class)
```

切点匹配 `com.na.controller` 包下的公有方法，或任意 `*controller*` 包内带该注解的公有方法。
**漏写注解不会报错，只会静默不记录**。

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
        String content = "查询个人路由树";
        context.setContent(content);
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

请求相关字段也可直接读取，`getRequest()` 只在请求线程内有效。

##### 2.4 落库监听

```java
import com.na.common.syslog.dto.SysLogDto;
import com.na.common.syslog.event.SysLogEvent;

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

线程池 `sysLogThreadPool` 由本模块自动装配提供，队列容量 25。

> **`dto.getRequest()` / `dto.getResponse()` 恒为 null。** 事件是异步消费的，回调时
> 请求线程早已返回，request 可能已被容器回收；本模块已把 IP、URI、HTTP 方法、UA、
> userId 抽成标量字段。需要更多请求信息请在 Formatter 内于请求线程中提取。
>
> 日志事件发布失败（如队列满）只会记一条 `操作日志事件发布失败` 的 error 日志，
> **不会影响业务请求的返回值**。

#### 3 从 1.x 升级

3.0.0 是破坏性大版本，接入方需要同步调整：

| 变更 | 需要做什么 |
|---|---|
| 坐标 `com.na:na-log:1.0.0` → `io.github.na-tool:na-log:3.0.0` | 改依赖坐标 |
| 注解 `@OperationLog` → `@OperationLog` | 全局替换注解 |
| `ILogFormatter` / `LogFormatterContext` → `ILogFormatter` / `LogFormatterContext` | 全局替换类型名 |
| servlet API 由 `javax` 改为 `jakarta` | 自定义 Formatter 的 import 改 jakarta |
| `SysLogEvent` / `SysLogDto` → `SysLogEvent` / `SysLogDto` | 监听器改类型 |
| 不再需要 `@ComponentScan("com.na")` | 可删掉该扫描（na-common 仍需扫描） |
| `na.log.key` 失效 | 删掉配置项 |
| `params` 落库格式：逗号分隔 + 1000 字符截断 | 若下游有解析逻辑需适配 |
| `result` 落库：null 由字符串 `"null"` 变为真 null，数组由对象哈希变为 `{a,b}` | 同上 |

已知限制：返回值若为未重写 `toString()` 的 POJO，日志里仍是对象哈希。彻底解决需要
JSON 序列化，而 Jackson 只是 `spring-boot-starter-web` 的传递依赖，本模块不引入该硬依赖。
