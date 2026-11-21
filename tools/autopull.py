#!/usr/bin/env python3
"""Keep this clone up to date with the team: every N seconds fetch `hub` and `origin`, then
fast-forward the current branch to its upstream and the local `main` to `origin/main`.

Safe by design: never creates merge commits, never rebases, resets or stashes, never touches
uncommitted work. When a fast-forward is impossible (local commits, conflicting local edits,
a merge or rebase in progress) it leaves everything as it is and says why, once.

    python tools/autopull.py                  # every 15 s until Ctrl+C
    python tools/autopull.py --every 30
    python tools/autopull.py --once           # one round, exit code 0
    pythonw tools/autopull.py --log .git/autopull.log   # Windows, no console window
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REMOTES = ("hub", "origin")
ENV = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_MERGE_AUTOEDIT="no")
# Under pythonw every console child (git, ssh) would otherwise flash its own terminal window.
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


class Pull:
    def __init__(self, repo, log_file=None):
        self.repo = Path(repo)
        self.log_file = log_file
        self.last = {}  # topic -> last message, so a waiting state is reported once

    def say(self, topic, message):
        if self.last.get(topic) == message:
            return
        self.last[topic] = message
        line = time.strftime("%H:%M:%S ") + message
        print(line, flush=True)
        if self.log_file:
            with open(self.log_file, "a", encoding="utf-8") as stream:
                stream.write(line + "\n")

    def git(self, *args):
        try:
            result = subprocess.run(["git", *args], cwd=self.repo, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", env=ENV, timeout=60,
                                    creationflags=NO_WINDOW)
        except subprocess.TimeoutExpired:
            return 124, "", "timed out"
        return result.returncode, result.stdout.strip(), result.stderr.strip()

    def busy(self):
        code, git_dir, _ = self.git("rev-parse", "--absolute-git-dir")
        if code:
            return "not a git repository"
        git_dir = Path(git_dir)
        for name, why in (("index.lock", "another git command is running"),
                          ("MERGE_HEAD", "a merge is in progress"), ("rebase-merge", "a rebase is in progress"),
                          ("rebase-apply", "a rebase is in progress"), ("CHERRY_PICK_HEAD", "a cherry-pick is in progress")):
            if (git_dir / name).exists():
                return why
        return None

    def round(self):
        why = self.busy()
        if why:
            self.say("state", "waiting: " + why)
            return
        self.last.pop("state", None)
        code, remotes, _ = self.git("remote")
        for remote in (r for r in REMOTES if r in remotes.split()):
            code, _, err = self.git("fetch", "--prune", "--quiet", remote)
            if code:
                self.say("fetch " + remote, f"fetch {remote} failed: {err.splitlines()[-1] if err else code}")
            else:
                self.last.pop("fetch " + remote, None)
        self.current_branch()
        self.local_main()

    def current_branch(self):
        code, branch, _ = self.git("symbolic-ref", "-q", "--short", "HEAD")
        code_u, upstream, _ = self.git("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
        if code or code_u:
            return  # detached HEAD or no upstream: fetching is all we do
        _, counts, _ = self.git("rev-list", "--left-right", "--count", f"HEAD...{upstream}")
        ahead, behind = (int(n) for n in counts.split())
        if not behind:
            self.last.pop("branch", None)
            return
        if ahead:
            self.say("branch", f"{branch}: {ahead} local and {behind} new commits on {upstream}; "
                               f"not touching it, merge by hand")
            return
        code, out, err = self.git("merge", "--ff-only", "--quiet", upstream)
        if code:
            reason = (err or out).splitlines()
            self.say("branch", f"{branch}: cannot fast-forward to {upstream} ({reason[0] if reason else code}); "
                               f"commit or move your local edits")
        else:
            _, head, _ = self.git("log", "-1", "--format=%h %an: %s")
            self.say("branch", f"{branch}: pulled {behind} commit(s) from {upstream}, now {head}")
            self.last.pop("branch", None)

    def local_main(self):
        _, branch, _ = self.git("symbolic-ref", "-q", "--short", "HEAD")
        if branch == "main":
            return  # handled as the current branch
        if self.git("rev-parse", "-q", "--verify", "refs/heads/main")[0] or \
                self.git("rev-parse", "-q", "--verify", "refs/remotes/origin/main")[0]:
            return
        _, counts, _ = self.git("rev-list", "--left-right", "--count", "main...origin/main")
        ahead, behind = (int(n) for n in counts.split())
        if behind and not ahead:
            # A refspec without "+" only accepts fast-forwards.
            code, _, err = self.git("fetch", "--quiet", ".", "refs/remotes/origin/main:refs/heads/main")
            self.say("main", f"main: fetch from origin/main failed: {err}" if code else f"main: pulled {behind} commit(s)")
            if not code:
                self.last.pop("main", None)
        elif behind and ahead:
            self.say("main", f"main: {ahead} local and {behind} new commits on origin/main; not touching it")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--every", type=float, default=15.0, help="seconds between rounds (default 15)")
    parser.add_argument("--once", action="store_true", help="one round, then exit")
    parser.add_argument("--repo", default=str(ROOT), help="repository (default: this clone)")
    parser.add_argument("--log", help="also append messages to this file")
    args = parser.parse_args(argv)
    pull = Pull(args.repo, args.log)
    if args.once:
        pull.round()
        return 0
    pull.say("start", f"autopull every {args.every:g} s in {Path(args.repo).resolve()}")
    try:
        while True:
            pull.round()
            time.sleep(max(args.every, 5.0))
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
