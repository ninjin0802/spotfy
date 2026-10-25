#!/usr/bin/env bash
# リリース：VERSION の版でビルドし、タグを付けて main とタグを push、GitHub Releases に APK を上げる。
# 使い方: scripts/release.sh   （前もって VERSION を上げ、CHANGELOG.md に「## <版>」の節を書いてコミットしておく）
# GitHub の操作は GH_TOKEN（ninjin0802 のトークン）で行う。
set -euo pipefail
cd "$(dirname "$0")/.."

version=$(tr -d '[:space:]' < VERSION)
tag="v$version"
repo="ninjin0802/spotfy"

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "VERSION が「数字.数字.数字」ではありません: $version" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "コミットしていない変更があります" >&2; exit 1; }
git fetch -q origin main --tags
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then echo "$tag はもうあります。VERSION を上げてください" >&2; exit 1; fi
git merge-base --is-ancestor origin/main HEAD || { echo "origin/main から分かれています。先に main を取り込んでください" >&2; exit 1; }

# CHANGELOG の「## 2.0.9」から次の「## 」までをリリースノートにする
notes=$(awk -v v="$version" '$0 ~ "^## " { if (found) exit; found = ($2 == v); next } found' CHANGELOG.md | sed '/./,$!d')
[[ -n "$notes" ]] || { echo "CHANGELOG.md に「## $version」の節がありません" >&2; exit 1; }

./gradlew -q assembleRelease
# 起動まわりをエミュレーターで確かめてから出す（急ぐときだけ SKIP_SMOKE=1）
if [[ "${SKIP_SMOKE:-}" != 1 ]]; then scripts/smoke-test.sh || { echo "スモークテストに失敗したのでリリースを止めました" >&2; exit 1; }; fi
mkdir -p build
apk="build/spotfy-$version.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"

git tag -a "$tag" -m "$tag"
git push origin HEAD:main "$(git rev-parse --abbrev-ref HEAD)" "$tag"
gh release create "$tag" "$apk" -R "$repo" --title "$tag" --notes "$notes"
echo "リリースしました: https://github.com/$repo/releases/tag/$tag"
