(ns cljbot.core
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
  ([] (wait-and-locate 5000))
  ([ms]
   (print "Move mouse...")
   (flush)
   (pause ms)
   (prn (location))))

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

(comment
  (doseq [i (range 3)]
    (click-left-on 10 10)
    (pause 100)
    (type-string "Chrome")
    (type-key :enter)
    (pause 2000))
  )

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (wait-and-locate))
