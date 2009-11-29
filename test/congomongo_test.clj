(ns congomongo-test
  (:use clojure.test
        somnium.congomongo
        somnium.congomongo.config
        somnium.congomongo.util
        somnium.congomongo.protocols
        clojure.contrib.pprint)
  (:import [com.mongodb.util JSON]))

(deftest protocol-coercions
  (let [forms   [clojurize mongofy]
        input   {:a {:b "c" :d "e" :f ["a" "b" "c"] :g {:h ["i" "j"]}}}
        results (for [from forms
                      to   forms
                      :let [start (from input)
                            x (to start)
                            y (from x)]
                      :when (not= from to)]
                  [start y from to x])]
    (doseq [t results]
      (doseq [x [(t 0) "->" (t 4) "->" (t 1)]] (println x))
      (is (= (t 0) (t 1)) (str (t 2) " " (t 3))))))

(def test-db "congomongotestdb")
(defn setup! [] (mongo! :db test-db))
(defn teardown! [] (drop-database! test-db))

(defmacro with-mongo [& body]
  `(do
     (setup!)
     ~@body
     (teardown!)))

(deftest databases-test
  (with-mongo
    (let [test-db2 "congomongotestdb-part-deux"]
    
      (is (= test-db (.getName (@*mongo-config* :db)))
          "default DB exists")
      (set-database! test-db2)

      (is (= test-db2 (.getName (@*mongo-config* :db)))
          "changed DB exists")
      (drop-database! test-db2))))

;(defn make-points! []
;  (println "slow insert of 10000 points:")
;  (time
;   (doseq [x (range 100)
;           y (range 100)]
;     (insert! :points {:x x :y y}))))

;(deftest slow-insert-and-fetch
;  (with-mongo
;    (make-points!)
;    (is (= (* 100 100)) (fetch-count :points))
;    (is (= (fetch-count :points
;                        :where [:x 42]) 100))))

;(deftest destroy
;  (with-mongo
;    (make-points!)
;    (let [point-id (:_id (fetch-one :points))]
;      (destroy! :points
;                {:_id point-id})
;      (is (= (fetch-count :points) (dec (* 100 100))))
;      (is (= nil (fetch-one :points
;                            :where {:_id point-id}))))))

;(deftest update
;  (with-mongo
;    (make-points!)
;    (let [point-id (:_id (fetchone :points))]
;      (update! :points
;               {:_id point-id}
;               {:x "suffusion of yellow"})
;      (is (= (:x (fetch :points
;                        :where [:_id point-id]))
;             "suffusion of yellow")))))

;; ;; mass insert chokes on excessively large inserts
;; ;; will need to implement some sort of chunking algorithm

; (deftest mass-insert
;   (with-mongo
;     (println "mass insert of 10000 points")
;     (time
;      (mass-insert! :points
;                    (for [x (range 100) y (range 100)]
;                      {:x x
;                       :y y
;                       :z (* x y)})))
;     (is (= (* 100 100)
;            (fetch-count :points))
;         "mass-insert okay")))

; (deftest basic-indexing
;   (with-mongo
;     (make-points!)
;     (add-index! :points [:x])
;     (is (some #(= (into {} (% "key")) {"x" 1})
;               (get-indexes :points)))))
