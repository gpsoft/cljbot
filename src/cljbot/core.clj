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
(defn pause [ms]
  (.delay robot ms))

;;; マウス操作
(def btn-map
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
(def ks-map
  ;; ksはキーストロークを抽象化したもの。
  ;; 実態はキーコードのベクタ。
  ;; 0以上のmodifier(ShiftとかAltね)と、1つの基本キーからなる。
  ;; ex: [KeyEvent/VK_A]
  ;;     [KeyEvent/VK_SHIFT KeyEvent/VK_B]
  ;;     [KeyEvent/VK_SHIFT KeyEvent/VK_ALT KeyEvent/VK_C]
  {:enter [KeyEvent/VK_ENTER]
   :esc [KeyEvent/VK_ESCAPE]
   :bs [KeyEvent/VK_BACK_SPACE]
   :colon [KeyEvent/VK_COLON]
   :comma [KeyEvent/VK_COMMA]
   :space [KeyEvent/VK_SPACE]
   :tab [KeyEvent/VK_TAB]})

(defn- ->keycode [c]
  (load-string
    (str "java.awt.event.KeyEvent/VK_" (Character/toUpperCase c))))

(defn- char->ks
  "文字をksへ。
  サポートする文字種は限定的。
  "
  [c]
  (when
    (not (or (Character/isLetter c) (Character/isDigit c)))
    (throw (ex-info (str "Unsupported character: " c) {:key c})))
  (let [mods (if (Character/isUpperCase c) [KeyEvent/VK_SHIFT] [])
        k (->keycode c)]
    (conj mods k)))

(defn- string->kss
  "文字列をksのシーケンスへ。"
  [s]
  (map char->ks s))

(defn- type-key*
  "あるksのタイプ(押して離す)をエミュレートする。
  ksではなく、:enterや:escなども指定可能。"
  [ks-or-key]
  (let [ks (get-in ks-map [ks-or-key] ks-or-key)]
    (doseq [k ks]
      (.keyPress robot k))
    (doseq [k (reverse ks)]
      (.keyRelease robot k))))

(defn- type-keys
  "複数のksを連続してタイプ(押して離す)する。"
  [kss]
  (doseq [ks kss]
    (type-key* ks)))

(defn type-key
  "`k`に対応するキーをタイプ(押して離す)する。
  `k`に指定できるのは、:enterや:escなど。"
  [k]
  (type-key* k))

(defn type-string
  "文字列をタイプする。"
  [s]
  (let [kss (string->kss s)]
    (type-keys kss)))

;;; スクリプト操作
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

(def opecode-map
  {:pause #'pause
   :click-left-on #'click-left-on
   :click-middle-on #'click-middle-on
   :click-right-on #'click-right-on
   :move-to #'move-to
   :type-key #'type-key
   :type-string #'type-string})

(defn- load-script [fpath]
  (clojure.edn/read-string (slurp fpath)))
(defn- store-script [fpath ops]
  (spit fpath (with-out-str (pprint-with-meta ops))))

(defn- learn-location [caption]
  (wait-and-locate caption))

(declare loop-operations)
(defn- play-operation [op]
  (let [[opecode & args] op]
    (case opecode
      :nop op
      :loop (apply loop-operations args) ; args are actually ops
      (let [m (meta op)             ;; meta info
            h (opecode opecode-map) ;; handler function
            operands (if (some #{:?} args)
                       (learn-location (:caption m))
                       args)]
        (apply h operands)
        (with-meta (vec (concat [opecode] operands)) m)))))

(defn- play-operations [ops]
  (vec (doall (map play-operation ops))))

(defn- loop-operations [n ops]
  (if-not (pos? n)
    [:loop n ops]

    ;; 1周実行して、learnした場合は、そこで終了。
    ;; learn不要だった場合は、残りの周回を実施。
    (let [first-run (play-operations ops)]
      (when (= first-run ops)
        (doall (for [i (range (dec n))]
                 (play-operations first-run))))
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
      (System/exit -1)
      )
    (let [ops (load-script (str script ".edn"))
          res (play-operations ops)]

      ;; learnした場合は、learn後のスクリプトを出力。
      (when-not (= ops res)
        (store-script (str script "-learned.edn") res)))))

