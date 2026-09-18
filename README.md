# 🌳 Active Git Branches Parameter Plugin

[🇬🇧 English](README.md) | [🇨🇳 简体中文](README.zh-CN.md)

> 🛑 Stop scrolling through hundreds of stale Git branches when starting a Jenkins build.

**Active Git Branches Parameter** adds a build parameter that shows the branches your team is most likely to need first: recently updated branches, optionally filtered, capped, and protected from accidental selection.

It is designed for teams with busy repositories and many feature/release branches, where the normal branch dropdown quickly becomes noisy.

## ❓ Why This Plugin?

Most Git branch parameter solutions can list branches. This plugin focuses on making the list useful when the repository is busy.

- 🚀 **Find active work quickly**: Put recently updated branches at the top instead of relying only on alphabetical sorting.
- 🔒 **Avoid dangerous branch picks**: Keep protected branches visible but disabled with `excludeBranches`, so users understand what exists without being able to select forbidden branches.
- ⭐ **Keep important branches visible**: Force-show branches such as `main`, `develop`, or `release/.*` with `alwaysIncludeBranches`, even when you limit the dropdown to the top N branches.
- ✅ **No Groovy scripts or script approval**: Use a dedicated Java parameter instead of Active Choices scripts that run shell commands on the controller.
- 🔗 **Works with explicit repository URLs**: Useful for Pipeline jobs where the repository is checked out inside the script, not declared in the job SCM configuration.
- 🪶 **Light on the controller and Git server**: Jobs pointing at the same repository share one cached branch list, refreshed at most once per minute.
- ⚡ **Fast after warm-up**: Uses stale-while-revalidate caching so users see the last known branch list immediately while Jenkins refreshes in the background.

## 🎯 When To Use It

Use this plugin when:

- 📊 **Many branches**: Your branch list is long and users usually want the most recently updated branches.
- 🧹 **Keep it simple**: You want a simple branch-only parameter without maintaining Groovy scripts.
- 🔐 **Private repos**: You need Jenkins credentials support for private repositories.
- 🛡️ **Protect main**: You want to discourage or block builds from `main`, `master`, or release branches while still showing them in the UI.
- 🏗️ **Checkout in the Jenkinsfile**: Your Pipeline checks out one or more repositories from inside the Jenkinsfile instead of the job SCM configuration.

### 📋 Not the right fit?

If you need **tags**, **pull requests**, or **arbitrary revisions**, the mature [Git Parameter Plugin](https://plugins.jenkins.io/git-parameter/) may be a better fit.  
If you need **GitLab-specific** API-based ref loading, consider [GitLab Repository Refs Parameter](https://plugins.jenkins.io/gitlab-repository-refs-parameter/).

## ✨ Features

### 🔄 Core Functionality
- 🌐 **Dynamic branch fetching** from a configured Git repository.
- ⏰ **Recent-activity sorting** by branch commit date when quick fetch is disabled.
- 🚄 **Quick fetch mode** for fast remote branch listing when commit timestamps are not required.

### 🎛️ Customization & Control
- 🔢 **Configurable Top N limit** to keep the dropdown focused.
- 🔍 **Regex branch filtering** with Java regular expressions.
- ⭐ **Always-include rules** for important branches that must stay visible.
- 🚫 **Exclude/disable rules** for branches that should be visible but not selectable.

### 🔐 Integration & Performance
- 🔑 **Jenkins Credentials integration** for private repositories.
- ⚡ **Stale-while-revalidate cache** for responsive Build with Parameters pages.

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| 🔵 Jenkins | 2.541.3 or later |
| ☕ Java | 17 or later |
| 🔌 Git Plugin | Latest recommended |

## 📊 Comparison

| Feature | Git Parameter | List Git Branches | Active Choices | **Active Git Branches** |
|---------|:---:|:---:|:---:|:---:|
| 📋 Branch dropdown | ✅ | ✅ | ✅ | ✅ |
| 🏷️ Tags / PRs / revisions | ✅ | ✅ | ✅ | ❌ |
| 🔗 Repo in parameter | ❌ | ✅ | ✅ | ✅ |
| ⏰ Sort by recent activity | ❌ | ⚠️ | ⚠️ | ✅ |
| 🔑 Jenkins Credentials | ✅ | ✅ | ⚠️ | ✅ |
| 🚫 No script approval needed | ✅ | ✅ | ❌ | ✅ |
| 🛡️ Disable protected branches in UI | ⚠️ | ⚠️ | ✅ | ✅ |

**Legend:** ✅ Full support · ⚠️ Limited/Partial · ❌ Not supported

## 📦 Installation

Install **Active Git Branches Parameter** from **Manage Jenkins → Plugins → Available plugins**.

## 🚀 Usage

### 📜 Pipeline (Declarative)

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

### 📝 Pipeline (Scripted)

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

### 💼 Freestyle Job

1. 🔧 Go to your job's configuration page
2. ☑️ Check "This project is parameterized"
3. ➕ Click "Add Parameter" and select "Active Git Branches Parameter"
4. ⚙️ Configure the following options:
   - 🏷️ **Parameter Name**: The name of the parameter (e.g., `BRANCH`)
   - 🔗 **Git Repository URL**: The URL of your Git repository
   - 🔑 **Credentials**: Select credentials for private repositories *(optional)*
   - 🔢 **Max Branch Count**: Maximum number of branches to display *(default: 10)*
   - 🔍 **Branch Filter**: Regular expression to filter branches *(optional)*
   - ⭐ **Always Include Branches**: Regex for branches that must remain visible *(optional)*
   - 🚫 **Exclude Branches**: Regex for branches to show but disable *(optional)*
   - 🚄 **Use Quick Fetch**: Fast remote branch listing without commit-time sorting *(default: enabled)*
   - ✏️ **Allow Custom Branch**: Allow users to type a branch name manually *(optional)*
   - 📌 **Default Value**: Pre-selected branch *(optional)*

## ⚙️ Configuration Options

| Option | Required | Description |
|--------|:---:|-------------|
| `name` | ✅ | Parameter name |
| `repositoryUrl` | ✅ | Git repository URL (HTTPS or SSH) |
| `credentialsId` | ⚠️ | Jenkins credentials ID for private repositories |
| `maxBranchCount` | ✅ | Maximum number of branches to display (1-100) |
| `branchFilter` | ⚠️ | Regular expression to filter branch names |
| `alwaysIncludeBranches` | ⚠️ | Regex for branches that must always be included |
| `excludeBranches` | ⚠️ | Regex to disable/prohibit selection of specific branches |
| `useQuickFetch` | ⚠️ | `true`: fast `ls-remote`, alphabetical. `false`: fetch branch tips into a cache repository on the controller, sorted by commit date *(default: `true`)* |
| `defaultValue` | ⚠️ | Default selected branch |
| `allowCustomBranch` | ⚠️ | Allow users to type arbitrary branch name *(default: `false`)* |
| `description` | ⚠️ | Parameter description |

**Legend:** ✅ Required · ⚠️ Optional

## 🔍 Branch Filter Examples

| Pattern | Description |
|---------|-------------|
| `.*` | ✅ Match all branches |
| `feature/.*` | 🎯 Only feature branches |
| `release/.*` | 🔖 Only release branches |
| `(main\|master\|develop)` | 🌳 Only main, master, or develop |
| `(?!release/).*` | ⛔ Exclude release branches |
| `hotfix-.*\|bugfix-.*` | 🔧 Hotfix or bugfix branches |

## 🔬 How It Works

1. 📄 **Page Load**: When the Build with Parameters page loads, the plugin resolves branches for the configured repository. All Git operations run on the controller and never touch job workspaces.
2. 🚄 **Quick Fetch** (default): Lists remote branches with `ls-remote`. Fast, but sorted alphabetically.
3. ⏰ **Time-sorted Fetch** (quick fetch disabled): Fetches only the latest commit of each branch into a cache repository under `$JENKINS_HOME/caches/active-git-branches`, then sorts by commit date. After the first fetch, only changed branch tips are downloaded.
4. 🔤 **Apply Filters**: The branch filter is applied, then `alwaysIncludeBranches` are preserved while `maxBranchCount` limits the rest.
5. 🛡️ **Disable Rules**: Branches matching `excludeBranches` stay visible but are disabled, and server-side validation prevents bypassing the rule.
6. ⚡ **Smart Caching**: Cached results are returned immediately. When the cached list is older than one minute, it is refreshed in the background. Jobs using the same repository, credentials and fetch mode share one cache entry.

## 🔧 Troubleshooting

### ❌ No branches displayed

- ✔️ Verify the repository URL is correct
- ✔️ Check that credentials are configured for private repositories
- ✔️ Use the "Test Connection" button in the configuration

**💡 Tip**: Check Jenkins logs for detailed error messages

### 🐌 Performance issues

- ✔️ Enable `useQuickFetch` to skip commit-date sorting
- ℹ️ With quick fetch disabled, the first load is slower because the branch tips are fetched into the cache repository

**💡 Tip**: To change how often branch lists are refreshed, set the system property `io.jenkins.plugins.activegitbranches.ActiveGitBranchesParameterDefinition.refreshIntervalSeconds` (default `60`)

### 🔐 Authentication errors

- ✔️ Ensure credentials have read access to the repository
- ✔️ For GitHub, use a Personal Access Token instead of password
- ✔️ For SSH, ensure the SSH key is properly configured in Jenkins

**💡 Tip**: Test credentials separately to isolate auth issues

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for how to build and test the plugin.

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.
