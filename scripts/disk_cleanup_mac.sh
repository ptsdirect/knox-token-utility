#!/usr/bin/env bash
set -euo pipefail

# macOS developer disk cleanup helper
# This script prints commands and prompts before executing destructive actions.
# Review each step. Comment out anything you do NOT want.

confirm() {
  read -r -p "[?] $1 (y/N) " ans
  case ${ans:-N} in
    [yY][eE][sS]|[yY]) return 0 ;;
    *) echo "Skipped"; return 1 ;;
  esac
}

echo "== Disk Usage Snapshot =="
df -h | head -n 5

echo "== Largest Directories in $HOME (Top 20) =="
# Exclude Library/Containers to reduce noise; adjust as needed
du -x -h -d 2 "$HOME" 2>/dev/null | sort -h | tail -n 20

# 1. Xcode Derived Data
if confirm "Remove Xcode DerivedData?"; then
  rm -rf ~/Library/Developer/Xcode/DerivedData/* || true
fi

# 2. iOS Simulator caches (SAFE: only caches, not runtimes)
if confirm "Clear CoreSimulator caches?"; then
  rm -rf ~/Library/Developer/CoreSimulator/Caches/* || true
fi

# 3. NPM cache (if present)
if command -v npm >/dev/null 2>&1; then
  if confirm "Prune npm cache?"; then
    npm cache clean --force || true
  fi
fi

# 4. Homebrew cleanup
if command -v brew >/dev/null 2>&1; then
  if confirm "Run brew cleanup?"; then
    brew cleanup -s || true
  fi
fi

# 5. Maven local repo partial prune (keeps recent 30 accessed artifacts)
if [ -d "$HOME/.m2/repository" ]; then
  if confirm "Prune old Maven artifacts (using find access time)?"; then
    find "$HOME/.m2/repository" -type f -atime +30 -delete || true
  fi
fi

# 6. System logs (non-critical user-level)
if confirm "Clear user DiagnosticReports?"; then
  rm -rf ~/Library/Logs/DiagnosticReports/* || true
fi

# 7. User caches (broad). This can slow first launches afterward.
if confirm "Clear ~/Library/Caches (broad)?"; then
  rm -rf ~/Library/Caches/* || true
fi

# 8. Docker system prune
if command -v docker >/dev/null 2>&1; then
  if confirm "Docker system prune (ALL: -a)?"; then
    docker system prune -af || true
  fi
fi

echo "== Post-Cleanup Disk Usage =="
df -h | head -n 5

echo "Done. Consider re-running: mvn clean verify"
