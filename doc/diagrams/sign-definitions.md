# 図柄の定義ファイルの読み込み

`client/SignTextures` が定義ファイルを読む流れ（0.2.0。1.20.1・1.21.1 は 1.1.0 から、下の「1.20.1・1.21.1」）。クライアントだけ。仕組みを変えたら、同じ PR でこの図を直す。

- ブロックのテクスチャを貼り直すとき（起動・F3+T・リソースパックの変更）に、毎回読み直す。
- 各名前空間の `assets/<namespace>/safetysigns/signs.json` を、`getAllResources` で全パックから集める（優先度の低い順）。
- 項目の ID の名前空間は、ファイルの名前空間と同じでなければ飛ばす。同じ ID は優先度の高いパックで丸ごと上書き。
- 形のおかしい項目・ファイルはログに警告を出して飛ばす（ほかの項目は使う）。`category` が知らない値なら、飛ばさずに警告を出して `OTHER`（その他のタブ）として読む（1.0.2）。
- アイテムの図柄入りモデルは、テクスチャの貼り直しのあとのモデルの作り直し（`ModelBakeEvent`）で古いものが捨てられる。

```mermaid
flowchart TD
    Reload["テクスチャの貼り直し<br/>TextureStitchEvent.Pre"] --> Domains["全名前空間を順に"]
    Domains --> All["getAllResources<br/>safetysigns/signs.json"]
    All --> Parse["SignDefinitionParser<br/>1 ファイルずつ"]
    Parse -- 形のおかしい項目 --> Warn["ログに警告して飛ばす"]
    Parse -- 知らない category --> Other["ログに警告して<br/>OTHER として読む"]
    Other --> Merge
    Parse --> Merge["SignCatalog.merge<br/>同じ ID は後のパックが勝つ"]
    Merge --> Catalog["SignTextures の一覧を差し替え"]
    Catalog --> Sprites["図柄・無地・「?」の<br/>テクスチャを登録"]
    Sprites --> Bake["ModelBakeEvent<br/>SignItemModel を作り直す"]
    Catalog --> Use["GUI・SignRenderer・<br/>ツールチップが参照"]
```

## 1.20.1・1.21.1

- 図柄のテクスチャは、ブロックの atlas が自分で集める（`assets/minecraft/atlases/blocks.json` の `directory` のソースで、全部のパック・全部の名前空間の `textures/signs/` の下）。`SignTextures` はテクスチャを登録しない。
- 定義ファイルは、リソースの読み直しのたびに読む（`RegisterClientReloadListenersEvent` に登録した `SignTextures`）。集め方（`getResourceStack`。優先度の低い順）・マージ・警告は 1.12.2 と同じ。
- 定義ファイルの texture が `signs/` の下でない図柄は、atlas に入らないので「?」で描き、ログに 1 回警告を出す（ID は消さない）。
- 読み直すたびに数を上げ（`generation`）、`SignRenderer` が取っておいた四角の並びを作り直す。

```mermaid
flowchart TD
    Reload["リソースの読み直し<br/>起動・F3+T・パックの変更"] --> Atlas["ブロックの atlas<br/>textures/signs/ の下を全部集める"]
    Reload --> Listener["SignTextures<br/>onResourceManagerReload"]
    Listener --> Stack["全名前空間の<br/>getResourceStack<br/>safetysigns/signs.json"]
    Stack --> Parse["SignDefinitionParser<br/>1 ファイルずつ"]
    Parse --> Merge["SignCatalog.merge<br/>同じ ID は後のパックが勝つ"]
    Merge --> Check{"texture が<br/>signs/ の下?"}
    Check -- いいえ --> Unknown["ログに警告<br/>「?」で描く"]
    Check -- はい --> Outline["PNG から板の形<br/>SignOutline"]
    Outline --> Catalog["一覧と形を差し替え<br/>generation を上げる"]
    Unknown --> Catalog
    Atlas --> Use
    Catalog --> Use["GUI・SignRenderer・<br/>SignItemModel・ツールチップ"]
```
