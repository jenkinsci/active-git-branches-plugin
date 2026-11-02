# README demo

The [animated walkthrough](../images/active-branches-demo.gif) uses screenshots of actual keyboard interactions with this plugin on a local Jenkins 2.541.3 instance. The repository and branch names are sample data. No build was started.

The native select popup is not included by the browser screenshot API. The animation therefore shows the real selected values and adds captions describing menu contents verified through the live page's accessibility tree and DOM. It is a screenshot sequence, not a continuous screen recording or a simulated Jenkins UI.

A [static preview](../images/active-branches-demo.png) is available for readers who prefer no animation. The GIF has six scenes, loops every 25.5 seconds, and is approximately 130 KiB.

## Sample repository

The fixture has 35 branches. Each points to an independent commit with an empty tree; only branch names and commit dates matter for this demo.

| Branch | Commit date (UTC) |
| --- | --- |
| `main` | 2026-01-01 |
| `release/1.0` | 2026-01-02 |
| `feature/archive-01` through `feature/archive-30` | 2026-02-01 |
| `feature/camera-preview` | 2026-10-30 |
| `feature/alarm-settings` | 2026-10-31 |
| `feature/playback-fix` | 2026-11-01 |

To recreate the fixture, run this from the plugin checkout. It creates a new repository under the ignored `work/` directory and refuses to overwrite an existing directory:

```sh
python3 - <<'PY'
import os
import subprocess
from pathlib import Path

repo = Path('work/readme-demo-repository').resolve()
repo.mkdir(parents=True, exist_ok=False)

def git(*args, env=None):
    return subprocess.check_output(
        ['git', '-C', str(repo), *args], env=env, text=True
    ).strip()

git('init')
git('symbolic-ref', 'HEAD', 'refs/heads/main')
tree = git('mktree')
env = dict(os.environ, GIT_AUTHOR_NAME='Demo', GIT_AUTHOR_EMAIL='demo@example.com',
           GIT_COMMITTER_NAME='Demo', GIT_COMMITTER_EMAIL='demo@example.com')
branches = [('main', '2026-01-01'), ('release/1.0', '2026-01-02')]
branches += [(f'feature/archive-{i:02d}', '2026-02-01') for i in range(1, 31)]
branches += [('feature/camera-preview', '2026-10-30'),
             ('feature/alarm-settings', '2026-10-31'),
             ('feature/playback-fix', '2026-11-01')]
for name, date in branches:
    env.update(GIT_AUTHOR_DATE=date + 'T12:00:00+00:00',
               GIT_COMMITTER_DATE=date + 'T12:00:00+00:00')
    commit = git('commit-tree', tree, '-m', name, env=env)
    git('update-ref', 'refs/heads/' + name, commit)
print(repo.as_uri())
PY
```

## Jenkins setup

Use a local development Jenkins instance with this plugin installed (`mvn hpi:run`; see [Contributing](../../CONTRIBUTING.md)). Create a separate Freestyle job with two Active Git Branches parameters. Use the fixture's printed `file://` URL for both repository URLs; it must be accessible to the Jenkins controller. Do not use production repositories or credentials for this demo.

| Setting | `ALPHABETICAL` | `ACTIVE_BRANCH` |
| --- | --- | --- |
| Branch filter | `feature/.*` | `feature/.*` |
| Use Quick Fetch | enabled | **disabled** |
| Max Branch Count | 100 | 5 |
| Always Include Branches | empty | `main\|release/.*` |
| Exclude Branches | empty | `main\|release/.*` |
| Allow Custom Branch | disabled | disabled |
| Default Value | empty | empty |

Leave builders empty. Open **Build with Parameters** without starting a build.

## Capture and verification

Capture at 1280 × 720 using the same viewport for every scene:

1. Initial page: `ALPHABETICAL` selects `feature/alarm-settings`, while `ACTIVE_BRANCH` selects `feature/playback-fix`.
2. Open `ALPHABETICAL`, press **End**, then **Enter**. The newest branch, `feature/playback-fix`, is the last entry alphabetically.
3. Focus `ACTIVE_BRANCH` at its initial `feature/playback-fix` selection.
4. Open it, press **Down**, then **Enter**: `feature/alarm-settings`.
5. Repeat: `feature/camera-preview`.
6. Open it, press **Home**, then **Enter**: `feature/playback-fix`. The two disabled options are skipped.

Verify the actual option values and disabled attributes before adding captions. Expected `ACTIVE_BRANCH` options, in order:

```text
release/1.0              disabled
main                     disabled
feature/playback-fix      enabled
feature/alarm-settings    enabled
feature/camera-preview    enabled
```

The two always-included branches occupy two of the five slots and come first when the limit applies; the remaining three slots contain the latest feature branches. Do not describe all five entries as globally sorted by commit date.

For this capture, screenshots were cropped consistently below the controls, resized to 1152 × 495, and placed above a separate caption area. The Jenkins UI content was not altered. Six frames were encoded as a 1152 × 650 GIF with durations of 3.5, 4.5, 5.5, 3.5, 3.5, and 5 seconds. The static preview is scene 3.
