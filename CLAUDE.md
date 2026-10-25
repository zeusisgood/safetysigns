# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

ISO 7010 の安全標識をモチーフにした、装飾用の警告看板を追加する Mod「Safety Signs」（mod ID `safetysigns`）。対象は **Minecraft Java Edition 1.12.2 / Forge 14.23.5.2860**・**1.20.1 / Forge 47.1.32**（下限も 47.1.32）・**1.21.1 / NeoForge 21.1.264** の 3 つ（機能は揃え続ける）。看板は見た目だけで、ゲーム上の効果はない。ISO とは無関係の非公式 Mod。

- 仕様書は `doc/spec/`。正本は `SPEC_2026-10-12.md`（MVP）。以後の版の仕様書は日付のファイル名で足し、`doc/spec/README.md` の表の一番上に 1 行の要約を足す。仕様書に書かれていないことは、その前の版と現行実装のまま。過去の仕様書が実装と違うと分かったら、本文は書き換えずに「（注: 実装では…）」を足す。
- 詳しいことは `doc/` にある（必要なときに読む）:

| 内容 | 文書 |
|---|---|
| 仕組み・全体構成と主要な流れの図 | `doc/architecture.md` |
| 条件付きの図 | `doc/diagrams/` |
| 設計の決定の記録 | `doc/adr/` |
| 後回しにした作業 | `doc/TODO.md` |
| リソースパックで図柄を追加する方法 | `doc/resourcepack.md` |
| テクスチャの生成・本体に図柄を足す手順 | `doc/development.md` |
| テクスチャの一覧画像 | `doc/textures/preview.png`（部品の一覧は `doc/textures/parts.png`、アイテムのアイコンは `doc/textures/items.png`） |
| CurseForge・Modrinth の説明文 | `doc/store/description.md` |

## 開発環境・コマンド

- 1.12.2 は **JDK 8**、ForgeGradle 3 + Gradle 4.9（Gradle 5 以降の構文は使えない。依存は `compile` / `testCompile`）。1.20.1 は **JDK 17**、ForgeGradle 6。1.21.1 は **JDK 21**、ModDevGradle。構成は `zeusisgood/weakspot` の同じ版のディレクトリと同じにする。
- リポジトリの形: `common/`（版に依存しない計算とそのテスト。各版のビルドがソースで読む）・`lang/`（翻訳の正本。json）・`shared/`（全部の版で同じリソース: テクスチャ・定義ファイル `signs.json`・ロゴ）・`mc1.12.2/`・`mc1.20.1/`・`mc1.21.1/`（各版のビルド。ブロックの状態・モデル・レシピ・メタデータとコード）。版ごとのディレクトリは別々の Gradle のビルド（1 つのマルチプロジェクトにできない）。`doc/`・`tools/` などは直下で全部の版に共通。
- ビルド + テスト（版のディレクトリの中で）: `./gradlew build` → `mc1.12.2/build/libs/safetysigns-1.12.2-forge-<version>.jar`（reobf 済み）・`mc1.20.1/build/libs/safetysigns-1.20.1-forge-<version>.jar`（reobf 済み）・`mc1.21.1/build/libs/safetysigns-1.21.1-neoforge-<version>.jar`。テストのみ: `./gradlew test`（作業ディレクトリはリポジトリの直下。`common/` のテストはどの版のビルドでも流れる）。クラウドでの JDK の入れ方・許可するホストは `doc/development.md` の「ビルドする」。
- テクスチャ生成: `python3 tools/texgen/generate.py`（Pillow。解像度は `--size`、既定 128。内蔵の図柄はすべて 128×128）。生成した PNG と `doc/textures/preview.png`・`doc/textures/parts.png`・`doc/textures/items.png`・`doc/store/icon.png` はコミットする。ビルドに Python を持ち込まない。
- 描画・GUI の見た目・設置の手触りは Claude が確かめられない。jar をユーザーのホスト側 Minecraft と testserver（マルチ）で試してもらう。

## バージョン

- 版は `mc1.12.2/build.gradle` の `version`・`mc1.20.1/gradle.properties` と `mc1.21.1/gradle.properties` の `mod_version`・3 つの版の `SafetySignsMod.VERSION` の 6 か所。必ず揃え、`CHANGELOG.md` の一番上の見出しも同じ版にする（ずれは `resources/ReleaseFilesTest` で CI が落ちる）。**1 つの版だけを直したときも、いつも 3 つの jar を同じ番号で出す**。
- Minecraft の版を足すのは**マイナー**。
- 初版は `0.1.0`。公開する時点で `1.0.0`。
- 機能の追加・不具合の修正ごとに**パッチ**。互換性を破るとき（通信内容の変更、古い版で読めない保存データ、**図柄 ID の削除・意味の変更**、設定キーの削除・意味の変更）は**マイナー**。迷ったらマイナー。
- 図柄 ID は一度出したら変えない・消さない（既存ワールドの看板の見た目が変わるため）。
- 文書だけの変更は版を上げない。
- 1.12.2 は `@Mod` の `acceptableRemoteVersions`（`SafetySignsMod.ACCEPTED_VERSIONS`）、1.20.1・1.21.1 は `SafetySignsNetwork.PROTOCOL`（マイナーの番号）で同じマイナー同士を接続可能にする。マイナーを上げたら両方を書き換え（`ReleaseFilesTest` が確かめる）、`CHANGELOG.md` に旧マイナーとは接続できないことを書く。

## アーキテクチャの約束事

**コードに触る前に `doc/architecture.md` の関係する所を読む。仕組みを変えたら、同じ PR でそちらと図を直す。**

- クライアントとサーバーの**両方に Mod が必要**。パッケージは `io.github.zeusisgood.safetysigns`。
- **機能を足す・直すときは、3 つの版で同じ PR で行う**（揃え続ける）。版に依存しない部分は先に `common/` に書く。クラスの名前と並びは 3 つの版で揃える（版ごとに違う所は `doc/architecture.md` の「版ごとの違い」）。
- `common/` は Minecraft に依存しない純粋な計算だけ（MC クラスを持ち込まない。1.12.2 版を残す限り Java 8 の書き方）。単体テストはここ（複数ブロックの設置判定・検索の絞り込み・定義ファイルのマージ・図柄 ID の形の確認・板の四角の並び）と、ファイルの揃い具合の `resources/ReleaseFilesTest`・`LangFilesTest`。
- 図柄は NBT（1.21.1 のアイテムはデータコンポーネント）に**図柄 ID の文字列**（`<namespace>:<名前>`）で持つ。番号にしない（リソースパックで図柄が増減しても壊れないように）。
- 図柄の定義ファイルはクライアントだけが読む（全パックをマージ。1.12.2 は `getAllResources`、新しい版は `getResourceStack`）。新しい版の図柄のテクスチャは `textures/signs/` の下だけ（atlas が集める）。サーバーは図柄の一覧を知らず、ID の文字列を保存・同期するだけ。
- 一覧にない ID は「?」の図柄で表示し、ID は消さずに保持する。
- **サーバーはクライアントから届いた値を正にしない**。図柄 ID は形（`<namespace>:<名前>`、64 文字まで）を確かめ、看板が近くにあるか（届く距離か）も確かめる。
- 複数ブロックの看板は、図柄を代表の 1 マスが持つ。1 マスが壊れたら全体を消し、アイテムは 1 個だけ落とす。
- 画面の文字列は `lang/en_us.json` と `lang/ja_jp.json` の両方に足す（1 行に 1 つの `"キー": "値"`。キーは 1.13 以降の形で、ブロックの名前は `block.safetysigns.<名前>`。1.12.2 の `.lang` はビルドのときに作る。日英のキーの一致は `resources/LangFilesTest`）。図柄の名前は定義ファイルに日英で持つ。
- 設定は `@Config`（`config/safetysigns.cfg`）。項目の名前は変えない。コメントの先頭に `[サーバー]` / `[クライアント]` を書く（今は設定の項目がない。新しい版で足すときも、キーの名前は 3 つの版で揃える）。

## 開発ルール

- **コミットメッセージは英語**、Git 本来の慣習（`Add` / `Fix` / `Update` などの動詞で始める。Conventional Commits は使わない）。**コードのコメントは日本語**。
- 後回しにした作業は `doc/TODO.md` に書く。
- 依存の追加は、PR の本文に理由を書けばしてよい。
- テストは、純粋な計算と、不具合を直したときの再発防止だけ。
- 秘密情報はコミットしない（必要なら `.env.example` だけ置く）。
- 設計の決定は軽い ADR（`doc/adr/NNNN-<英語の題>.md`、連番）に残す。

### 図（Mermaid）

- 必須は 2 図: 全体構成・主要な流れ（`doc/architecture.md` に埋め込む）。条件付きの図（GUI の画面遷移、図柄の読み込みの流れなど）は `doc/diagrams/`。
- 1 図あたりノード 20 個まで。
- 仕組みを変える PR では図も直す。レビュー用サブエージェントが、図とコードが一致しているかを確かめる。

### サブエージェント（`.claude/agents/`）

| 役割 | モデル | ツール |
|---|---|---|
| 探索（`explorer`） | haiku | 読み取りだけ |
| ログ要約（`log-summarizer`） | haiku | 読み取りだけ |
| レビュー（`reviewer`） | sonnet | 読み取りだけ |

- **PR を作る前に必ず `reviewer` でレビューする**。
- 詳しい書き方は公式の文書（https://code.claude.com/docs/en/sub-agents）に従う。

## ブランチと PR

- 長く残すブランチは `main` だけ。**作業ごとに `main` から新しいブランチを切る**（名前は `claude/<内容が分かる英語>`）。
- **Claude がしてはいけないこと**: force push、`main` への push、履歴の書き換え、リポジトリの外のファイルの削除、PR の Merge、ブランチの削除、`.env` の読み取り。
- 渡せる状態になったら、Claude が `main` への PR を作る。本文は、変えたこと・試してほしいこと・直さなかった文書とその理由。
- CI（`.github/workflows/ci.yml` の `build`）の成功が Merge に必須。自分が作った PR の CI が赤くなったら直して push する。
- PR を出したあとは見張らない。CI の結果は、聞かれたときと次の作業を始めるときに確かめる。
- Merge はユーザーが行う。**タグと GitHub Release は、`main` に取り込まれたあとに Actions が作る。Claude はタグを付けない**（`.github/workflows/ci.yml` の `release` ジョブ、`v<版>`。手順は `doc/development.md` の「リリースの手順」）。
- PR のレビューは、普段はセッション内の `reviewer`。必要な PR だけユーザーが `@claude` で呼ぶ（`.github/workflows/claude.yml`）。
- 仕様書にもとづく作業は、承認を待たずに実装から push と PR まで進める。ただし**止まる条件**（互換性を破る変更が必要、仕様の意図が読み取れない、ビルドやテストが通らない）に当たったら、push せずに止まって報告する。
- リリースしたら、3 つの jar を `mc1.12.2/build/release/` にコピーしてユーザーに添付し、PR のリンクと「試してほしいこと」を渡す。

## ユーザーとの進め方

- ユーザーの要望は短い。「相談」「ラフ」と付いていれば、実装せずに相談から始める。相談では、案を並べ、おすすめを 1 つ示し、「決めてほしいこと」を番号付きで聞く。決まったら仕様書を書く。
- 下書きの仕様書は、ユーザーが「実装」と言うまで実装しない。

## 次の作業

MVP（仕様書の「9. 作業の順番」）は 1〜8 まで済み（8 のレシピは SPEC_2026-10-12-5 で変えた）。1.0.0 を公開したら、次の作業は `doc/TODO.md` からユーザーと決める。1.1.0 で 1.20.1 + Forge・1.21.1 + NeoForge に対応した（SPEC_2026-10-25-2）。
