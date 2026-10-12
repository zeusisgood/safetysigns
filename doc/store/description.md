# ストアの説明文（CurseForge・Modrinth）

CurseForge と Modrinth の説明欄にそのまま貼る文章。英語を先に、日本語をあとに並べる（どちらも Markdown のまま貼れる）。
画像は、ストアの画像欄に `doc/textures/preview.png` を上げて使う。

| 欄 | 内容 |
|---|---|
| 名前 | Safety Signs |
| 短い説明（Summary） | Decorative warning signs inspired by ISO 7010 and other safety signs and hazard symbols. Unofficial. |
| 分類 | Decoration |
| 対応 | Minecraft 1.12.2 / Forge（クライアントとサーバーの両方） |
| ライセンス | MIT |
| ソース | https://github.com/zeusisgood/safetysigns |

---

## Safety Signs

Decorative warning signs inspired by **ISO 7010** and other safety signs and hazard symbols (**GHS** pictograms, **ISO 21482**) for Minecraft 1.12.2 (Forge).
Put a radiation sign around your reactor, an electricity sign on your switchboard, or a "no thoroughfare" sign at the door. The signs are purely decorative and have no gameplay effect.

> **Unofficial mod. Not affiliated with ISO, the United Nations or any standards body.** The pictograms were drawn for this mod with ISO 7010, the GHS and ISO 21482 as references.

### Features

- Three sign types: **wall 1x1**, **wall 2x2** and **pole** (3 blocks tall)
- 18 pictograms
  - ISO 7010: general warning, explosive, radioactive, electricity, hot surface, wear protective clothing, no thoroughfare, do not touch
  - GHS: all 9 pictograms (exploding bomb, flame, flame over circle, gas cylinder, corrosion, skull and crossbones, exclamation mark, health hazard, environment)
  - ISO 21482: ionizing radiation (large sources)
- Right-click a placed sign to pick a pictogram, with category tabs and search (English and Japanese)
- Per-sign settings: **cutout** (plate shaped like the pictogram, or square), **back** (plain silver or the same pictogram), **night glow** (pictogram drawn at full brightness)
- Sneak + right-click with a sign item to copy a sign's look; broken signs keep their pictogram
- **Resource packs can add new pictograms** with a small JSON file and a texture (any power-of-two square, such as 64x64)

### Recipes (shapeless)

- Wall sign 1x1: 1 wooden sign + 1 iron ingot
- Wall sign 2x2: 4 wooden signs + 1 iron ingot
- Pole sign: 3 wooden signs + 1 iron ingot
- Iron accepts the ore dictionary `ingotIron` and `plateIron`
- A single sign item in the grid resets it to blank

### Notes

- Required on **both the client and the server**.
- Developed and tested with Forge 14.23.5.2860.
- Languages: English, Japanese.

---

## Safety Signs（日本語）

ISO 7010 などの安全標識・ハザードシンボル（**GHS** 絵表示・**ISO 21482**）をモチーフにした、装飾用の警告看板を追加する Minecraft 1.12.2（Forge）の Mod です。
原子炉のまわりに放射線の看板、配電盤に感電注意、扉に通行禁止などを立てられます。看板は見た目だけで、ゲーム上の効果はありません。

> **ISO・国連・各規格の団体とは無関係の非公式 Mod です。** 図柄は ISO 7010・GHS・ISO 21482 を参考に、この Mod のために描き起こしたものです。

### できること

- 看板は 3 種類: **壁掛け 1×1**・**壁掛け 2×2**・**ポール付き**（縦 3 ブロック）
- 図柄は 18 種
  - ISO 7010: 一般警告・爆発物・放射性物質・感電注意・高温注意・防護服着用・通行禁止・触るな
  - GHS: 9 種すべて（爆弾の爆発・炎・円上の炎・ガスボンベ・腐食性・どくろ・感嘆符・健康有害性・環境）
  - ISO 21482: 電離放射線（大量の線源）
- 置いた看板を右クリックして図柄を選ぶ（分類のタブと、日英の検索）
- 看板ごとの設定: **切り抜き**（図柄の形の板 / 四角い板）、**裏面**（銀 / 同じ図柄）、**夜間に光る**（図柄を明るさ最大で描く）
- 看板アイテムを持ってスニーク + 右クリックで見た目を写す。壊しても図柄は残る
- **リソースパックで図柄を足せる**（小さな JSON と、2 の累乗の正方形のテクスチャ。64×64 など）

### レシピ（不定形）

- 壁掛け看板 1×1: 看板（木）1 + 鉄インゴット 1
- 壁掛け看板 2×2: 看板（木）4 + 鉄インゴット 1
- ポール付き看板: 看板（木）3 + 鉄インゴット 1
- 鉄は鉱石辞書の `ingotIron`・`plateIron` も使える
- 看板アイテム 1 個だけをクラフト枠に入れると無地に戻る

### 注意

- **クライアントとサーバーの両方に必要**です。
- 開発・動作確認は Forge 14.23.5.2860 です。
- 言語: 英語・日本語
