; Copyright (c) 2009 Andrew Boekhoff

; Permission is hereby granted, free of charge, to any person obtaining a copy
; of this software and associated documentation files (the "Software"), to deal
; in the Software without restriction, including without limitation the rights
; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
; copies of the Software, and to permit persons to whom the Software is
; furnished to do so, subject to the following conditions:

; The above copyright notice and this permission notice shall be included in
; all copies or substantial portions of the Software.

; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
; THE SOFTWARE.

(ns
  #^{:author "Andrew Boekhoff",
     :doc "Various wrappers and utilities for the mongodb-java-driver"}
  somnium.congomongo
  (:use     [somnium.congomongo config protocols util coerce]
            [clojure.contrib def])
  (:import  [com.mongodb Mongo DB DBCollection DBObject]
            [com.mongodb.util JSON]))

(defunk mongo!
  "Creates a Mongo object and sets the default database.
   Keyword arguments include:
   :host -> defaults to localhost
   :port -> defaults to 27017
   :db   -> defaults to nil (you'll have to set it anyway, might as well do it now.)"
  {:arglists '({:db ? :host "localhost" :port 27017})}
  [:db nil :host "localhost" :port 27017]
   (let [mongo  (Mongo. host port)
         n-db     (if db (.getDB mongo (named db)) nil)]
     (reset! *mongo-config*
             {:mongo mongo
              :db    n-db})
     true))

(defmacro return-fns
  [to]
  `(case ~to
         :nil     (constantly nil)
         :clojure clojurize
         :mongo   (fn [o#] (iterator-seq o#))
         :json    jsonize))

;; perhaps split *mongo-config* out into vars for thread-local
;; changes. 

(defn get-coll
  "Returns a DBCollection object"
  [collection]
  (.getCollection #^DB (:db @*mongo-config*)
                  #^String (named collection)))

(defn *fetch
  "worker function for fetch macro"
  [{:keys [collection where only skip limit as]}]
  (let [c (get-coll collection)
        w (mongofy (or where {}))
        o (mongofy (if only (zipmap only (repeat 1)) {}))
        s (or skip 0)
        l (if limit (- 0 (Math/abs limit)) 0)
        f (return-fns (or as :clojure))]
    (f (.find #^DBCollection c
              #^DBObject w
              #^DBObject o
              (int s) (int l)))))

(defmacro fetch
  "syntax sugar take one"
  [collection & options]
  (let [{:keys [where] :as omap} (map-keys keyword (apply hash-map options))
        wmap   (if where
                 (->> where
                      (partition-map-by keyword?)
                      (map-vals parse-mongosyms))
                 {})]
    `(*fetch (merge ~(dissoc omap :where)
                    {:where ~wmap :collection ~collection}))))

(defmacro fetch-one
  [collection & options]
  (let [as (:as (map-keys keyword (apply hash-map options)))]
    `(let [d# (fetch ~collection ~@options :limit 1)]
      (if (= ~as :json) d# (first d#)))))

(defnk insert! 
  "Inserts a map into collection. Will not overwrite existing maps.
   Takes optional from and to keyword arguments. To insert
   as a side-effect only specify :to as nil."
  {:arglists '([coll obj {:many false :from :clojure :to nil}])}
  [coll obj :from :clojure :to nil :many false]
  (let [res (.insert #^DBCollection (get-coll coll)
                     (mongofy obj))]
      (if to
        ((return-fns to) res))))

(defnk mass-insert!
  {:arglists '([coll objs {:from :clojure :to :clojure}])}
  [coll objs :from :clojure :to :clojure]
  (insert! coll objs :from from :to to :many true))
  
;; should this raise an exception if _ns and _id aren't present?
(defnk update!
   "Alters/inserts a map in a collection. Overwrites existing objects.
   The shortcut forms need a map with valid :_id and :_ns fields or
   a collection and a map with a valid :_id field."
   {:arglists '(collection old new {:upsert true :multiple false :as :clojure})}
   [coll old new :upsert true :multiple false :as :clojure]
   ((return-fns as) (.update #^DBCollection (get-coll coll)
                             #^DBObject (mongofy old)
                             #^DBObject (mongofy new)
                             upsert multiple)))

(defnk destroy!
   "Removes map from collection. Takes a collection name and
    a query map"
   {:arglists '(collection where {:from :clojure})}
   [c q :from :clojure]
   (.remove (get-coll c)
            #^DBObject (mongofy q)))

(defnk add-index!
   "Adds an index on the collection for the specified fields if it does not exist.
    Options include:
    :unique -> defaults to false
    :force  -> defaults to true"
   {:arglists '(collection fields {:unique false :force true})}
   [c f :unique false :force true]
   (-> (get-coll c)
       (.ensureIndex (coerce-fields f) force unique)))

(defn drop-index!
  "Drops an index on the collection for the specified fields"
  [coll fields]
  (.dropIndex (get-coll coll) (coerce-fields fields)))

(defn drop-all-indexes!
  "Drops all indexes from a collection"
  [coll]
  (.dropIndexes (get-coll coll)))

(defnk get-indexes
  "Get index information on collection"
  {:arglists '([collection :as (:clojure)])}
   [coll :as :clojure]
   (mongofy (.getIndexInfo (get-coll coll))))

(defn drop-database!
 "drops a database from the mongo server"
 [title]
 (.dropDatabase (:mongo @*mongo-config*) (named title)))

(defn set-database!
  "atomically alters the current database"
  [title]
  (if-let [db (.getDB (:mongo @*mongo-config*) (named title))]
    (swap! *mongo-config* merge {:db db})
    (throw (RuntimeException. (str "database with title " title " does not exist.")))))

(defn databases
  "List databases on the mongo server" []
  (.getDatabaseNames (:mongo @*mongo-config*)))

(defn collections
  "Returns the set of collections stored in the current database" []
  (.getCollectionNames #^DB (:db @*mongo-config*)))

(defn drop-coll!
  "Permanently deletes a collection. Use with care."
  [collection]
  (.drop #^DBCollection (.getCollection #^DB (:db @*mongo-config*)
                                        #^String (named collection))))
