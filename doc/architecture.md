# 仕組み（アーキテクチャ）

現在の実装（1.0.2、仕様書「9. 作業の順番」の 8 までと [SPEC_2026-10-12-2](spec/SPEC_2026-10-12-2.md)〜[SPEC_2026-10-12-8](spec/SPEC_2026-10-12-8.md)）の全体構成と主要な流れ。
仕組みを変えたら、同じ PR でこの文書と図を直す。条件付きの図は [`diagrams/`](diagrams/) にある。

## リポジトリの形

Minecraft の版ごとのディレクトリ（`mc<版>/`）は、それぞれ別々の Gradle のビルド（ForgeGradle の版が版ごとに違い、1 つのマルチプロジェクトにできない）。版に依存しないものは直下に置き、各版のビルドがソースとして読む（[ADR 0015](adr/0015-support-three-minecraft-versions-in-one-repository.md)）。

| ディレクトリ | 中身 |
|---|---|
| `common/` | Minecraft に依存しない計算（下の表の `common/`）とそのテスト、ファイルの揃い具合のテスト（`resources/ReleaseFilesTest`・`LangFilesTest`） |
| `lang/` | 翻訳の正本（`en_us.json`・`ja_jp.json`。キーは 1.13 以降の形）。1.12.2 はビルドのときに `.lang` を作る（ブロックの名前のキー `block.safetysigns.<名前>` を `tile.safetysigns.<名前>.name` にする） |
| `shared/` | 全部の版で同じリソース: テクスチャ（`textures/signs`・`block`・`item`）、図柄の定義ファイル `signs.json`、ロゴ。`tools/texgen/generate.py` が書き出す |
| `mc1.12.2/` | 1.12.2 + Forge のコードと、版で形が違うリソース（ブロックの状態・モデル・レシピ・`mcmod.info`） |

```mermaid
flowchart LR
    Texgen["tools/texgen<br/>generate.py"] --> Shared["shared/<br/>テクスチャ・signs.json・ロゴ"]
    Common["common/<br/>計算とテスト"] --> B112
    Lang["lang/*.json"] -- .lang に変換 --> B112
    Shared --> B112["mc1.12.2/<br/>./gradlew build"]
    B112 --> Jar["safetysigns-1.12.2-版.jar"]
```

## 全体構成

- クライアントとサーバーの両方に Mod が必要（`@Mod` の `acceptableRemoteVersions` で同じマイナー同士だけ接続できる）。
- 看板の図柄は、TileEntity とアイテムの NBT に**図柄 ID の文字列**で持つ（[ADR 0001](adr/0001-store-sign-pattern-as-string-id.md)）。看板ごとの切り抜き（ON/OFF）・裏面（銀/図柄）・夜間に光るか（ON/OFF）も同じ NBT に持ち、4 つをまとめて `common/SignAppearance` として持ち運ぶ（[ADR 0007](adr/0007-keep-cutout-and-back-face-per-sign.md)、[ADR 0008](adr/0008-keep-night-glow-per-sign.md)）。
- 図柄の一覧（定義ファイル）はクライアントだけが読む。サーバーは ID の文字列を保存・同期し、届いた ID の形と距離を確かめるだけ（[ADR 0002](adr/0002-load-sign-definitions-on-texture-stitch.md)）。
- 図柄の分類（GUI のタブ）は ISO 7010 の W P M E F と GHS・その他（`common/SignCategory`）。定義ファイルの知らない分類は「その他」として読む（[ADR 0012](adr/0012-read-unknown-categories-as-other.md)）。
- 看板は 1 マス以上を使う（壁掛け 1×1・2×2、ポール付き）。図柄は代表のマス（正面から見て左下。ポール付きは最下段の支柱）の TileEntity だけが持ち、ほかのマスは部品の番号だけを状態に持つ（[ADR 0004](adr/0004-multiblock-signs-with-a-representative-cell.md)、[ADR 0005](adr/0005-pole-sign-keeps-the-pattern-in-the-pole-cell.md)、[`diagrams/multiblock.md`](diagrams/multiblock.md)）。
- 見た目: 看板の板は、代表のマスの TileEntity の描画（`SignRenderer`）が、図柄を描く面（ブロックの `getFaceRow`・`getFaceZ`。壁掛けは看板全体、ポール付きは最上段の板）に描く。切り抜き ON は図柄のテクスチャの形、OFF は四角い板で、無地の下地の上に図柄を重ねる。裏は銀なら灰色、図柄なら表と同じ図柄を左右反転して貼る。縁はいつも灰色（形は `common/SignOutline` がテクスチャのアルファから作る。組み立てた板はテクスチャ・看板の種類・切り抜き・裏面ごとにディスプレイリストに取っておき、テクスチャを貼り直したら作り直す。[ADR 0006](adr/0006-draw-the-plate-cut-to-the-pattern-shape.md)）。リストは 2 つ続きで、1 つ目が下地・裏・縁、2 つ目が表の図柄。夜間に光る看板は、2 つ目だけをライトマップの最大（240, 240）で描く（周りは照らさない。下地・裏・縁・ポール・金具は周りの明るさ）。クライアントの全体設定（`@Config`）はない。ブロックモデル（JSON）は板を持たない（壁掛けは何も描かない。ポール付きは支柱と固定金具）。無地のアイテムは種類ごとのアイコン（`textures/item/`、32×32）。図柄を持つアイテムは `SignItemModel` が NBT に合わせて差し替える（切り抜き ON は図柄のテクスチャ 1 枚、OFF は無地の下地 + 図柄の 2 枚重ね。裏面はアイコンに出さない）。
- テクスチャ（図柄・無地・「?」・裏・支柱）は `tools/texgen/` のスクリプトが作り、PNG をコミットする（ビルドに Python は要らない）。内蔵の図柄はすべて 128×128（`--size` の既定。図柄ごとに倍率を変える `hires` も残している）。標識の形と記号の部品（解像度によらない設計の座標の図形）の組み合わせで、出力のマス目に直接ドット絵として塗る。`tools/texgen/manual/` に置いた手の仕上げを優先する仕組みもある（[ADR 0009](adr/0009-generate-textures-from-parts-with-manual-overrides.md)、[ADR 0011](adr/0011-draw-textures-on-a-resolution-independent-grid.md)、[ADR 0014](adr/0014-draw-all-builtin-pictograms-at-128.md)、[`diagrams/texture-generation.md`](diagrams/texture-generation.md)、手順は [`development.md`](development.md)）。

| パッケージ / ファイル | 役割 | 動く場所 |
|---|---|---|
| `SafetySignsMod` | `@Mod` 本体。mod ID・版・接続できる版の範囲。通信路と GUI の登録 | 両方 |
| `SafetySignsGuiHandler` | GUI の番号 → 画面（図柄選択）。コンテナがないのでサーバーは何も開かない | 両方（画面はクライアント） |
| `block/SafetySignsBlocks` | ブロック・アイテム・TileEntity（`safetysigns:sign`）の登録 | 両方 |
| `block/BlockSafetySign` | 看板のブロックの共通部分: 向き、代表のマスのたどり方、図柄を描く面の位置、設置時の図柄の引き継ぎ、右クリックで GUI、壊したとき・ピックブロックで図柄入りのアイテム、1 マス壊れたら全体を消す | 両方 |
| `block/BlockWallSign`・`block/BlockWallSign2x2` | 壁掛け看板 1×1・2×2（板の外形の四角の当たり判定。2×2 は部品の番号 `part` を状態に持つ） | 両方 |
| `block/BlockPoleSign` | ポール付き看板（縦 3 マス。部品 0（代表）・1 が支柱、2 が板）。床（上面）にだけ置ける。当たり判定は、支柱の段は細い柱、看板の段は板の外形の四角 | 両方 |
| `tile/TileEntitySafetySign` | 図柄 ID を持つ（代表のマスだけ）。保存とクライアントへの同期 | 両方 |
| `item/ItemSafetySign`・`item/SignNbt` | 看板アイテム（ツールチップに図柄と、既定と違う切り抜き・裏面・光るか）。複数マスの看板は全部のマスに置けるときだけ置く。スニーク右クリックで看板の見た目を写す。NBT のキー `SignId`・`Cutout`・`BackPattern`・`Glow` の読み書き | 両方 |
| `recipe/RecipeClearSign`・`recipe/SafetySignsRecipes` | 看板アイテム 1 個だけで無地に戻すレシピ（`safetysigns:clear_sign`）とその登録 | 両方 |
| `mc1.12.2/…/assets/safetysigns/recipes/*.json` | 看板を作るレシピ（不定形。木の看板 + 鉄。鉄は鉱石辞書 `ingotIron`・`plateIron`。[ADR 0010](adr/0010-craft-signs-with-shapeless-json-recipes.md)） | 両方 |
| `network/MessageSetSign` | クライアント → サーバー: 見た目（図柄・切り抜き・裏面・光るか）を変える。サーバーが形・距離・場所を確かめる | 両方 |
| `common/` | Minecraft に依存しない計算: 図柄 ID の形（`SignIds`）、定義ファイルの読み取り（`SignDefinitionParser`）、マージと検索（`SignCatalog`）、看板が使うマスの計算と置けるかの判定（`SignLayout`・`GridPos`）、テクスチャのアルファから切り抜いた板の形（`SignOutline`）、看板の見た目の値（`SignAppearance`） | 両方（使うのは主にクライアント） |
| `client/SignTextures` | 定義ファイルを全パックから読んでマージし、図柄のテクスチャを登録する。テクスチャの PNG から板の形を作って取っておく | クライアントだけ |
| `client/SignRenderer`・`client/SignItemModel`・`client/ClientModels` | 看板の板（表・裏・縁。切り抜き・裏面の設定に合わせ、光る看板は表の図柄だけ明るさ最大）の描画、アイテムのモデル、その登録 | クライアントだけ |
| `client/gui/GuiSignPicker` | 図柄選択の GUI（タブ（W P M E F GHS その他）・グリッド・プレビュー・検索・無地に戻す・切り抜きと裏面の切り替え・プレビューの下に夜間に光るかの切り替え） | クライアントだけ |
| `assets/<namespace>/safetysigns/signs.json` | 図柄の定義ファイル（本体のものは `shared/` にある。形は [`resourcepack.md`](resourcepack.md)） | クライアントだけ |

```mermaid
flowchart LR
    subgraph Both["両方（クライアント・サーバー）"]
        Mod["SafetySignsMod<br/>@Mod・通信路・GUI の登録"]
        Reg["SafetySignsBlocks<br/>登録"]
        Block["BlockSafetySign<br/>1×1・2×2・ポール付き"]
        Item["ItemSafetySign"]
        TE["TileEntitySafetySign<br/>NBT: SignId・Cutout・BackPattern・Glow"]
        Msg["MessageSetSign"]
        Common["common/<br/>SignIds・SignCatalog・SignLayout"]
        Recipe["recipe/・recipes/*.json<br/>作るレシピ・無地に戻すレシピ"]
    end
    subgraph Client["クライアントだけ"]
        Tex["SignTextures<br/>定義の読み込み"]
        Json["signs.json<br/>全リソースパック"]
        Render["SignRenderer<br/>切り抜いた板を描く<br/>（光る看板は表だけ明るさ最大）"]
        Gui["GuiSignPicker"]
    end
    Mod -- 通信路・GUI の登録 --> Msg
    Reg --> Block
    Reg --> Item
    Reg --> TE
    Block -->|"設置・右クリック（代表のマスをたどる）"| TE
    Block -- マスの計算 --> Common
    Item -- 全部のマスに置けるか --> Common
    TE -- 写す・ドロップ・ピックブロック --> Item
    Recipe -- 作る・NBT を外す --> Item
    Block -- openGui --> Gui
    Json --> Tex
    Tex -- マージ・検索・板の形 --> Common
    Gui -- 一覧・検索 --> Tex
    Gui -- 選ぶたびに送る --> Msg
    Msg -- 形の確認 --> Common
    Msg -- 距離を確かめて書き込む --> TE
    TE -. 同期 .-> Render
    Render -- テクスチャ・板の形 --> Tex
```

## 主要な流れ（図柄を選ぶ）

1. 看板アイテム以外を持って（素手でもよい）看板を右クリックすると、クライアントが `GuiSignPicker` を開く（サーバーは何も開かない）。
2. GUI は `SignTextures` の一覧（全パックの定義をマージしたもの）を表示する。検索は全カテゴリ横断。
3. 図柄をクリックするたび・切り抜きや裏面・夜間に光るかのボタンを押すたびに、`MessageSetSign`（位置・図柄 ID（無地なら ID なし）・切り抜き・裏面・光るか）をサーバーへ送る。
4. サーバーは、ID の形（`<namespace>:<名前>`、64 文字まで）・看板まで 8 ブロック以内か・いじってよい場所（スポーン保護の外）かを確かめ、TileEntity に書き込む。図柄の一覧は見ない（切り抜き・裏面・光るかは真偽値なのでそのまま）。
5. TileEntity の変更が、近くのクライアントへ同期される（バニラの TileEntity の更新）。
6. クライアントは `SignRenderer` で、切り抜き・裏面の設定に合わせた板を描く（光る看板は表の図柄だけ明るさ最大）。一覧にない ID は「?」で描き、ID は消さない。

```mermaid
sequenceDiagram
    actor P as プレイヤー
    participant C as クライアント
    participant S as サーバー
    participant O as ほかのクライアント
    P->>C: 看板を右クリック（看板アイテム以外）
    C->>C: GuiSignPicker を開く（一覧は SignTextures）
    P->>C: 図柄をクリック・切り抜き / 裏面 / 光るかを切り替え
    C->>S: MessageSetSign（位置・図柄 ID・切り抜き・裏面・光るか）
    S->>S: ID の形・8 ブロック以内・スポーン保護を確かめる
    S->>S: TileEntity の見た目を書き換えて保存
    S-->>C: TileEntity の更新
    S-->>O: TileEntity の更新
    C->>P: 図柄の形の板（一覧にない ID は「?」）
```

## 設置・コピー・壊す

右クリックの分岐（GUI・コピー・設置）は [`diagrams/right-click.md`](diagrams/right-click.md) に図がある。

- 2×2・ポール付きなど複数マスの看板では、どのマスを右クリック・ピックブロックしても、`BlockSafetySign.getSignTile` が代表のマスをたどり、同じ看板として扱う。GUI は代表のマスの位置で開き、`MessageSetSign` も代表のマスへ送る。
- 複数マスの看板の設置（全部のマスに置けるときだけ）と破壊（1 マスで全体が消え、アイテムは 1 個）は [`diagrams/multiblock.md`](diagrams/multiblock.md)。
- 看板アイテムで右クリックすると、`ItemBlock` が設置し（複数マスなら `ItemSafetySign.placeBlockAt` が残りのマスも置く。ポール付きは、クリックした面が上面のときだけ置ける＝`BlockPoleSign.canPlaceBlockOnSide`）、`BlockSafetySign.onBlockPlacedBy` がアイテムの見た目（`SignId` は形の正しいものだけ。切り抜き・裏面・光るかも）を TileEntity に写す。向きは置いた人の向きの反対。
- 看板アイテムを持って看板を右クリックしたときは GUI を開かず、そのまま隣に設置する。
- 看板アイテムを持ってスニークし、看板を右クリックすると、`ItemSafetySign.onItemUse` が看板の見た目（図柄・切り抜き・裏面・光るか）を手のアイテムに写す（設置しない。種類・サイズは違ってよい。スタックはまとめて写す）。スニーク中はバニラが `onBlockActivated` を呼ばないので、ブロックではなくアイテムの側で扱う（[ADR 0003](adr/0003-handle-copy-and-drops-on-the-item-side.md)）。書き換えはサーバーだけで、手のアイテムはバニラの同期で届く。
- ピックブロックは `BlockSafetySign.getPickBlock` が図柄入りのアイテムを返す（クライアントの TileEntity の見た目を使う）。クリエイティブの Ctrl + ピックブロックで付く `BlockEntityTag` は、OP 以外が置いたときは読ませない（`onlyOpsCanSetNbt`。形を確かめない ID が TileEntity に入るのを防ぐ）。
- 壊すと、見た目（図柄・切り抜き・裏面・光るか）を保持したアイテムが 1 個落ちる（一覧にない ID もそのまま）。プレイヤーが壊すときは `removedByPlayer` でブロックを消さずに残し、`getDrops` で代表のマスの TileEntity の図柄を読んでから `harvestBlock` で消す。クリエイティブではバニラどおり落とさない。爆発では、バニラが消す前にドロップを作るのでそのまま読める。
- 看板はピストンで動かない・壊れない（`getMobilityFlag` が `BLOCK`。代表のマス以外は TileEntity を持たないので明示している）。
- 描画の範囲（`getRenderBoundingBox`）は代表のマスの周り 1 マス・上へ 3 マスまで広げてある。2×2 の代表のマスやポール付きの支柱が画面の外に出ても、板が消えないように。描く距離（`getMaxRenderDistanceSquared`）は 256 ブロック。
- 看板アイテム 1 個だけをクラフト枠に入れると、`RecipeClearSign` が同じアイテムの無地（NBT なし。切り抜き ON・裏面 銀・光る に戻る）を返す。もともと NBT のないアイテムでは何も出さない。レシピ本には出さない。
- 図柄は誰でも変えられる（仕様書 4。ロック機能は後回し）。アドベンチャーモードでも変えられる。弾くのは観戦者と、スポーン保護の内側だけ。

## NBT

| 場所 | キー | 値 |
|---|---|---|
| TileEntity（`safetysigns:sign`） | `SignId` | 図柄 ID の文字列。無地ならキーなし |
| 〃 | `Cutout` | 切り抜き OFF のときだけ `false`（byte）。キーなしは ON |
| 〃 | `BackPattern` | 裏面が図柄のときだけ `true`（byte）。キーなしは銀 |
| 〃 | `Glow` | 夜間に光らないときだけ `false`（byte）。キーなしは光る |
| アイテム（タグの直下） | `SignId`・`Cutout`・`BackPattern`・`Glow` | 同上。既定の見た目なら NBT なし（無地のアイテムとスタックできる） |

キー名・TileEntity の登録名は既存のワールドが参照するので変えない。

## ブロックの状態（メタデータ）

| ブロック | 状態 | メタデータ |
|---|---|---|
| `safetysigns:wall_sign_1x1` | `facing` | 向きの horizontal index（0 南・1 西・2 北・3 東） |
| `safetysigns:wall_sign_2x2` | `facing`・`part`（0 左下が代表・1 右下・2 左上・3 右上。正面から見て） | 下位 2 ビットが向き、上位 2 ビットが部品 |
| `safetysigns:pole_sign` | `facing`・`part`（0 最下段の支柱が代表・1 中段の支柱・2 最上段の板） | 下位 2 ビットが向き、上位 2 ビットが部品 |

メタデータの意味は既存のワールドが参照するので変えない。
