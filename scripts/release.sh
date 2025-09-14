#!/usr/bin/env bash
set -euo pipefail

# Knox Token Utility Release Helper
# Automates: version bump (Maven), commit, annotated tag, push.
# Usage:
#   ./scripts/release.sh <new-version> [--dry-run] [--no-push] [--sign] [--main-branch=main]
# Examples:
#   ./scripts/release.sh 1.0.1
#   ./scripts/release.sh 1.1.0 --sign
#   ./scripts/release.sh 1.0.1 --dry-run
#
# Notes:
# - Creates annotated tag v<new-version>
# - Requires a clean working tree
# - Uses Maven Versions Plugin (invoked automatically)
# - Does NOT publish to Maven Central; only git/tag push (GitHub Actions then builds release)
# - You must have already added a remote named 'origin'
#
# Exit codes:
# 0 success; non‑zero indicates failure stage.

color() { local c="$1"; shift; printf "\033[%sm%s\033[0m\n" "$c" "$*"; }
info() { color 36 "[INFO] $*"; }
success() { color 32 "[OK] $*"; }
warn() { color 33 "[WARN] $*"; }
error() { color 31 "[ERR] $*" 1>&2; }

usage() { sed -n '1,40p' "$0" | grep -E '^(# |#!/)' | sed -E 's/^# ?//; s/^#!/Usage:/' ; }

if [[ ${1:-} == "-h" || ${1:-} == "--help" || $# -lt 1 ]]; then
  usage
  exit 0
fi

NEW_VERSION="$1"; shift || true
DRY_RUN=false
NO_PUSH=false
SIGN_TAG=false
MAIN_BRANCH="main"

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --no-push) NO_PUSH=true ;;
    --sign) SIGN_TAG=true ;;
    --main-branch=*) MAIN_BRANCH="${arg#*=}" ;;
    *) error "Unknown option: $arg"; exit 2 ;;
  esac
done

if [[ ! $NEW_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9._]+)?$ ]]; then
  error "Version must match SemVer core pattern: X.Y.Z or X.Y.Z-qualifier"
  exit 2
fi

if ! command -v mvn >/dev/null 2>&1; then
  error "Maven (mvn) not found in PATH"
  exit 3
fi
if ! command -v git >/dev/null 2>&1; then
  error "git not found in PATH"
  exit 3
fi

# Ensure inside project root (pom.xml present)
if [[ ! -f pom.xml ]]; then
  error "Run from project root containing pom.xml"
  exit 4
fi

# Verify clean working tree
if ! git diff --quiet || ! git diff --cached --quiet; then
  error "Working tree not clean. Commit or stash changes first."
  exit 5
fi

CURRENT_VERSION=$(xmllint --xpath "/*[local-name()='project']/*[local-name()='version']/text()" pom.xml 2>/dev/null || true)
if [[ -z $CURRENT_VERSION ]]; then
  # Fallback grep (less precise but avoids plugin dependency)
  CURRENT_VERSION=$(grep -m1 -E '<version>[0-9]+' pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
fi
info "Current version: $CURRENT_VERSION -> New version: $NEW_VERSION"

if git rev-parse -q --verify "refs/tags/v$NEW_VERSION" >/dev/null; then
  error "Tag v$NEW_VERSION already exists"
  exit 6
fi

if $DRY_RUN; then
  warn "Dry run mode: no file changes, commits, or pushes will occur"
fi

# Set new version using Maven Versions Plugin (downloaded automatically)
if ! $DRY_RUN; then
  info "Setting Maven project version to $NEW_VERSION"
  mvn -q versions:set -DnewVersion="${NEW_VERSION}" -DgenerateBackupPoms=false
fi

# Confirm change
NEW_POM_VERSION=$(xmllint --xpath "/*[local-name()='project']/*[local-name()='version']/text()" pom.xml 2>/dev/null || true)
if [[ -z $NEW_POM_VERSION ]]; then
  NEW_POM_VERSION=$(grep -m1 -E '<version>[0-9]+' pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
fi
if [[ $NEW_POM_VERSION != $NEW_VERSION ]]; then
  if $DRY_RUN; then
    info "(Dry) Would have set pom.xml version to $NEW_VERSION"
  else
    error "pom.xml version mismatch after update: expected $NEW_VERSION got $NEW_POM_VERSION"
    exit 7
  fi
fi

if ! $DRY_RUN; then
  info "Creating commit for version bump"
  git add pom.xml
  git commit -m "chore: release ${NEW_VERSION}"
fi

TAG_CMD=(git tag -a "v${NEW_VERSION}" -m "Release ${NEW_VERSION}")
$SIGN_TAG && TAG_CMD=(git tag -s "v${NEW_VERSION}" -m "Release ${NEW_VERSION}")

if ! $DRY_RUN; then
  "${TAG_CMD[@]}"
else
  info "(Dry) Would create annotated tag v${NEW_VERSION}"
fi

if ! $NO_PUSH && ! $DRY_RUN; then
  info "Pushing branch $MAIN_BRANCH and tag v${NEW_VERSION} to origin"
  git push origin "$MAIN_BRANCH"
  git push origin "v${NEW_VERSION}"
else
  info "Skipped push (NO_PUSH=$NO_PUSH DRY_RUN=$DRY_RUN). Push manually when ready:"
  echo "  git push origin $MAIN_BRANCH"
  echo "  git push origin v${NEW_VERSION}"
fi

success "Release workflow complete for version $NEW_VERSION"

cat <<EOF
Next steps:
  - Monitor GitHub Actions for release/tag workflow success.
  - Verify artifacts attached to the GitHub Release (fat JAR, checksums).
  - Optionally create a CHANGELOG entry for $NEW_VERSION.
EOF
