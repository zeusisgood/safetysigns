# 開発の手順

## テクスチャを作る

図柄のテクスチャは `tools/texgen/` のスクリプトで作る（Python 3 + Pillow）。出力の PNG と一覧画像はコミットする。

```
python3 tools/texgen/generate.py            # 128×128（既定）
python3 tools/texgen/generate.py --size 64  # 解像度を変える（16〜512 の 2 の累乗。試し用。コミットするのは 128）
```

| 出力 | 内容 |
|---|---|
| `src/main/resources/assets/safetysigns/textures/signs/<名前>.png` | 図柄（128×128。標識の形の外側は透明） |
| `src/main/resources/assets/safetysigns/textures/blocks/sign_*.png` | 無地・「?」（`--size` の解像度）・裏面・支柱（いつも 16×16） |
| `doc/textures/preview.png` | 全図柄を同じ大きさに拡大して（128 なら 2 倍）、コードと英語の名前を添えた一覧（PR に貼る） |
| `doc/textures/parts.png` | 記号の部品だけを白地に黒で並べた一覧（PR に貼る） |
| `src/main/resources/assets/safetysigns/textures/items/*.png` | 無地の看板アイテムのアイコン 3 種（いつも 32×32。図柄を持つアイテムは図柄のアイコンになる） |
| `doc/textures/items.png` | アイテムのアイコンの一覧（PR に貼る） |
| `doc/store/icon.png` | CurseForge のアイコン（壁掛け 1×1 のアイコンを 16 倍、512×512。透明な所は `#2B2F33`。jar には入らない） |
| `src/main/resources/logo.png` | Forge の Mods 画面のロゴ（同じ絵を 4 倍、128×128。`mcmod.info` の `logoFile`） |

本体の図案は 128 で見て決めている。`--size` を変えても描けるが、64 以下では細い所（0.5 単位の線・隙間）がつぶれる（コミットするのは 128）。

### 解像度（内蔵の図柄はすべて 128）

- 内蔵の図柄は**すべて 128×128**（`--size` の既定。[SPEC_2026-10-12-11](spec/SPEC_2026-10-12-11.md)）。64×64 は使わない。無地・「?」も 128×128。
- 座標は 0〜64 のまま。128 では 1 単位が 2 ピクセルなので、0.5 単位の細い線・隙間も描ける。地の縁との間隔（1.5 単位）の確かめは同じ。
- 図案は 128 のマス目で見て決める。小さく描いて拡大することはしない（塗るのはいつも出力のマス目。`raster.fill`）。
- 図柄ごとに解像度を変える仕組み（`SIGNS` で `hires(<形>(...), factor)` に包むと `--size` の factor 倍）は残しているが、今の内蔵の図柄では使っていない。
- 看板・GUI では解像度が違っても同じ大きさに描かれる（Mod のコードは解像度を問わない）。リソースパックの図柄は、2 の累乗の正方形なら解像度を問わない（[resourcepack.md](resourcepack.md)）。

| ファイル | 役目 |
|---|---|
| `raster.py` | 図形（`Shape`: 点が内側かを答える関数）と、組み合わせ・移動・回転と、マス目に塗る `fill` |
| `parts.py` | 色、標識の形（三角・円・輪・斜線・四角・ひし形）、記号の部品 |
| `generate.py` | 標識の形ごとの描き方（`warning` など）、図柄の定義 `SIGNS`、一覧画像、無地・「?」・裏面・支柱 |

流れの図は [diagrams/texture-generation.md](diagrams/texture-generation.md)、決定は [ADR 0009](adr/0009-generate-textures-from-parts-with-manual-overrides.md) と [ADR 0011](adr/0011-draw-textures-on-a-resolution-independent-grid.md)。

### 描き方のきまり

- 図案はすべて自分で描く。既存の画像や SVG（Wikimedia Commons など）をダウンロード・取り込み・なぞりはしない。
- 座標は**設計の単位**で書く。キャンバス全体が 0〜64（左上が (0, 0)、中心は (32, 32)）で、出力の解像度に関係なく同じ値。解像度 128（既定）なら 1 単位が 2 ピクセル。
- ドット絵として描く。塗るときは、出力の解像度のピクセルごとに、**ピクセルの中心が図形の内側か**だけで決める（`raster.fill`）。アンチエイリアス・中間色・半透明は使わない。大きく描いてから縮めることもしない。
- 使う色は次の表と透明だけ（`parts.py` の定数）。生成した図柄にほかの色があると、スクリプトが止まる。

| 用途 | 色 |
|---|---|
| 警告の黄 | `#F9A800` |
| 黒（枠・記号） | `#1A1A1A` |
| 禁止・防火の赤 | `#9B2423` |
| 指示の青 | `#005387` |
| 安全状態の緑 | `#237F52` |
| 白 | `#FFFFFF` |
| GHS の枠・ISO 21482 の赤（`HAZARD_RED`） | `#D7191F` |

- 形（`generate.py` の `warning` / `prohibition` / `mandatory` / `safe_condition` / `fire` / `ghs` / `ionizing_radiation`）。どれも外側に 1 単位の透明な余白を残して 62 単位に収める:
  - 警告（W）: 黄の正三角形に黒枠と黒の記号。角は半径 3 で丸め、黒枠は 5（`parts.warning_triangle`）。中の黄色の重心は 41 行目あたり（`parts.TRIANGLE_SYMBOL_Y`）。
  - 禁止（P）: 白い円に黒の記号。その上に赤い輪（太さ 6）と左上→右下の斜線（幅 5.5）を重ねる。
  - 指示（M）: 青い円に白の記号。
  - 安全状態（E）: 緑の四角（角を半径 4 で丸める）に白の記号。
  - 防火（F）: 赤の四角に白の記号。
  - GHS: 白地のひし形（正方形を 45° 回したもの）に赤 `#D7191F` の枠（4）と黒の記号。角は半径 2 で丸める（`parts.diamond`）。記号はひし形の中の正方形（中心から縦横 12 くらい）に収まるように置き、横棒・骨の端などは角に寄せすぎない。
  - ISO 21482（`ionizing_radiation`）: 警告と同じ三角形に、黄の代わりに赤 `#D7191F`、黒枠と黒の記号。
- 記号は、地（黄・白・青・緑・赤）の縁から 1.5 単位以上離す。近すぎるとスクリプトが止まる（`check_margin`。解像度によらず、細かいマス目で確かめる）。止まったら、記号を小さくするか位置をずらす。
- 形の中心は 32（ピクセルの境目）。左右対称の記号は中心を 32 に置くと、左右のピクセルがちょうど揃う。

### 部品

`parts.py` の記号の部品は、どれも図形を返すだけ（塗るのは標識の形の関数）。多くは `(cx, cy, size)` で中心と高さを受け取る。一覧は `doc/textures/parts.png`（`parts.catalog()` の順）。

| 部品 | 関数 | 備考 |
|---|---|---|
| 人物 | `person(cx, cy, size, pose)` | `pose` は `standing`（正面）・`walking`・`running`・`crouching`（横向き、右向き）。`mirror=True` で左向き。足の裏はどの姿でも同じ高さ |
| 防護服の人 | `coverall_person` | M010 |
| 手 | `hand(cx, cy, size)` | 開いた手のひら。`mirror=True` で親指が右 |
| 頭部 | `head(cx, cy, size)` | 保護具を重ねる前提の無地の胸像。保護具の位置は `head_anchor("eyes", cx, cy, size)` など（`crown` `brow` `eyes` `ears` `mouth` `chin`）。重ねるのは `wear(頭部, 保護具)`（保護具の周りを細く抜いて境目を見せる） |
| 矢印 | `arrow(cx, cy, length, angle)` | `angle` は右が 0 で時計回りの度 |
| 炎・稲妻・しずく・歯車 | `flame` `lightning` `droplet` `gear` | |
| 波 | `waves`（横に流れる）・`rising_waves`（縦に立ちのぼる） | |
| 警告の記号 | `exclamation` `trefoil` `explosion` `hot_surface` | W001・W003・W002・W017 |
| GHS の記号 | `exploding_bomb` `flame_on_bar` `flame_over_circle` `gas_cylinder` `corrosion` `skull_crossbones` `health_hazard` `environment` | GHS01〜GHS09（GHS07 は `exclamation`）。どれも原点の中心で高さ 40（`GHS_HEIGHT`）で書いている |
| 放射線の波 | `radiation_waves(cx, cy, angles)` | 中心から外へ向かう波線（ISO 21482） |
| 試験管・欠けた手 | `test_tube(底, 口, line)`・`corroded_hand(line)` | GHS05 と W023 で共用。`line` を渡すと輪郭線、渡さなければ塗りつぶし（試験管） |
| ISO 7010 の警告の記号 | `laser` `antenna` `magnet` `biohazard` `snowflake` `automatic_start` `crushing` `corrosive_triangle` `battery` `upright_cylinder` `arc_flash` `hot_content` `hot_steam` | W004・W005・W006・W009・W010・W018・W019・W023・W026・W029・W042・W079・W080（W016 は `skull_crossbones`、W021 は `flame`） |
| ISO 7010 の禁止の記号 | `cigarette` `burning_match` `bucket_on_fire` `mobile_phone` | P002・P003・P011・P013（P001 は記号なし: `prohibition()`） |
| 保護具を付けた頭部 | `protected_head(cx, cy, size, gear)` | `gear` は `ear_muffs` `goggles` `face_shield` `helmet` `dust_mask` `respirator`（M003・M004・M013・M014・M016・M017）。保護具は `HEAD_GEAR` の関数で、頭部（`head_shape`）と同じ座標で書き、`wear` で重ねる。頭の位置と大きさは図柄どうしで揃える（`generate.py` の `HEAD_GEAR_PLACE`） |
| ISO 7010 の指示の記号 | `open_book` `safety_boot` `glove` | M002・M008・M009（M001 は `exclamation`） |
| ISO 7010 の安全状態・防火の記号 | `exit_door` `first_aid_cross` `eyewash` `safety_shower` `stop_button` `extinguisher` `call_point` | E001・E002（`mirror=True` が左の E001）・E003・E011・E012・E020・F001・F005。指さす手は `pointing_hand_shape`（F005）。E011 だけは横向きの頭を描く（正面の頭部だと水の線が顔を横切る） |

部品を足すときは、原点を中心にした決まった高さで形を書き、`place` で置く（`hand_shape` と `hand` の組を見る）。輪郭線だけで描く部品（GHS05 の試験管・手）は `raster.outline(形, 太さ)` で線にする（形を `raster.erode` で細らせて抜いたもの。太さは小さい値の前提）。形は `raster.py` の基本の図形（円・楕円・四角・角の丸い四角・太線 `capsule`・太さの変わる線 `tapered`・折れ線 `polyline`・多角形・輪・波）を `|`（足す）・`-`（抜く）・`&`（重なり）で組む。
重なった部品の境目を見せたいときは `raster.layered(奥, 手前, gap=1.0)`（手前の周りを gap だけ抜く）。

### 図柄を足す

1. 定義ファイル `src/main/resources/assets/safetysigns/safetysigns/signs.json` に項目を足す（形は [resourcepack.md](resourcepack.md)。名前は日英の両方）。**図柄 ID は一度出したら変えない・消さない**。
2. 記号の部品が足りなければ、`tools/texgen/parts.py` に関数を足す（上の「部品」）。
3. `tools/texgen/generate.py` の `SIGNS` に、`"<名前>": <形>(<部品>(<中心と大きさ>))` を足す。記号を 2 つ以上並べるときは、形の関数に部品を続けて渡す。
4. `python3 tools/texgen/generate.py` を流して、`doc/textures/preview.png` と `doc/textures/parts.png` を見る。地の縁と記号がくっついていないか（くっつくとスクリプトが止まる）、遠目に何か分かるかを確かめる。
5. PNG・一覧画像・スクリプトをまとめてコミットする。機能の追加なので版はパッチで上げ、`CHANGELOG.md` に書く。

### 手で仕上げる

部品の組み合わせだと不自然な図柄は、ドットを直接打って仕上げてもよい（仕組みは残しているが、今は本体の図柄はすべてスクリプトで描いていて、`manual/` に図柄はない）。

1. 生成した `textures/signs/<名前>.png` を `tools/texgen/manual/<名前>.png` にコピーする。
2. ドット絵の描けるエディタ（Aseprite・LibreSprite・GIMP の鉛筆ツールなど）で、上の色だけを使って直す。消しゴムで消した所は完全な透明にする。
3. `generate.py` を流すと、`manual/` にある図柄は生成せず、そのファイルを出力にコピーする（流し直しても手の仕上げは消えない）。一覧画像の名前に `(manual)` が付く。
4. 2 の累乗の正方形でなければ止まる。解像度がその図柄の解像度（`--size`。既定 128）と違う・色が外れていると警告が出る（出力はされる）。直してから流し直す。

`manual/` のファイルを消せば、生成に戻る。`SIGNS` にない名前のファイルを置けば、手で描いただけの図柄としても出力される（定義ファイルの項目は別に足す）。

## リリースの手順

タグと GitHub Release は、`main` に取り込まれたあとに Actions（`.github/workflows/release.yml`）が作る。Claude はタグを付けない（`.claude/settings.json` で `git tag` とタグの push を止めている）。流れの図は [`diagrams/release.md`](diagrams/release.md)。

### 版を上げる PR で直すもの

1. `build.gradle` の `version` と `SafetySignsMod.VERSION` を揃えて上げる（パッチ・マイナーの決め方は `CLAUDE.md` の「バージョン」）。マイナーを上げるときは `SafetySignsMod.ACCEPTED_VERSIONS` も新しいマイナーの範囲にする。
2. `CHANGELOG.md` の一番上に `## <版>` の節を足す。この節がそのまま Release の本文になる（マイナーなら、旧マイナーとは接続できないことを書く）。
3. `./gradlew build` を流す。`resources/ReleaseFilesTest` が、`build.gradle`・`SafetySignsMod.VERSION`・`CHANGELOG.md` の最新の見出しの 3 つが一致するかを確かめる（ずれていれば PR の CI が落ちる）。
4. jar を `build/release/` にコピーし（Merge の前に試せるように）、PR を作る。

文書だけの変更など、版を上げない PR では何も起きない。

### Merge のあと（Actions が行う）

1. `build.gradle` の `version` を読み、タグ `v<版>` がもうあれば何もしない。
2. なければ `./gradlew build`（ビルドとテスト）を流す。失敗したら、タグも Release も作らない。
3. `CHANGELOG.md` の `## <版>` から次の `## ` の手前までを本文にし、`safetysigns-1.12.2-<版>.jar` を添付して、取り込んだコミットにタグ `v<版>` と Release を作る（`gh release create`）。節がない・空なら止まる。

### 確かめ方・失敗したとき

- GitHub の Actions の「Release」が緑で、Releases に `v<版>` があり、jar が添付され、本文が `CHANGELOG.md` の節と同じなら成功。
- ビルドやテストで落ちたときは、直す PR を Merge すれば、その push でもう一度流れる（タグがまだないので）。
- 一時的な失敗（Maven の取得など）なら、Actions の画面で失敗した「Release」を Re-run する。
