# 開発の手順

## テクスチャを作る

図柄のテクスチャは `tools/texgen/` のスクリプトで作る（Python 3 + Pillow）。出力の PNG と一覧画像はコミットする。

```
python3 tools/texgen/generate.py
```

| 出力 | 内容 |
|---|---|
| `src/main/resources/assets/safetysigns/textures/signs/<名前>.png` | 図柄（32×32、標識の形の外側は透明） |
| `src/main/resources/assets/safetysigns/textures/blocks/sign_*.png` | 無地・「?」・裏面・支柱 |
| `doc/textures/preview.png` | 全図柄を 8 倍にして、コードと英語の名前を添えた一覧（PR に貼る） |

流れの図は [diagrams/texture-generation.md](diagrams/texture-generation.md)、決定は [ADR 0009](adr/0009-generate-textures-from-parts-with-manual-overrides.md)。

### 描き方のきまり

- ドット絵として描く。アンチエイリアス・中間色・半透明は使わない。使う色は次の表と透明だけ（`parts.py` の定数）。生成した図柄にほかの色があると、スクリプトが止まる。

| 用途 | 色 |
|---|---|
| 警告の黄 | `#F9A800` |
| 黒（枠・記号） | `#1A1A1A` |
| 禁止の赤 | `#9B2423` |
| 指示の青 | `#005387` |
| 安全状態の緑（まだ使っていない） | `#237F52` |
| 白 | `#FFFFFF` |

- 形（`generate.py` の `warning` / `prohibition` / `mandatory`）:
  - 警告（W）: 黄の正三角形に黒枠と黒の記号。31 ピクセル幅で、頂点は 1 ピクセル（`parts.warning_triangle`）。中の黄色は、おおよそ 9〜27 行目。
  - 禁止（P）: 31 ピクセル幅の白い円に黒の記号。その上に赤い輪（3 ピクセル）と左上→右下の斜線（1 行 3 ピクセル）を重ねる。
  - 指示（M）: 31 ピクセル幅の青い円に白の記号。
- 形の中心は 15 列目・15 行目（31 ピクセル幅を左右対称にするため）。記号のドット絵は幅を奇数にすると、真ん中に置ける。

### 図柄を足す

1. 定義ファイル `src/main/resources/assets/safetysigns/safetysigns/signs.json` に項目を足す（形は [resourcepack.md](resourcepack.md)。名前は日英の両方）。**図柄 ID は一度出したら変えない・消さない**。
2. 記号の部品が足りなければ、`tools/texgen/parts.py` に関数を足す。`(image, cx, top, color)` を受け取り、`dots_centered` に「#」のドット絵を渡すのがいちばん簡単。円や角度で決まる形（放射線マークなど）は `fill_where` でピクセルごとに内外を決める。
3. `tools/texgen/generate.py` の `SIGNS` に、`<名前>: <形>((<部品>, (<位置>)))` を足す。位置は、三角形なら黄色の重心より少し下（三角形の上は細い）。
4. `python3 tools/texgen/generate.py` を流して、`doc/textures/preview.png` を見る。枠と記号がくっついていないか（1 ピクセルは空ける）、遠目に何か分かるかを確かめる。
5. PNG・一覧画像・スクリプトをまとめてコミットする。機能の追加なので版はパッチで上げ、`CHANGELOG.md` に書く。

### 手で仕上げる

人物の入る図柄など、部品の組み合わせだと不自然な図柄は、ドットを直接打って仕上げてよい。

1. 生成した `textures/signs/<名前>.png` を `tools/texgen/manual/<名前>.png` にコピーする。
2. ドット絵の描けるエディタ（Aseprite・LibreSprite・GIMP の鉛筆ツールなど）で、上の色だけを使って直す。消しゴムで消した所は完全な透明にする。
3. `generate.py` を流すと、`manual/` にある図柄は生成せず、そのファイルを出力にコピーする（流し直しても手の仕上げは消えない）。一覧画像の名前に `(manual)` が付く。
4. 色が外れていると警告が出る（出力はされる）。直してから流し直す。

`manual/` のファイルを消せば、生成に戻る。`SIGNS` にない名前のファイルを置けば、手で描いただけの図柄としても出力される（定義ファイルの項目は別に足す）。
