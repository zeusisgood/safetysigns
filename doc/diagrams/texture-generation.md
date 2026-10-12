# テクスチャの生成

`tools/texgen/generate.py` が看板のテクスチャと一覧画像を作る流れ（0.6.1）。手元で流して、出力の PNG をコミットする（ビルドには Python を持ち込まない）。仕組みを変えたら、同じ PR でこの図を直す。

- 図柄は `SIGNS` に「標識の形（`warning` / `prohibition` / `mandatory`）＋記号の部品（`parts.py`）」で定義する。
- `tools/texgen/manual/<名前>.png` があれば、その図柄は生成せず、手で仕上げたファイルをそのままコピーする（`SIGNS` にない名前も出力する）。
- 生成した図柄は、決めた色と透明以外のピクセルがあれば止まる。手で仕上げた図柄は警告だけ出して使う（32×32 でなければ止まる）。
- 一覧画像の見出し（コードと英語の名前）は、jar に入る定義ファイル `signs.json` から読む。

```mermaid
flowchart TD
    Run["python3 tools/texgen/generate.py"] --> Each["SIGNS と manual/ の<br/>名前を順に"]
    Each --> Manual{"manual/名前.png<br/>がある?"}
    Manual -- ある --> Load["読んで大きさ・色を確かめる<br/>色の外れは警告だけ"]
    Manual -- ない --> Frame["標識の形を描く<br/>三角 / 白地 / 青い円"]
    Frame --> Symbol["記号の部品を描く<br/>parts.py"]
    Symbol --> Slash["禁止だけ<br/>赤い輪と斜線を上に"]
    Slash --> Check["決めた色と透明だけか<br/>外れたら止まる"]
    Load --> Save["textures/signs/名前.png"]
    Check --> Save
    Run --> Blocks["無地・「?」・裏・支柱<br/>textures/blocks/"]
    Defs["signs.json<br/>コードと英語の名前"] --> Preview
    Save --> Preview["doc/textures/preview.png<br/>8 倍・見出し付き"]
    Blocks --> Preview
```
