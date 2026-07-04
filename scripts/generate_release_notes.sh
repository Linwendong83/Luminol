#!/usr/bin/env bash
set -euo pipefail

upstream_repository="${UPSTREAM_REPOSITORY:-LuminolMC/Luminol}"
repository="${GITHUB_REPOSITORY:-Linwendong83/Luminol}"
ref_name="${SOURCE_REF_NAME:-${GITHUB_REF_NAME:-$(git branch --show-current 2>/dev/null || true)}}"
release_tag="${tag:-}"
release_version="${mcversion:-unknown}"
release_commit="${commit_id:-$(git log --pretty='%h' -1)}"
release_jar="${jar:-}"
release_comments="${RELEASE_COMMENTS:-}"

body_dir="${RUNNER_TEMP:-/tmp}"
mkdir -p "${body_dir}"
release_body_file="$(mktemp "${body_dir%/}/release-notes.XXXXXX.md")"

commit_url() {
  local repo="$1"
  local sha="$2"
  printf 'https://github.com/%s/commit/%s' "${repo}" "${sha}"
}

compare_url() {
  local repo="$1"
  local base="$2"
  local head="$3"
  printf 'https://github.com/%s/compare/%s...%s' "${repo}" "${base}" "${head}"
}

fetch_upstream_branch() {
  if [ -z "${ref_name}" ]; then
    return 1
  fi

  if ! git remote get-url upstream >/dev/null 2>&1; then
    git remote add upstream "https://github.com/${upstream_repository}.git"
  fi

  git fetch --no-tags upstream "${ref_name}:refs/remotes/upstream/${ref_name}" >/dev/null 2>&1
}

find_previous_tag() {
  local pattern="*"
  if [ "${release_version}" != "unknown" ]; then
    pattern="${release_version}-*"
  fi

  git tag --merged HEAD --sort=-creatordate -l "${pattern}" | while IFS= read -r candidate; do
    if [ "${candidate}" = "${release_tag}" ]; then
      continue
    fi

    printf '%s\n' "${candidate}"
    break
  done
}

write_commit_list() {
  local range="$1"
  local repo="$2"

  if [ "$(git rev-list --count "${range}" 2>/dev/null || printf '0')" = "0" ]; then
    printf -- '- No commit changes detected.\n'
    return
  fi

  git log --reverse --format='%H%x09%h%x09%s' "${range}" | while IFS=$'\t' read -r full_sha short_sha subject; do
    printf -- '- [%s](%s) %s\n' "${short_sha}" "$(commit_url "${repo}" "${full_sha}")" "${subject}"
  done
}

head_sha="$(git rev-parse HEAD)"
head_subject="$(git show -s --format=%s HEAD)"
read -r -a parents <<< "$(git show -s --format=%P HEAD)"

changes_title="Repository Commit Changes"
changes_repository="${repository}"
changes_range="HEAD"
compare_repository="${repository}"
compare_base=""
compare_head="${head_sha}"

if [ "${#parents[@]}" -ge 2 ]; then
  first_parent="${parents[0]}"
  second_parent="${parents[1]}"
  upstream_ref="refs/remotes/upstream/${ref_name}"
  is_upstream_sync=false

  if [[ "${head_subject}" == *"upstream/"* ]]; then
    is_upstream_sync=true
  elif fetch_upstream_branch && git merge-base --is-ancestor "${second_parent}" "${upstream_ref}"; then
    is_upstream_sync=true
  fi

  if [ "${is_upstream_sync}" = "true" ]; then
    changes_title="Upstream Commit Changes"
    changes_repository="${upstream_repository}"
    changes_range="${first_parent}..${second_parent}"
    compare_repository="${upstream_repository}"
    compare_base="$(git merge-base "${first_parent}" "${second_parent}" || true)"
    compare_head="${second_parent}"
  fi
fi

if [ "${changes_range}" = "HEAD" ]; then
  previous_tag="$(find_previous_tag || true)"
  if [ -n "${previous_tag}" ]; then
    changes_range="${previous_tag}..HEAD"
    compare_base="${previous_tag}"
  elif git rev-parse HEAD^ >/dev/null 2>&1; then
    changes_range="HEAD^..HEAD"
  fi
fi

{
  printf 'Version: `%s` | Commit [%s](%s)' "${release_version}" "${release_commit}" "$(commit_url "${repository}" "${head_sha}")"
  if [ -n "${release_tag}" ] && [ -n "${release_jar}" ]; then
    printf ' [![download](https://img.shields.io/github/downloads/%s/%s/total?color=red&style=flat-square)](https://github.com/%s/releases/download/%s/%s)' "${repository}" "${release_tag}" "${repository}" "${release_tag}" "${release_jar}"
  fi
  printf '\n'
  printf 'This release is automatically compiled by GitHub Actions.\n\n'

  if [ -n "${release_comments}" ]; then
    printf '### Comments\n'
    printf '> %s\n\n' "${release_comments//$'\n'/$'\n> '}"
  fi

  printf '### Branch Info\n'
  printf '> %s\n\n' "${ref_name:-unknown}"

  printf '### Build Commit\n'
  printf '> [%s](%s) %s\n\n' "${release_commit}" "$(commit_url "${repository}" "${head_sha}")" "${head_subject}"

  printf '### %s\n' "${changes_title}"
  write_commit_list "${changes_range}" "${changes_repository}"

  if [ -n "${compare_base}" ] && [ -n "${compare_head}" ]; then
    printf '\n**Full Changelog**: %s\n' "$(compare_url "${compare_repository}" "${compare_base}" "${compare_head}")"
  fi
} > "${release_body_file}"

if [ -n "${GITHUB_ENV:-}" ]; then
  printf 'release_body_file=%s\n' "${release_body_file}" >> "${GITHUB_ENV}"
else
  printf '%s\n' "${release_body_file}"
fi
