# リリースの流れ

`main` に取り込まれたときに `.github/workflows/release.yml` が動き、版が新しければタグと GitHub Release を作る。仕組みを変えたら、同じ PR でこの図を直す。

- 版は `build.gradle` の `version`。タグ `v<版>` がもうあれば何もしない（版を上げていない Merge・文書だけの Merge）。
- ビルドかテストが失敗したら、タグもリリースも作らない。版のずれ（`build.gradle`・`SafetySignsMod.VERSION`・`CHANGELOG.md` の最新の見出し）は `ReleaseFilesTest` で落ちるので、ふつうは PR の CI（`ci.yml`）で先に見つかる。
- タグは `gh release create` が Release と一緒に、取り込んだコミットに付ける。権限は `contents: write` だけ。

```mermaid
flowchart TD
    Merge["main に push<br/>PR の Merge"] --> Read["build.gradle の version を読む"]
    Read --> Tag{"タグ v版 が<br/>もうある?"}
    Tag -- ある --> Skip["何もしない"]
    Tag -- ない --> Build["./gradlew build<br/>ビルドとテスト<br/>ReleaseFilesTest を含む"]
    Build -- 失敗 --> Stop["止まる<br/>タグも Release も作らない"]
    Build -- 成功 --> Notes["CHANGELOG.md の<br/>## 版 の節を取り出す"]
    Notes -- 節がない・空 --> Stop
    Notes --> Release["gh release create v版<br/>タグを付けて Release を作る<br/>safetysigns-1.12.2-版.jar を添付"]
```
