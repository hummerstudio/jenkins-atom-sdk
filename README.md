# Jenkins Atom SDK

写 Jenkins Atom 插件用的 Java SDK：**零依赖**，读 `input.json`、跑你的代码、写 `output.json`。

配套的 Jenkins 插件是 [jenkins-atom-runner](https://github.com/hummerstudio/jenkins-atom-plugin) ——
它负责下载插件包、准备参数与凭据、把进程拉起来、回收结果。本 SDK 只关心“进来的是文件，出去的是文件”。

```xml
<dependency>
  <groupId>com.jenkinschina</groupId>
  <artifactId>jenkins-atom-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

> **发布状态**：还没上传到 Maven Central。现在的用法是 `./build.sh` 出 jar，随原子包一起放进去
> （`task.json` 的 classpath 里带上 `jenkins-atom-sdk.jar`）。等要公开发布时再看
> [docs/PUBLISHING.md](docs/PUBLISHING.md)。

## 一个原子长什么样

```java
@AtomService(paramClass = DemoParam.class)
public class MyAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) {
        DemoParam param = context.getParam(DemoParam.class);   // 绑定 input.json
        String token = context.getSecret("deployToken");       // 来自 Jenkins Credentials

        context.log("hello " + param.msg);
        Path report = context.getWorkspace().resolve("out/report");

        return AtomResult.success("done")
                .data("echo", param.msg)
                .artifact("files", context.getWorkspace().resolve("out/result.txt").toString())
                .report("report", "测试报告", report.toString(), "index.html");
    }
}
```

配套的 `task.json`（与 bk-ci 的写法一致，Jenkins 扩展放 `x-jenkins`）：

```json
{
  "atomCode": "my-atom",
  "execution": {
    "language": "java",
    "target": "$atom_java_path -cp my-atom.jar:jenkins-atom-sdk.jar com.jenkinschina.atom.sdk.AtomRunner"
  },
  "input": { "msg": { "label": "消息", "type": "vuex-input", "default": "hi" } },
  "output": { "echo": { "type": "string" } },
  "x-jenkins": {
    "protocol": "jenkins-1",
    "secrets": { "deployToken": "my-credentials-id" }
  }
}
```

打包后把 `my-atom.jar` + `jenkins-atom-sdk.jar` + `task.json` 放进插件目录，
用主仓库的 `tools/publish-atom.sh` 发布到插件中心。

## 一个 jar 里放多个实现

一个插件包可以包含多个 `AtomTask`（共享代码/依赖，但对外是两个原子）。选择顺序由 `AtomRunner` 决定：

1. `ATOM_TASK_CLASS`（或系统属性 `atom.task.class`）—— 显式指定，优先级最高。这个值来自两处：
   **job 配置里的「入口类（Task）」下拉框**（runner 读包里的 `META-INF/services` 生成候选），
   或者 `task.json` 的 `x-jenkins.taskClass` 作为包级默认；
2. `ATOM_CODE` —— runner 会传当前要执行的 atomCode，SDK 找 `@AtomService` 值相同的那个：

   ```java
   @AtomService(value = "sdk-demo", paramClass = DemoParam.class)
   public class DemoAtom implements AtomTask { ... }

   @AtomService("sdk-demo-alt")
   public class AltTask implements AtomTask { ... }
   ```
3. 只有一个实现时才自动选中。

多个实现且都没匹配上时**直接报错并列出候选**，而不是默默跑第一个：

```
this jar has 2 AtomTask implementations (…DemoAtom(sdk-demo), …AltTask(sdk-demo-alt))
and none matched atomCode=xxx; set x-jenkins.taskClass in task.json or ATOM_TASK_CLASS
```

`example/` 就是一个实现，`jenkins-atom-demo` 仓库演示了两个实现同包。

## 契约（protocol `jenkins-1`）

| 输入 | 说明 |
| --- | --- |
| `$atom_data_dir` | 工作目录（也是进程 cwd），`input.json` / `output.json` 都在这里 |
| `$atom_data_input` | 默认 `input.json`，键是表单字段名 + `bk*` 环境变量 |

规范名是 `atom_data_dir` / `atom_data_input` / `atom_data_output` / `atom_java_path` / `atom_workspace`；
`bk_data_*` 是 BK-CI 的老名字，SDK 两个都认（先 `atom_*`，再 `bk_*`）。
| `.secrets.json` | 凭据（键为字段名与 credentials id 两种），**读完即删** |

`output.json`：

```json
{
  "status": "success|failure|error",
  "message": "给人看的一句话",
  "errorType": 1,
  "errorCode": 70001,
  "data": {
    "echo":     { "type": "string", "value": "hi" },
    "password": { "type": "string", "value": "x", "isSensitive": true },
    "files":    { "type": "artifact", "value": ["/abs/path/out.txt"] },
    "report":   { "type": "report", "label": "报告", "path": "/abs/report", "target": "index.html" }
  }
}
```

## 构建

```bash
./build.sh     # SDK + 示例原子
./e2e.sh       # 用真实 java -cp 跑一遍，校验契约
mvn -B -ntp test
```

## 发布到 Maven Central

见 [docs/PUBLISHING.md](docs/PUBLISHING.md)（命名空间验名、GPG、Portal token、GitHub Actions 全流程）。

## License

Apache-2.0
