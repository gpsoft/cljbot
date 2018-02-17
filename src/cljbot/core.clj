(ns cljbot.core
  (:require [clojure.pprint :as pp])
  (:import [java.awt Robot MouseInfo PointerInfo]
           [java.awt.event InputEvent KeyEvent]
           [javax.swing KeyStroke])
  (:gen-class))

(def robot (Robot.))
(.setAutoDelay robot 30)

(defn pause [ms]
  (.delay robot ms))

(defn click-on [x y b]
  (let [btn (if (= b :right) InputEvent/BUTTON3_MASK InputEvent/BUTTON1_MASK)]
    (doto robot
      (.mouseMove x y)
      (.mousePress btn)
      (.mouseRelease btn))))
(defn click-left-on [x y] (click-on x y :left))
(defn click-right-on [x y] (click-on x y :right))
(defn move-to [x y]
  (.mouseMove robot x y))
(defn location []
  (let [pt (.getLocation (MouseInfo/getPointerInfo))]
    [(.-x pt) (.-y pt)]))
(defn wait-and-locate
  ([caption] (wait-and-locate caption 5))
  ([caption s]
   (print (str "Please move mouse for " caption "...")) (flush)
   (dotimes [n s]
     (print (- s n)) (flush)
     (pause 1000))
   (let [pos (location)]
     (println pos)
     pos)))

;; ks is a vector of keys
;; consists of zero or more modifier keys and one actual key
;; ex: [KeyEvent/VK_A]
;;     [KeyEvent/VK_SHIFT KeyEvent/VK_B]
;;     [KeyEvent/VK_SHIFT KeyEvent/VK_ALT KeyEvent/VK_C]
(def ks-map
  {:enter [KeyEvent/VK_ENTER]
   :esc [KeyEvent/VK_ESCAPE]
   :bs [KeyEvent/VK_BACK_SPACE]
   :colon [KeyEvent/VK_COLON]
   :comma [KeyEvent/VK_COMMA]
   :space [KeyEvent/VK_SPACE]
   :tab [KeyEvent/VK_TAB]})

(defn char->ks [c]
  (when
    (not (or (Character/isLetter c) (Character/isDigit c)))
    (throw (ex-info "Only alphabets are supported." {:key c})))
  (let [mods (if (Character/isUpperCase c) [KeyEvent/VK_SHIFT] [])
        k (load-string (str "KeyEvent/VK_" (Character/toUpperCase c)))]
    (conj mods k)))
(defn string->kss [s]
  (map char->ks s))
(defn type-key [ks]
  (let [ks (get-in ks-map [ks] ks)]
    (doseq [k ks]
      (.keyPress robot k))
    (doseq [k (reverse ks)]
      (.keyRelease robot k))))
(defn type-keys [kss]
  (doseq [ks kss]
    (type-key ks)))
(defn type-string [s]
  (let [kss (string->kss s)]
    (type-keys kss)))
(defn ppm [obj]
        (let [orig-dispatch pp/*print-pprint-dispatch*]
          (pp/with-pprint-dispatch 
            (fn [o]
              (when (meta o)
                (print "^")
                (orig-dispatch (meta o))
                (pp/pprint-newline :fill))
              (orig-dispatch o))
            (pp/pprint obj))))
(declare loop-operations)
(def opecode-map
  {:pause #'pause
   :click-left-on #'click-left-on
   :type-string #'type-string
   })

(defn load-script [fpath]
  (clojure.edn/read-string (slurp fpath)))
(defn store-script [fpath ops]
  (spit
    fpath
    (binding [*print-meta* true]
      (with-out-str (ppm ops))
      #_(pr-str ops))))
(defn learn-location [caption]
  (wait-and-locate caption))
(defn play-operation [op]
  (let [[opecode & args] op]
    (case opecode
      :nop op
      :loop (apply loop-operations args)
      (let [m (meta op)
            operands (if (some #{:?} args)
                       (learn-location (:caption m))
                       args)]
        (apply (opecode opecode-map) operands)
        (with-meta (vec (concat [opecode] operands)) m)))))
(defn play-operations [ops]
  (vec (doall (map play-operation ops))))
(defn loop-operations [n ops]
  (if-not (pos? n)
    [:loop n ops]
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

(defn show-usage []
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
      (when-not (= ops res)
        (store-script (str script "-learned.edn") res)))))
