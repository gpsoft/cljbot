# cljbot

ClojureでGUI操作を自動化する。裏方として`java.awt.Robot`を使う。

- [デモ動画(YouTube)](https://youtu.be/Py2ZQ_Uyajw)

## できること

- マウスクリック
- キーボード押下(キー種は限定的)
- 待つ
- ループ
- 上記を組み合わせたスクリプトを再生
- マウスの座標を学習するモード

※OSのキーボードレイアウト設定が「日本語キーボード」になってない場合、記号キーの一部(例えば`@`とか)は、たぶん、まともに動かない(`2`が押下される?)。

## ビルド

```shell-session
$ git clone https://github.com/gpsoft/cljbot.git
$ cd cljbot
$ boot build
```

## 実行

```shell-session
$ java -jar target/cljbot-xxxxx-standalone.jar script/run3
```

↑スクリプト`script/run3.edn`を実行。

Windowsで使う場合、うまくアプリを制御できないときは管理者権限のコマンドプロンプトから起動してみると良い。

## 操作一覧

スクリプトに書ける操作は……

- `:nop`
- `:loop`
- `:pause`
- `:click-left-on` (`middle`と`right`も)
- `:move-to`
- `:type-string` (文字列用)
- `:type-key` (特殊キー用)

`:type-string`に使える文字は……

- アルファベット
- 数字
- 一部の記号

`:type-key`に使えるキーは……

- `:enter`
- `:esc`
- `:bs`
- `:colon`
- `:comma`
- `:space`
- `:tab`

※使える文字とキーは、漸次拡張中。

## スクリプト例

ednで書く。

### run1.edn
左クリック。
```
[
 [:click-left-on 10 20]
 ]
```

### run2.edn
クリックして、待って、ESCキー押下。
```
[
 [:click-left-on 10 20]
 [:nop "Wait for a sec."]
 [:pause 1000]
 [:type-key :esc]
 ]
```

### run3.edn
マウス座標を学習。
```
[
 ^{:caption "The start button"} [:click-left-on :?]
 [:pause 1000]
 [:type-key :esc]
 ]
```

座標の代わりに`:?`を指定したところがミソ。メタデータ`:caption`は任意。ちなみにednは、メタデータを正式にはサポートしてない。でもまぁ、Clojureで扱うなら問題無さそう。

実行すると、`:?`のところで、マウスの移動待ちになる。

```shell-session
$ java -jar target/cljbot-xxxxx-standalone.jar script/run3
Please move mouse for The start button...
```

ここで、マウスを所望の位置へ移動(5秒以内に!)して待てば、マウス位置を学習し、学習後のスクリプトをファイルに保存してくれる。

### run3-learned.edn
```
[^{:caption "The start button"} [:click-left-on 1661 332]
 [:pause 1000]
 [:type-key :esc]]
```

### run4.edn
ループと文字列タイプ。
```
[
 [:loop 2
  [
   [:type-string "Hey" :i "Yo"]
   [:type-key :enter]
   ]
  ]
 ]
```

`:i`は、ループカウンタ。

