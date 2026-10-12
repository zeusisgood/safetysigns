# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

ISO 7010 の安全標識をモチーフにした、装飾用の警告看板を追加する Mod「Safety Signs」（mod ID `safetysigns`）。対象は **Minecraft Java Edition 1.12.2 / Forge 14.23.5.2860**。看板は見た目だけで、ゲーム上の効果はない。ISO とは無関係の非公式 Mod。

- 仕様書は `doc/spec/`。正本は `SPEC_2026-10-12.md`（MVP）。以後の版の仕様書は日付のファイル名で足し、`doc/spec/README.md` の表の一番上に 1 行の要約を足す。仕様書に書かれていないことは、その前の版と現行実装のまま。過去の仕様書が実装と違うと分かったら、本文は書き換えずに「（注: 実装では…）」を足す。
- 詳しいことは `doc/` にある（必要なときに読む）:

| 内容 | 文書 |
|---|---|
| 仕組み・全体構成と主要な流れの図 | `doc/architecture.md` |
| 条件付きの図 | `doc/diagrams/` |
| 設計の決定の記録 | `doc/adr/` |
| 後回しにした作業 | `doc/TODO.md` |
| リソースパックで図柄を追加する方法 | `doc/resourcepack.md` |
| テクスチャの一覧画像 | `doc/textures/preview.png` |

## 開発環境・コマンド

- **JDK 8**、ForgeGradle 3 + Gradle 4.9（Gradle 5 以降の構文は使えない。依存は `compile` / `testCompile`）。構成は `zeusisgood/weakspot` と同じにする。
- ビルド + テスト: `./gradlew build` → `build/libs/safetysigns-1.12.2-<version>.jar`（reobf 済み）。テストのみ: `./gradlew test`。
- テクスチャ生成: `python3 tools/texgen/generate.py`（Pillow）。生成した PNG と `doc/textures/preview.png` はコミットする。ビルドに Python を持ち込まない。
- 描画・GUI の見た目・設置の手触りは Claude が確かめられない。jar をユーザーのホスト側 Minecraft と testserver（マルチ）で試してもらう。

## バージョン

- 版は `build.gradle` の `version` と `SafetySignsMod.VERSION` の 2 か所。必ず揃える。
- 初版は `0.1.0`。公開する時点で `1.0.0`。
- 機能の追加・不具合の修正ごとに**パッチ**。互換性を破るとき（通信内容の変更、古い版で読めない保存データ、**図柄 ID の削除・意味の変更**、設定キーの削除・意味の変更）は**マイナー**。迷ったらマイナー。
- 図柄 ID は一度出したら変えない・消さない（既存ワールドの看板の見た目が変わるため）。
- 文書だけの変更は版を上げない。
- `@Mod` の `acceptableRemoteVersions` で同じマイナー同士を接続可能にする。マイナーを上げたら範囲も書き換え、`CHANGELOG.md` に旧マイナーとは接続できないことを書く。

## アーキテクチャの約束事

**コードに触る前に `doc/architecture.md` の関係する所を読む。仕組みを変えたら、同じ PR でそちらと図を直す。**

- クライアントとサーバーの**両方に Mod が必要**。パッケージは `io.github.zeusisgood.safetysigns`。
- `common/` は Minecraft に依存しない純粋な計算だけ（MC クラスを持ち込まない）。単体テストはここ（複数ブロックの設置判定・検索の絞り込み・定義ファイルのマージ・図柄 ID の形の確認）。
- 図柄は NBT に**図柄 ID の文字列**（`<namespace>:<名前>`）で持つ。番号にしない（リソースパックで図柄が増減しても壊れないように）。
- 図柄の定義ファイルはクライアントだけが読む（`IResourceManager.getAllResources` で全パックをマージ）。サーバーは図柄の一覧を知らず、ID の文字列を保存・同期するだけ。
- 一覧にない ID は「?」の図柄で表示し、ID は消さずに保持する。
- **サーバーはクライアントから届いた値を正にしない**。図柄 ID は形（`<namespace>:<名前>`、64 文字まで）を確かめ、看板が近くにあるか（届く距離か）も確かめる。
- 複数ブロックの看板は、図柄を代表の 1 マスが持つ。1 マスが壊れたら全体を消し、アイテムは 1 個だけ落とす。
- 画面の文字列は `en_us.lang` と `ja_jp.lang` の両方に足す。図柄の名前は定義ファイルに日英で持つ。
- 設定は `@Config`（`config/safetysigns.cfg`）。項目の名前は変えない。コメントの先頭に `[サーバー]` / `[クライアント]` を書く。

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
- Merge はユーザーが行う。**タグは Merge を確認してから Claude が付ける**（`v<版>`）。GitHub Release は Actions が作る。
- PR のレビューは、普段はセッション内の `reviewer`。必要な PR だけユーザーが `@claude` で呼ぶ（`.github/workflows/claude.yml`）。
- 仕様書にもとづく作業は、承認を待たずに実装から push と PR まで進める。ただし**止まる条件**（互換性を破る変更が必要、仕様の意図が読み取れない、ビルドやテストが通らない）に当たったら、push せずに止まって報告する。
- リリースしたら、jar を `build/release/` にコピーしてユーザーに添付し、PR のリンクと「試してほしいこと」を渡す。

## ユーザーとの進め方

- ユーザーの要望は短い。「相談」「ラフ」と付いていれば、実装せずに相談から始める。相談では、案を並べ、おすすめを 1 つ示し、「決めてほしいこと」を番号付きで聞く。決まったら仕様書を書く。
- 下書きの仕様書は、ユーザーが「実装」と言うまで実装しない。

## 次の作業

MVP（仕様書の「9. 作業の順番」）。1〜3 は済み。次は 4（壁掛け看板 2×2、複数ブロック方式）。
