# Active Git Branches Parameter Plugin

[English](README.md) | [简体中文](README.zh-CN.md)

Stop scrolling through hundreds of stale Git branches when starting a Jenkins build.

**Active Git Branches Parameter** adds a build parameter that shows the branches your team is most likely to need first: recently updated branches, optionally filtered, capped, and protected from accidental selection.

It is designed for teams with busy repositories, many feature/release branches, or multi-repository workspaces where the normal branch dropdown quickly becomes noisy.

## Why This Plugin?

Most Git branch parameter solutions can list branches. This plugin focuses on making the list useful when the repository is busy.

- **Find active work quickly**: Put recently updated branches at the top instead of relying only on alphabetical sorting.
- **Avoid dangerous branch picks**: Keep protected branches visible but disabled with `excludeBranches`, so users understand what exists without being able to select forbidden branches.
- **Keep important branches visible**: Force-show branches such as `main`, `develop`, or `release/.*` with `alwaysIncludeBranches`, even when you limit the dropdown to the top N branches.
- **No Groovy scripts or script approval**: Use a dedicated Java parameter instead of Active Choices scripts that run shell commands on the controller.
- **Works with explicit repository URLs**: Useful for Pipeline jobs where the repository is checked out inside the script, not declared in the job SCM configuration.
- **Better for multi-repo workspaces**: Automatically detects matching Git repositories in direct workspace subdirectories, with an optional manual `subdirectory` override.
- **Fast after warm-up**: Uses stale-while-revalidate caching so users see the last known branch list immediately while Jenkins refreshes in the background.

## When To Use It

Use this plugin when:

- Your branch list is long and users usually want the most recently updated branches.
- You want a simple branch-only parameter without maintaining Groovy scripts.
- You need Jenkins credentials support for private repositories.
- You want to discourage or block builds from `main`, `master`, or release branches while still showing them in the UI.
- Your Pipeline checks out one or more repositories from inside the Jenkinsfile or workspace subdirectories.

If you need tags, pull requests, or arbitrary revisions, the mature [Git Parameter Plugin](https://plugins.jenkins.io/git-parameter/) may be a better fit. If you need GitLab-specific API-based ref loading, consider [GitLab Repository Refs Parameter](https://plugins.jenkins.io/gitlab-repository-refs-parameter/).

## Features

- **Dynamic branch fetching** from a configured Git repository.
- **Recent-activity sorting** by branch commit date when workspace refs or full-clone mode are available.
- **Quick fetch mode** for fast remote branch listing when commit timestamps are not required.
- **Configurable Top N limit** to keep the dropdown focused.
- **Regex branch filtering** with Java regular expressions.
- **Always-include rules** for important branches that must stay visible.
- **Exclude/disable rules** for branches that should be visible but not selectable.
- **Jenkins Credentials integration** for private repositories.
- **Subdirectory / multi-repo support** for one-level auto-detection and manual overrides.
- **Stale-while-revalidate cache** for responsive Build with Parameters pages.

## Requirements

- Jenkins 2.541.3 or later
- Java 17 or later
- Git Plugin

## Comparison

| Need | Git Parameter | List Git Branches Parameter | Active Choices Script | Active Git Branches Parameter |
|------|---------------|-----------------------------|-----------------------|-------------------------------|
| Branch dropdown | Yes | Yes | Custom script | Yes |
| Tags / PRs / revisions | Yes | Tags / revisions | Custom script | No, branch-focused |
| Repository configured directly in parameter | No, reads job SCM | Yes | Custom script | Yes |
| Sort branches by recent commit activity | No native support | Name-based sorting | Possible but scripted | Yes |
| Jenkins Credentials integration | Yes | Yes | Manual/script-specific | Yes |
| No Groovy script approval | Yes | Yes | No | Yes |
| Disable protected branches in the UI | Limited | Limited | Custom script | Yes |
| Workspace subdirectory auto-detection | SCM-dependent | No | Custom script | Yes |

## Installation

### From Source

1. Clone this repository
2. Build the plugin:
   ```bash
   mvn clean package
   ```
3. Install the generated `.hpi` file from `target/active-git-branches.hpi` via Jenkins Plugin Manager

### From Jenkins Update Center

*(Coming soon)*

## Usage

### Pipeline (Declarative)

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

### Pipeline (Scripted)

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

1. Go to your job's configuration page
2. Check "This project is parameterized"
3. Click "Add Parameter" and select "Active Git Branches Parameter"
4. Configure the following options:
   - **Parameter Name**: The name of the parameter (e.g., `BRANCH`)
   - **Git Repository URL**: The URL of your Git repository
   - **Credentials**: Select credentials for private repositories (optional)
   - **Max Branch Count**: Maximum number of branches to display (default: 10)
   - **Branch Filter**: Regular expression to filter branches (optional)
   - **Always Include Branches**: Regular expression for branches that must remain visible (optional)
   - **Exclude Branches**: Regular expression for branches to show but disable (optional)
   - **Use Quick Fetch**: Use fast remote branch listing when commit-time sorting is not required (default: enabled)
   - **Allow Custom Branch**: Allow users to type a branch name manually when needed (optional)
   - **Default Value**: Pre-selected branch (optional)

## Configuration Options

| Option | Required | Description |
|--------|----------|-------------|
| `name` | Yes | Parameter name |
| `repositoryUrl` | Yes | Git repository URL (HTTPS or SSH) |
| `credentialsId` | No | Jenkins credentials ID for private repositories |
| `maxBranchCount` | Yes | Maximum number of branches to display (1-100) |
| `branchFilter` | No | Regular expression to filter branch names |
| `alwaysIncludeBranches` | No | Regular expression for branches that must always be included in the list |
| `excludeBranches` | No | Regular expression to disable/prohibit selection of specific branches (grayed out and unselectable) |
| `useQuickFetch` | No | Use fast remote branch listing without commit timestamps when no matching workspace is available (default: `true`) |
| `defaultValue` | No | Default selected branch |
| `subdirectory` | No | Relative path to Git repository within workspace (auto-detected if omitted) |
| `allowCustomBranch` | No | Allow users to type arbitrary branch name manually (default: `false`) |
| `description` | No | Parameter description |

## Branch Filter Examples

| Pattern | Description |
|---------|-------------|
| `.*` | Match all branches |
| `feature/.*` | Only feature branches |
| `release/.*` | Only release branches |
| `(main\|master\|develop)` | Only main, master, or develop |
| `(?!release/).*` | Exclude release branches |
| `hotfix-.*\|bugfix-.*` | Hotfix or bugfix branches |

## Development

### Prerequisites

- JDK 17 or later
- Maven 3.8+

### Building

```bash
mvn clean package
```

### Running locally

```bash
mvn hpi:run
```

This will start a local Jenkins instance at http://localhost:8080/jenkins with the plugin installed.

### Running tests

```bash
mvn test
```

## How It Works

1. When the Build with Parameters page loads, the plugin resolves branches for the configured repository.
2. If a matching workspace Git repository is available, Jenkins performs a lightweight fetch and reads local refs so branches can be sorted by commit date.
3. If no workspace is available, quick fetch mode uses remote refs for speed; full-clone mode can be used when time-based sorting is more important than first-load speed.
4. The branch filter is applied, then `alwaysIncludeBranches` are preserved while `maxBranchCount` limits the rest.
5. Branches matching `excludeBranches` stay visible but are disabled, and server-side validation prevents bypassing the rule.
6. Cached results are returned immediately on later page loads while a background refresh updates the branch list.

## Troubleshooting

### No branches displayed

- Verify the repository URL is correct
- Check that credentials are configured for private repositories
- Use the "Test Connection" button in the configuration

### Performance issues

- Reduce the `maxBranchCount` value
- The first load may be slower as the repository is being cloned temporarily

### Authentication errors

- Ensure credentials have read access to the repository
- For GitHub, use a Personal Access Token instead of password
- For SSH, ensure the SSH key is properly configured in Jenkins

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

MIT License - see [LICENSE](LICENSE) for details.
