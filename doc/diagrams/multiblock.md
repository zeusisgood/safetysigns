# 複数マスの看板の設置と破壊

壁掛け看板 2×2・ポール付き看板など、複数マスを使う看板の設置と破壊の流れ（0.4.0）。仕組みを変えたら、同じ PR でこの図を直す。
流れは 3 つの版で同じ。メソッドの名前は 1.12.2 のもので、1.20.1・1.21.1 の名前は [architecture.md の「版ごとの違い」](../architecture.md#版ごとの違い)。

- マスの並びは `common/SignLayout`。正面から見て、**代表のマス（部品 0）が左下**で、そこから右・上へ広がる。2×2 の部品は 0 左下・1 右下・2 左上・3 右上。ポール付きは幅 1・高さ 3 で、部品は 0 最下段の支柱・1 中段の支柱・2 最上段の板（[ADR 0005](../adr/0005-pole-sign-keeps-the-pattern-in-the-pole-cell.md)）。
- 置く所（クリックした面の隣、または置き換え可能なブロック）が代表のマスになる。ポール付きは、クリックした面が上面（床）のときだけ置ける（`BlockPoleSign.canPlaceBlockOnSide`）。
- サーバーで置けなかったときは、全部のマスの今の状態をクライアントへ送り直す（クライアントが先に置いて見せたマスを消す）。
- 部品の番号はブロックの状態（`part`）に持つ。図柄 ID は代表のマスの TileEntity だけが持つ。
- どのマスからでも、`BlockSafetySign.getSignTile` が向きと部品の番号から代表のマスをたどる（GUI・コピー・ピックブロック・ドロップ）。
- 1×1 も同じ仕組みで、マスが 1 つだけの場合として扱う。
- 図柄は代表のマスの TileEntity が、ブロックの決める面（`getFaceRow`・`getFaceZ`）に描く。壁掛けは看板全体、ポール付きは最上段の板だけ。板は図柄の形に切り抜いて描く（[ADR 0006](../adr/0006-draw-the-plate-cut-to-the-pattern-shape.md)）。

## マスの並び

```mermaid
flowchart LR
    subgraph W1["壁掛け 1×1"]
        A0["0 代表"]
    end
    subgraph W2["壁掛け 2×2（正面から見る）"]
        direction BT
        B0["0 左下・代表"] --- B2["2 左上"]
        B1["1 右下"] --- B3["3 右上"]
    end
    subgraph P["ポール付き（床に置く）"]
        direction BT
        C0["0 最下段の支柱・代表<br/>図柄の TileEntity"] --- C1["1 中段の支柱"] --- C2["2 最上段の板<br/>図柄を描く面"]
    end
```

## 設置

```mermaid
flowchart TD
    Click([看板アイテムで右クリック]) --> IB{"ItemBlock.onItemUse<br/>代表のマスに置けるか<br/>（ポール付きはクリックした面が上面か）"}
    IB -- いいえ --> Fail["失敗<br/>（アイテムは減らない）"]
    IB -- はい --> All{"ItemSafetySign.placeBlockAt<br/>SignLayout.canPlace<br/>全部のマスに置けるか"}
    All -->|"いいえ（ふさがっている・高さの外・スポーン保護・ほかの人や物とぶつかる）"| Fail
    All -- はい --> Origin["代表のマスを置く<br/>onBlockPlacedBy が図柄を引き継ぐ"]
    Origin --> Parts["残りのマスを部品の番号つきで置く<br/>（2×2 は右・上、ポール付きは真上の支柱と板）"]
    Parts --> Done([アイテムを 1 個減らす])
```

## 破壊

- どう壊れても（プレイヤー・爆発・`/setblock`・他 Mod の `setBlockState`）、サーバーでは `breakBlock` を通る。ここで、同じ看板のほかのマスを、アイテムを落とさずに消す。
- アイテムは、壊れたマスの `getDrops` が 1 個だけ作る。図柄は代表のマスから読む。プレイヤーが壊すときは、`removedByPlayer` でブロックを残し、`harvestBlock` でドロップを作ってから消す。爆発・`destroyBlock` は、消す前にドロップを作る。
- クリエイティブで壊すとドロップは作られない（バニラどおり）。
- ピストンでは動かない・壊れない（`getMobilityFlag` が `BLOCK`）。

```mermaid
flowchart TD
    Break([1 マスが壊れる]) --> Who{壊し方}
    Who -- プレイヤー（サバイバル） --> Keep["removedByPlayer<br/>まだ消さない"]
    Keep --> Harvest["harvestBlock → getDrops<br/>代表のマスの図柄で 1 個落とす"]
    Harvest --> Air
    Who -- 爆発・destroyBlock --> Drop["getDrops<br/>代表のマスの図柄で 1 個落とす"]
    Drop --> Air
    Who -->|"クリエイティブ・setBlockState など"| Air["そのマスを空気にする"]
    Air --> BB["breakBlock<br/>（代表のマスなら TileEntity も消す）"]
    BB --> Others["同じ看板のほかのマスを<br/>落とさずに空気にする"]
    Others -.->|"それぞれの breakBlock（消えたマスは飛ばす）"| BB
```
