(ns cljbot.core
  (:require [clojure.pprint :as pp])
  (:import [java.awt Robot MouseInfo PointerInfo]
           [java.awt.event InputEvent KeyEvent]
           [javax.swing KeyStroke])
  (:gen-class))

;;; Robotインスタンス
(def robot (Robot.))
(.setAutoDelay robot 30)

;;; 基本操作
(defn pause
  "`ms`ミリ秒待つ。"
  [ms]
  (.delay robot ms))

;;; マウス操作
(def ^:private btn-map
  {:left InputEvent/BUTTON1_MASK
   :middle InputEvent/BUTTON2_MASK
   :right InputEvent/BUTTON3_MASK})

(defn- click-on [x y b]
  (let [btn (b btn-map)]
    (doto robot
      (.mouseMove x y)
      (.mousePress btn)
      (.mouseRelease btn))))
(defn click-left-on [x y] (click-on x y :left))
(defn click-middle-on [x y] (click-on x y :middle))
(defn click-right-on [x y] (click-on x y :right))

(defn move-to [x y] (.mouseMove robot x y))

(defn- location []
  (let [pt (.getLocation (MouseInfo/getPointerInfo))]
    [(.-x pt) (.-y pt)]))

(defn wait-and-locate
  "`s`秒待ってから、マウス位置を返す。デフォは5秒。"
  ([] (wait-and-locate "the target"))
  ([caption] (wait-and-locate caption 5))
  ([caption s]
   (print (str "Please move mouse for " caption "...")) (flush)
   (dotimes [n s]
     (print (- s n)) (flush)
     (pause 1000))
   (let [pos (location)]
     (println pos)
     pos)))

;;; キーボード操作
(def kc-map
  ;; kcはキーの組み合わせを抽象化したもの(key combo)。
  ;; 実態はキーイベントコードのベクタ。
  ;; 0以上のmodifier(ShiftとかAltね)と、1つの基本キーからなる。
  ;; ex: [KeyEvent/VK_A]
  ;;     [KeyEvent/VK_SHIFT KeyEvent/VK_B]
  ;;     [KeyEvent/VK_SHIFT KeyEvent/VK_ALT KeyEvent/VK_C]
  ;; キーワードからkcへ変換できる。
  ;; :spaceのように、単純に変換(VK_SPACE)できるものもあるし、
  ;; ↓このマップで変換するものもある。
  {:esc [KeyEvent/VK_ESCAPE]
   :bs [KeyEvent/VK_BACK_SPACE]
   :asterisk [KeyEvent/VK_SHIFT KeyEvent/VK_COLON]
   :ampersand [KeyEvent/VK_SHIFT KeyEvent/VK_6]
   :back-quote [KeyEvent/VK_SHIFT KeyEvent/VK_AT]
   :plus [KeyEvent/VK_SHIFT KeyEvent/VK_SEMICOLON]
   :braceleft [KeyEvent/VK_SHIFT KeyEvent/VK_BRACELEFT]
   :braceright [KeyEvent/VK_SHIFT KeyEvent/VK_BRACERIGHT]
   :exclamation-mark [KeyEvent/VK_SHIFT KeyEvent/VK_1]
   })
(def spchar-map
  {\* :asterisk
   \+ :plus
   \& :ampersand
   \@ :at
   \` :back-quote
   \\ :back-slash
   \[ :bracketleft
   \] :bracketright
   \{ :braceleft
   \} :braceright
   \space :space
   \: :colon
   \, :comma
   \! :exclamation-mark
   })

(defn- s->keycode [s]
  (try
    (->> s
         (str "java.awt.event.KeyEvent/VK_")
         load-string)
    (catch RuntimeException e nil)))
(def ^:private c->keycode
  (comp s->keycode #(Character/toUpperCase %)))
(def ^:private upper-snake
  (comp #(clojure.string/replace % "-" "_")
        #(.toUpperCase %)))
(def ^:private kw->keycode
  (comp s->keycode upper-snake name))

(defn- type-kc
  "あるkcのタイプ(押して離す)をエミュレートするプリミティブ。"
  [kc]
  (doseq [k kc]
    (.keyPress robot k))
  (doseq [k (reverse kc)]
    (.keyRelease robot k)))

(defn- kw->kc
  [kw]
  (if-let [kc (kc-map kw)]
    kc
    (when-let [keycode (kw->keycode kw)]
      [keycode])))
(defn- letter->kc [c]
  (let [mods (if (Character/isUpperCase c) [KeyEvent/VK_SHIFT] [])
        k (c->keycode c)]
    (conj mods k)))
(defn- digit->kc [c]
  [(c->keycode c)])
(def ^:private spchar->kc
  (comp kw->kc spchar-map))

(defn- char->kc
  "文字をkcへ。
  サポートする文字種には制限あり。
  "
  [c]
  (let [kc (cond
             (Character/isLetter c) (letter->kc c)
             (Character/isDigit c) (digit->kc c)
             (spchar-map c) (spchar->kc c)
             :else nil)]
    (when-not kc
      (throw (ex-info (str "Unsupported character: " c) {:char c})))
    kc))

(defn- string->kcs
  "文字列をkcのシーケンスへ。"
  [s]
  (map char->kc s))

(defn- type-kcs
  "複数のkcを連続してタイプ(押して離す)する。"
  [kcs]
  (doseq [kc kcs]
    (type-kc kc)))

(defn type-key
  "`kw`に対応するキーをタイプ(押して離す)する。
  `kw`に指定できるのは、:enterや:escなど。"
  [kw]
  (let [kc (kw->kc kw)]
    (when-not kc
      (throw (ex-info (str "Unsupported key " kw) {:key kw})))
    (type-kc kc)))

(defn type-string
  "文字列をタイプする。"
  [& ss]
  (let [kcs (string->kcs (apply str ss))]
    (type-kcs kcs)))


;;; スクリプト操作
(def ^:dynamic *loop-i* nil)
(defn- pprint-with-meta
  "メタ情報とともにpretty printする。"
  [object]
  (let [orig-dispatch pp/*print-pprint-dispatch*]
    (pp/with-pprint-dispatch
      (fn [obj]
        (when-let [m (meta obj)]
          (print "^")
          (orig-dispatch m)
          (print " "))
        (orig-dispatch obj))
      (pp/pprint object))))

(defn- type-string-with-i [& ss]
  (->> ss
       (replace {:i *loop-i*})
       (apply type-string)))

(def opecode-map
  {:pause #'pause
   :click-left-on #'click-left-on
   :click-middle-on #'click-middle-on
   :click-right-on #'click-right-on
   :move-to #'move-to
   :type-key #'type-key
   :type-string #'type-string-with-i})
(defn- handler-or-die [opecode]
  (let [h (opecode-map opecode)]
    (when-not h
      (throw (ex-info (str "Unsupported operation: " opecode)
                      {:opecode opecode})))
    h))

(def ^:private load-script
  (comp clojure.edn/read-string slurp))
(defn- store-script [fpath ops]
  (let [script (with-out-str (pprint-with-meta ops))]
    (spit fpath script)))

(defn- learn-location [caption]
  (wait-and-locate caption))

(declare loop-operations)
(defn- rebuild-operation [opecode operands m]
  (-> (concat [opecode] operands)
      vec
      (with-meta m)))
(defn- play-operation [op]
  (let [[opecode & args] op]
    (case opecode
      :nop op
      :loop (apply loop-operations args) ; args are actually ops
      (let [m (meta op)                ;; meta info
            h (handler-or-die opecode) ;; handler
            operands (if (some #{:?} args)
                       (learn-location (:caption m))
                       args)]
        (apply h operands)
        (rebuild-operation opecode operands m)))))

(defn- play-operations [ops]
  (->> ops
       (map play-operation)
       doall
       vec))

(defn- loop-operations [n ops]
  (if-not (pos? n)
    [:loop n ops]

    ;; 1周実行して、learnした場合は、そこで終了。
    ;; learn不要だった場合は、残りの周回を実施。
    (let [first-run (binding [*loop-i* 0]
                      (play-operations ops))]
      (when (= first-run ops)
        (doseq [i (range 1 n)]
          (binding [*loop-i* i]
            (play-operations ops))))
      [:loop n first-run])))

(comment
  (doseq [i (range 3)]
    (click-left-on 10 10)
    (pause 100)
    (type-string "Chrome")
    (type-key :enter)
    (pause 2000))
  (-> (load-script "run1.edn")
      (play-operations))
  )

(defn- show-usage []
  (println "Usage: cljbot SCRIPT")
  (println "   ex: cljbot run1"))

(defn -main
  [& args]
  (let [[script] args]
    (when (nil? script)
      (show-usage)
      (System/exit -1))
    (let [ops (load-script (str script ".edn"))
          res (play-operations ops)]

      ;; learnした場合は、learn後のスクリプトを出力。
      (when-not (= res ops)
        (store-script (str script "-learned.edn") res)))))

