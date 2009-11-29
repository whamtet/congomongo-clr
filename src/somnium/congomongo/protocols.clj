(ns somnium.congomongo.protocols
  (:use somnium.congomongo.util)
  (:import [com.mongodb DBObject BasicDBObject DBCursor]
           [com.mongodb.util JSON]
           [somnium.congomongo ClojureDBObject]
           [clojure.lang Keyword IPersistentCollection IPersistentMap]
           [java.util List Map Set Iterator]))

;; pure clojure with protcols + keyword memoization outperforms
;; direct java with ClojureDBObject !!

(def *keywordizer* (memoize keyword))
(def *anti-keywordizer* (memoize named))

(defprotocol MongoKey
  (mongo-key [k]))

(extend-protocol
 MongoKey
 Keyword 
 (mongo-key [k] (.getName k))
 String
 (mongo-key [s] s)
 Object
 (mongo-key [o] (.toString o)))

(defprotocol Clojurize
  "utility for coercing maps.
   takes an object and an optional keyword memoizer.
   Reusing a memoizer on large collections offers a
   near tenfold speed increase."
  (clojurize ([o] [o f])))

(extend-protocol
 Clojurize
 ClojureDBObject
 (clojurize ([o] (.toClojure o)) ([o f] (.toClojure o)))
 DBObject
 (clojurize [o]
            (let [m             (transient {})
                  #^Iterator it (.iterator #^Set (.keySet o))]
              (while (.hasNext it)
                     (let [#^String k (.next it)]
                       (assoc! m (*keywordizer* k) (.get o k))))
              (persistent! m)))
 Iterator
 (clojurize
  ([o] (map #(clojurize %) (iterator-seq o))))
 List
 (clojurize
  ([o]
     (persistent!
      (reduce
       (fn [tv i]
         (conj! tv (clojurize i)))
       (transient [])
       o))))
 Object
 (clojurize ([o] o))
 nil
 (clojurize ([o] o)))

(defprotocol Mongofy
  "utilty for creating mongo objects"
  (mongofy [o]))

(extend-protocol
 Mongofy
 ClojureDBObject
 (mongofy ([o] o))
 DBObject
 (mongofy ([o] o))
 IPersistentMap
 (mongofy [m] (ClojureDBObject. m))
 Map
 (mongofy [m] (ClojureDBObject. m))

; can't seem to compete on object creation speed
; (mongofy
;  ([m] 
;     (let [#^DBObject dbo (BasicDBObject.)
;           #^Iterator it  (.iterator #^Set (.keySet m))]
;       (while (.hasNext it)
;              (let [#^Object k (.next it)
;                    #^String mk (mongo-key k)]
;                (.put dbo mk #^Object (mongofy (.get m k)))))
;       dbo)))

 List
 (mongofy [l]
          (persistent!
           (reduce #(conj! %1 (mongofy %2))
                   (transient [])
                   l)))
 Keyword
 (mongofy [o] (*anti-keywordizer* o))
 Object
 (mongofy [o] o)
 nil
 (mongofy [o] o))

(defprotocol Jsonize
  (jsonize [o]))

(extend-protocol
 Jsonize
 Iterator
 (jsonize [o] (JSON/serialize (iterator-seq o)))
 DBObject
 (jsonize [o] (JSON/serialize o))
 IPersistentCollection
 (jsonize [o] (JSON/serialize (mongofy o)))
 Object
 (jsonize [o] (JSON/serialize o)))