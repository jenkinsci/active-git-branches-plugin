# Active Git Branches Parameter Plugin

[English](README.md) | [简体中文](README.zh-CN.md)

在 Jenkins 构建前，不用再从上百个过期 Git 分支里慢慢找。

**Active Git Branches Parameter** 提供一个 Jenkins 构建参数，让用户优先看到最可能要构建的分支：最近活跃的分支。你还可以通过过滤、数量限制、强制保留和禁选规则，让分支下拉框更贴近团队真实工作流。

这个插件适合分支数量多、Feature/Release 分支频繁、或者一个 Jenkins workspace 中包含多个 Git 仓库的团队。

## 为什么选择这个插件？

很多 Jenkins 插件都能“列出 Git 分支”。这个插件关注的是：当仓库分支很多时，怎样让用户更快选到正确分支，并减少误选风险。

- **优先看到最近活跃的分支**：不再只按字母排序，最近有提交的分支会更靠前。
- **降低误选保护分支的风险**：通过 `excludeBranches` 让 `main`、`master`、`release/.*` 等分支可见但不可选。
- **重要分支始终可见**：通过 `alwaysIncludeBranches` 保证 `main`、`develop` 等关键分支不会因为 Top N 限制被挤掉。
- **不需要 Groovy 脚本审批**：使用专用 Java 参数类型，不依赖 Active Choices Groovy 脚本，也不需要 Script Approval。
- **支持直接配置仓库地址**：适合在 Pipeline 中手动 `git checkout` 的项目，不依赖 Jenkins Job 的 SCM 配置。
- **适合多仓 workspace**：可以自动识别 workspace 下一级子目录中的 Git 仓库，也支持手动配置 `subdirectory`。
- **二次打开更快**：使用 stale-while-revalidate 缓存，页面可以先展示上一次分支列表，同时后台刷新最新数据。

## 适合哪些场景？

当你遇到这些问题时，可以考虑使用这个插件：

- 仓库分支很多，用户通常只关心最近更新过的分支。
- 想要一个简单的“只选分支”的构建参数，而不是维护 Groovy 脚本。
- 私有仓库需要 Jenkins Credentials 支持。
- 希望阻止用户从 `main`、`master` 或 release 分支发起某些构建，但又希望这些分支在 UI 中保持可见。
- Pipeline 在 Jenkinsfile 或 workspace 子目录中 checkout 一个或多个仓库。

如果你需要选择 tag、pull request 或任意 revision，成熟的 [Git Parameter Plugin](https://plugins.jenkins.io/git-parameter/) 可能更适合。如果你需要 GitLab API 风格的 refs 加载，可以看看 [GitLab Repository Refs Parameter](https://plugins.jenkins.io/gitlab-repository-refs-parameter/)。

## 功能特性

- 从指定 Git 仓库动态获取分支。
- 在 workspace refs 或 full-clone 模式可用时，按分支最近提交时间排序。
- 支持 quick fetch 模式，在不需要提交时间排序时快速列出远程分支。
- 支持 Top N 限制，让下拉框更聚焦。
- 支持 Java 正则表达式过滤分支。
- 支持 always-include 规则，确保重要分支始终可见。
- 支持 exclude/disable 规则，让特定分支可见但不可选。
- 集成 Jenkins Credentials，支持私有仓库。
- 支持 workspace 一级子目录自动检测和手动子目录配置。
- 使用 stale-while-revalidate 缓存，提升 Build with Parameters 页面响应速度。

## 环境要求

- Jenkins 2.541.3 或更高版本
- Java 17 或更高版本
- Git Plugin

## 对比

| 需求 | Git Parameter | List Git Branches Parameter | Active Choices Script | Active Git Branches Parameter |
|------|---------------|-----------------------------|-----------------------|-------------------------------|
| 分支下拉框 | 支持 | 支持 | 需要自定义脚本 | 支持 |
| Tag / PR / revision | 支持 | 支持 tag / revision | 需要自定义脚本 | 不支持，专注分支选择 |
| 在参数中直接配置仓库地址 | 不支持，读取 Job SCM | 支持 | 需要自定义脚本 | 支持 |
| 按最近提交活跃度排序 | 无原生支持 | 主要按名称排序 | 可以实现但要写脚本 | 支持 |
| Jenkins Credentials 集成 | 支持 | 支持 | 依赖脚本实现 | 支持 |
| 不需要 Groovy 脚本审批 | 支持 | 支持 | 不支持 | 支持 |
| 在 UI 中禁选保护分支 | 能力有限 | 能力有限 | 需要自定义脚本 | 支持 |
| workspace 子目录仓库自动检测 | 依赖 SCM 配置 | 不支持 | 需要自定义脚本 | 支持 |

## 安装

### 从源码构建

1. 克隆本仓库。
2. 构建插件：
   ```bash
   mvn clean package
   ```
3. 在 Jenkins 插件管理页面上传 `target/active-git-branches.hpi`。

### Jenkins Update Center

即将支持。

## 使用方式

### Declarative Pipeline

```groovy
pipeline {
    agent any
    parameters {
        activeGitBranches(
            name: 'BRANCH',
            repositoryUrl: 'https://github.com/your-org/your-repo.git',
            credentialsId: 'github-credentials',
            maxBranchCount: 10,
            branchFilter: 'feature/.*',
            alwaysIncludeBranches: 'main|develop',
            excludeBranches: 'main|master',
            description: 'Select a branch to build'
        )
    }
    stages {
        stage('Build') {
            steps {
                echo "Building branch: ${params.BRANCH}"
                git branch: params.BRANCH,
                    url: 'https://github.com/your-org/your-repo.git',
                    credentialsId: 'github-credentials'
            }
        }
    }
}
```

### Scripted Pipeline

```groovy
properties([
    parameters([
        activeGitBranches(
            name: 'BRANCH',
            repositoryUrl: 'https://github.com/your-org/your-repo.git',
            maxBranchCount: 15,
            description: 'Select a branch'
        )
    ])
])

node {
    stage('Build') {
        echo "Selected branch: ${params.BRANCH}"
    }
}
```

### Freestyle Job

1. 打开 Job 配置页面。
2. 勾选 “This project is parameterized”。
3. 点击 “Add Parameter”，选择 “Active Git Branches Parameter”。
4. 配置参数：
   - **Parameter Name**：参数名，例如 `BRANCH`
   - **Git Repository URL**：Git 仓库地址
   - **Credentials**：私有仓库凭据，可选
   - **Max Branch Count**：最多展示多少个分支，默认 `10`
   - **Branch Filter**：分支过滤正则，可选
   - **Always Include Branches**：必须保持可见的分支正则，可选
   - **Exclude Branches**：可见但不可选的分支正则，可选
   - **Use Quick Fetch**：使用快速远程分支列表，默认启用
   - **Allow Custom Branch**：允许用户手动输入分支名，可选
   - **Default Value**：默认选中的分支，可选

## 配置项

| 配置项 | 必填 | 说明 |
|--------|------|------|
| `name` | 是 | 参数名 |
| `repositoryUrl` | 是 | Git 仓库地址，支持 HTTPS 或 SSH |
| `credentialsId` | 否 | Jenkins 凭据 ID，用于私有仓库 |
| `maxBranchCount` | 是 | 最多展示多少个分支，范围 1-100 |
| `branchFilter` | 否 | 用于过滤分支名的正则表达式 |
| `alwaysIncludeBranches` | 否 | 必须始终包含在列表中的分支正则 |
| `excludeBranches` | 否 | 禁止选择的分支正则，匹配后会置灰且不可选 |
| `useQuickFetch` | 否 | 没有匹配 workspace 时，使用不带提交时间的快速远程分支列表，默认 `true` |
| `defaultValue` | 否 | 默认选中的分支 |
| `subdirectory` | 否 | workspace 内 Git 仓库相对路径；为空时自动检测 |
| `allowCustomBranch` | 否 | 允许用户手动输入任意分支名，默认 `false` |
| `description` | 否 | 参数描述 |

## 分支过滤示例

| 正则 | 说明 |
|------|------|
| `.*` | 匹配所有分支 |
| `feature/.*` | 只匹配 feature 分支 |
| `release/.*` | 只匹配 release 分支 |
| `(main\|master\|develop)` | 只匹配 main、master 或 develop |
| `(?!release/).*` | 排除 release 分支 |
| `hotfix-.*\|bugfix-.*` | 匹配 hotfix 或 bugfix 分支 |

## 开发

### 前置条件

- JDK 17 或更高版本
- Maven 3.8+

### 构建

```bash
mvn clean package
```

### 本地运行

```bash
mvn hpi:run
```

该命令会启动一个本地 Jenkins 实例，并安装当前插件。默认地址为 `http://localhost:8080/jenkins`。

### 运行测试

```bash
mvn test
```

## 工作原理

1. 用户打开 Build with Parameters 页面时，插件会根据配置解析 Git 分支。
2. 如果找到匹配的 workspace Git 仓库，Jenkins 会执行轻量 fetch 并读取本地 refs，从而按提交时间排序。
3. 如果没有 workspace，quick fetch 模式会优先使用远程 refs 提升速度；如果更关注时间排序，可以使用 full-clone 模式。
4. 插件先应用 `branchFilter`，然后保留 `alwaysIncludeBranches`，再对其余分支应用 `maxBranchCount` 限制。
5. 匹配 `excludeBranches` 的分支会保持可见但不可选，服务端校验也会阻止绕过规则。
6. 后续页面加载会先返回缓存结果，同时后台刷新分支列表。

## 故障排查

### 没有显示分支

- 确认仓库地址正确。
- 确认私有仓库凭据配置正确。
- 在配置页使用 “Test Connection” 检查连接。

### 性能问题

- 降低 `maxBranchCount`。
- 首次加载可能较慢，因为 Jenkins 需要获取仓库信息。

### 认证错误

- 确认凭据对目标仓库有读取权限。
- GitHub 建议使用 Personal Access Token，而不是密码。
- SSH 方式请确认 Jenkins 中 SSH key 配置正确。

## 贡献

欢迎提交 issue 和 pull request。

## License

MIT License - see [LICENSE](LICENSE) for details.
