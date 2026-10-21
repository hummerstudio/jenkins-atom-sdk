# 发布到 Maven Central（2026 现行流程）

> **状态：暂未发布。** 本文是“将来要发时的路线”，先按这个准备着；不上传不影响任何现有用法
> （原子包自带 jar 即可）。

Central 旧的 OSSRH staging 已经下线，现在是 **Central Portal**（central.sonatype.com）：
`mvn -P release deploy` 把 bundle 传上去，校验通过后在页面上点发布（或开 `autoPublish`）。

本仓库的 `pom.xml` 已经把 Central 的硬性要求都配好了：

- `-sources.jar` / `-javadoc.jar`（`release` profile 里）
- PGP 签名（`maven-gpg-plugin`，`verify` 阶段）
- POM 元数据：`name` / `description` / `url` / `licenses` / `developers` / `scm`
- `central-publishing-maven-plugin:0.12.0`，`publishingServerId=central`，`autoPublish=false`
- checksum 由 Portal 插件自动生成（MD5/SHA1/SHA256/SHA512）

## 0. 先决条件（一次性）

### 0.1 命名空间

groupId 是 `com.jenkinschina`，对应已有的 jenkinschina.com，**不需要注册新域名，也不涉及 GitHub 命名空间**。
验名走 DNS：

1. 在 https://central.sonatype.com → Namespaces → 添加 `com.jenkinschina`；
2. Portal 给你一个 ticket 号（形如 `OSSRH-12345`）；
3. 在 jenkinschina.com 的 DNS 加一条 TXT 记录：`@  TXT  "OSSRH-12345"`（Cloudflare 上几十秒生效）；
4. 回 Portal 点 Verify，通过后 TXT 记录可以删掉。

> 换 groupId 的代价：坐标和 Java 包名要一起改（`com.jenkinschina.atom.sdk`），所以**在有人依赖之前**定下来。

### 0.2 GPG 签名密钥

```bash
gpg --full-generate-key          # RSA 4096，有效期 2y，记住 passphrase
gpg --list-secret-keys --keyid-format=long      # 取 KEYID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>   # 必须上传，Central 要能查到公钥
gpg --armor --export-secret-keys <KEYID> > /tmp/central-signing-key.asc
```

- 私钥只进 GitHub Secret（`GPG_PRIVATE_KEY`），绝不进仓库；
- 密钥过期后可以延长（`gpg --edit-key`），过期的旧签名仍然有效；
- CI 里用 `--pinentry-mode loopback` + `GPG_PASSPHRASE`，pom 里已配。

### 0.3 Portal token

central.sonatype.com → 右上角账号 → **Generate User Token**，得到一对 username/password
（不是登录密码）。放进 GitHub Secrets：

| Secret | 值 |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | token username |
| `MAVEN_CENTRAL_PASSWORD` | token password |
| `GPG_PRIVATE_KEY` | 上面导出的私钥全文 |
| `GPG_PASSPHRASE` | 私钥口令 |

## 1. 本地干跑（不需要 token）

```bash
mvn -B -ntp -P release -Dgpg.skip=true package
ls target/*.jar      # 应该有 3 个：主 jar、-sources.jar、-javadoc.jar
```

## 2. 发布

本地（需要 `~/.m2/settings.xml` 里有 `central` server）：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

```bash
mvn -B -ntp -P release deploy      # 上传 bundle，Portal 做校验
```

或者打 tag，让 `.github/workflows/release.yml` 跑（推荐）：

```bash
git tag v0.1.0 && git push origin v0.1.0
```

然后到 https://central.sonatype.com → Deployments → 看到 `Published` 状态后点 **Publish**。
首次建议一直手动点，确认 bundle 没问题后把 `autoPublish` 改成 `true` 全自动。

## 3. 规则与坑

- **版本不可变**：`0.1.0` 发出去就不能覆盖，修 bug 请发 `0.1.1`。
- **不能发 `-SNAPSHOT`**；快照可以发 Portal 的 snapshots 仓库（本仓库没配）。
- 传上去的 bundle 校验失败不会污染 Central，改完重传即可（版本号可以复用，只要没真正发布）。
- 发布后通常 10~30 分钟内出现在 `repo1.maven.org`，搜索索引更慢。
- `-javadoc.jar` 里如果注释有坏 HTML，`doclint` 会报错：pom 里已经设 `doclint=none`。
- 不要提交 `target/`、`*.asc`、私钥。

## 4. 发布之后

- README 里的依赖坐标可以给外部原子作者用了；
- 主仓库 `jenkins-atom-plugin` 的 `NativeProtocolTest` 会优先用本仓库构建出的 jar，
  也可以改成从 Central 拉取；
- 发新版时同步改 `example/pom.xml` 里的 SDK 版本。

## 5. 回滚 / 撤包

Central 不支持删除已发布版本（只能发新版修正）。如果发错：
Portal → Deployments 里**未发布**的可以 drop；已发布的只能发补丁版本。
