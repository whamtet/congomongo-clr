(ns somnium.congomongo.coerce
  (:use [clojure.data.json :only [write-str read-str]]
        )
  (:import [clojure.lang IPersistentMap Keyword IPersistentCollection Ratio Symbol]
           [System.Collections IEnumerable IDictionary IList]
           ;  [java.util Map List Set]
           ;  [com.mongodb DBObject BasicDBObject BasicDBList]
           ;  [com.mongodb.gridfs GridFSFile]
           ;  [com.mongodb.util JSON]
           ))

(assembly-load "MongoDB.Bson")
(import '[MongoDB.Bson BsonArray BsonInt32 BsonInt64 BsonBoolean
          BsonDateTime BsonDouble BsonNull BsonRegularExpression
          BsonString BsonSymbol BsonDocument BsonElement
          BsonValue BsonExtensionMethods
          ])

(defn seqable?
  "Returns true if (seq x) will succeed, false otherwise."
  [x]
  (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (nil? x)
      (instance? IEnumerable x)
      (-> x .GetType .IsArray)
      (string? x)
      (instance? IDictionary x)
      ))

(def ^{:dynamic true
       :doc "Set this to false to prevent coercion from setting string keys to keywords"}
  *keywordize* true)

;;; Converting data from mongo into Clojure data objects

(defprotocol ConvertibleFromMongo
  (mongo->clojure [o keywordize]))

(extend-protocol ConvertibleFromMongo
  BsonDocument
  (mongo->clojure [^BsonDocument m keywordize]
                  (reduce
                   (if keywordize
                     (fn [m ^BsonElement e]
                       (assoc m (keyword (.Name e)) (mongo->clojure (.Value e) keywordize)))
                     (fn [m ^BsonElement e]
                       (assoc m (.Name e) (mongo->clojure (.Value e) keywordize))))
                   {} (seq m)))
  IEnumerable
  (mongo->clojure [^IEnumerable l keywordize]
                  (mapv #(mongo->clojure % keywordize) l))
  Object
  (mongo->clojure [o keywordize] o)
  nil
  (mongo->clojure [o keywordize] o)
  BsonInt32
  (mongo->clojure [o keywordize] (int o))
  BsonInt64
  (mongo->clojure [o keywordize] (long o))
  BsonBoolean
  (mongo->clojure [o keywordize] (.Value o))
  BsonDateTime
  (mongo->clojure [o keywordize] (str o))
  BsonDouble
  (mongo->clojure [o keywordize] (.Value o))
  BsonNull
  (mongo->clojure [o keywordize] nil)
  BsonRegularExpression
  (mongo->clojure [o keywordize] (-> o .Pattern re-pattern))
  BsonString
  (mongo->clojure [o keywordize]
                  (let [s (str o)]
                    (if (.StartsWith s ":")
                      (keyword (.Substring s 1))
                      s)))
  BsonSymbol
  (mongo->clojure [o keywordize] (-> o str symbol))
  )


;; ;;; Converting data from Clojure into data objects suitable for Mongo

(defprotocol ConvertibleToMongo
  (clojure->mongo [o]))

(extend-protocol ConvertibleToMongo
  IPersistentMap
  (clojure->mongo [m]
                  (let [out (BsonDocument.)]
                    (doseq [[k v] m]
                      (.Add out (str k) (clojure->mongo v)))
                    out))
  IPersistentCollection
  (clojure->mongo [m] (BsonArray. (map clojure->mongo m)))
  Keyword
  (clojure->mongo [^Keyword o]
                  (BsonString. (str o)))
  nil
  (clojure->mongo [o] BsonNull/Value)
  Object
  (clojure->mongo [o] (BsonValue/Create o))
  Ratio
  (clojure->mongo [o] (BsonValue/Create (.ToDouble o nil)))
  Symbol
  (clojure->mongo [o] (-> o str BsonValue/Create))
  Int64
  (clojure->mongo [o] (BsonInt64. o))
  )

(def to-json #(BsonExtensionMethods/ToJson % (class %)))
(def to-edn #(-> % to-json read-str))

(let [translations {[:clojure :mongo  ] clojure->mongo
                    [:clojure :json   ] write-str
                    [:mongo   :clojure] #(mongo->clojure % *keywordize*)
                    [:mongo   :json   ] to-json
                    [:json    :clojure] #(read-str % :key-fn (if *keywordize*
                                                               keyword
                                                               identity))
                    [:json    :mongo  ] #(BsonDocument/parse %)}]
  (defn coerce
    "takes an object, a vector of keywords:
    from [ :clojure :mongo :json ]
    to   [ :clojure :mongo :json ],
    and an an optional :many keyword parameter which defaults to false"
    {:arglists '([obj [:from :to] {:many false}])}
    [obj from-and-to & {:keys [many] :or {many false}}]
    (let [[from to] from-and-to]
      (cond (= from to) obj
            (nil?   to) nil
            :else
            (if-let [f (translations from-and-to)]
              (if many
                (map f (if (seqable? obj)
                         obj
                         (iterator-seq obj)))
                (f obj))
              (throw (Exception. "unsupported keyword pair")))))))

(defn dbobject [& args]
  (clojure->mongo
   (into {} (map vec (partition 2 args)))))

(defn coerce-fields
  "Used for creating argument object for :only - unordered,
   maps truthy to 1 and falsey to 0, default 1."
  [fields]
  (clojure->mongo ^IPersistentMap (if (map? fields)
                                    (into {} (for [[k v] fields]
                                               [k (if v 1 0)]))
                                    (zipmap fields (repeat 1)))))

(defn coerce-ordered-fields
  "Used for creating index specifications and sort specifications.
   Accepts a vector of fields or field/value pairs. Produces an
   ordered object of field/value pairs - default 1."
  [fields]
  (clojure->mongo ^IPersistentMap (apply array-map
                                         (flatten
                                          (for [f fields]
                                            (if (vector? f)
                                              f
                                              [f 1]))))))

(defn coerce-index-fields
  "Used for creating index specifications.
   Deprecated as of 0.3.3.
   [:a :b :c] => (array-map :a 1 :b 1 :c 1)
   [:a [:b 1] :c] => (array-map :a 1 :b -1 :c 1)

   See also somnium.congomongo/add-index!"
  [fields]
  (coerce-ordered-fields fields))
