#!/usr/bin/env bash
# リリース：VERSION の版でビルドし、タグを付けて push、GitHub Releases に APK を上げる。
# 使い方: scripts/release.sh
#   前もって VERSION を上げ、CHANGELOG.md に「## <版>」の節を書き、PR で main に入れておく（CI が通ったものだけ main に入る）。
#   main に入ったコミットからだけリリースできる。
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
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "今のコミットが origin/main と違います。PR を main にマージしてから、main の最新でリリースしてください" >&2; exit 1; }

# CHANGELOG の「## 2.0.9」から次の「## 」までをリリースノートにする
notes=$(awk -v v="$version" '$0 ~ "^## " { if (found) exit; found = ($2 == v); next } found' CHANGELOG.md | sed '/./,$!d')
[[ -n "$notes" ]] || { echo "CHANGELOG.md に「## $version」の節がありません" >&2; exit 1; }
grep -q '^### ' <<<"$notes" || { echo "CHANGELOG.md の「## $version」に「### 🐛 直したこと」などの見出しがありません（書き方は CHANGELOG.md の先頭）" >&2; exit 1; }
# 最後にダウンロードの案内と、前の版からの差分のリンクを足す
prev=$(git tag -l 'v*' --sort=-v:refname | head -1)
notes+=$'\n\n---\n\n'"📦 [spotfy-$version.apk](https://github.com/$repo/releases/download/$tag/spotfy-$version.apk) を入れてください（アプリの設定の「アップデート」からも入れられます）"
[[ -n "$prev" ]] && notes+=$'\n\n'"全部の変更: [$prev...$tag](https://github.com/$repo/compare/$prev...$tag)"

./gradlew -q assembleRelease
# 起動まわりをエミュレーターで確かめてから出す（急ぐときだけ SKIP_SMOKE=1）
if [[ "${SKIP_SMOKE:-}" != 1 ]]; then scripts/smoke-test.sh || { echo "スモークテストに失敗したのでリリースを止めました" >&2; exit 1; }; fi
mkdir -p build
apk="build/spotfy-$version.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"

git tag -a "$tag" -m "$tag"
git push origin "$tag"
gh release create "$tag" "$apk" -R "$repo" --title "$tag" --notes "$notes"
echo "リリースしました: https://github.com/$repo/releases/tag/$tag"
