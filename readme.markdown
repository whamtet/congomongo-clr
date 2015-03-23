CongoMongo CLR
===========

What?
------
A toolkit for using MongoDB with ClojureCLR.  This is a port of [CongoMongo](https://github.com/aboekhoff/congomongo) to ClojureCLR.  Some features of CongoMongo have not been ported because they are not supported in the underlying C# library.  Features skipped include setting read preferences and post-connection authentication.

Basics
--------

### Setup

#### import
```clojure
(ns my-mongo-app
  (:require [somnium.congomongo :as m]))
```
#### make a connection
```clojure
(def conn
  (m/make-connection "mydb"
                     :host "127.0.0.1"
                     :port 27017))
=> #'user/conn

conn => {:mongo #<MongoClient Mongo: /127.0.0.1:20717>, :db #<DBApiLayer mydb>}
```
#### set the connection globally
```clojure
(m/set-connection! conn)
```
#### or locally
```clojure
(m/with-mongo conn
    (m/insert! :robots {:name "robby"}))
```
#### specify a write concern
```clojure
(m/set-write-concern conn :journaled)
```
These are the new, official write concerns as of release 0.4.0, using the 2.10 Java
driver. The earlier write concerns are shown in parentheses and are deprecated as
of the 0.4.0 release.
* :errors-ignored will not report any errors - fire and forget (:none)
* :unacknowledged will report network errors - but does not wait for the write to be acknowledged (:normal - this was the default prior to 0.4.0)
* :acknowledged will report key constraint and other errors - this is the default (:safe, :strict was deprecated in 0.1.9)
* :journaled waits until the primary has sync'd the write to the journal (:journal-safe)
* :fsynced waits until a write is sync'd to the filesystem (:fsync-safe)
* :replica-acknowledged waits until a write is sync'd to at least one replica as well (:replicas-safe, :replica-safe)
* :majority waits until a write is sync'd to a majority of replica nodes (no previous equivalent)

#### specify a read preference
You can pass a simple read preference (without tags) to each function accepting read preferences. This may look like:

```clojure
(m/fetch :fruit :read-preference :nearest)
```

to get the fruit from the nearest server. You may create more advances read preferences using the `read-preference` function.

```clojure
(let [p (m/read-preference :nearest {:location "Europe"})]
   (fetch :fruit :read-preference p)
)
```
to be more specific to get the nearest fruit. You may also set a default `ReadPreference` on a per collection or connection basis using `set-read-preference` or `set-collection-read-preference!`.

```clojure
(m/set-read-preference conn :primary-preferred)
(m/set-collection-read-preference! :news :secondary)
```


### Simple Tasks
------------------

#### create
```clojure
(m/insert! :robots
           {:name "robby"})
```
#### read
```clojure
(def my-robot (m/fetch-one :robots)) => #'user/my-robot

my-robot => {:name "robby",
             :_id  #<ObjectId> "0c23396f7e53e34a4c8cf400">}
```
#### update
```clojure
(m/update! :robots my-robot (merge my-robot {:name "asimo"}))

=>  #<WriteResult { "serverUsed" : "/127.0.0.1:27017" ,
                    "updatedExisting" : true ,
                    "n" : 1 ,
                    "connectionId" : 169 ,
                    "err" :  null  ,
                    "ok" : 1.0}>
```
#### destroy
```clojure
(m/destroy! :robots {:name "asimo"}) => #<WriteResult { "serverUsed" : "/127.0.0.1:27017" ,
                                                        "n" : 1 ,
                                                        "connectionId" : 170 ,
                                                        "err" :  null  ,
                                                        "ok" : 1.0}>
(m/fetch :robots) => ()
```
### More Sophisticated Tasks
----------------------------

#### mass inserts
```clojure
(dorun (m/mass-insert!
         :points
         (for [x (range 100) y (range 100)]
           {:x x
            :y y
            :z (* x y)}))

=> nil ;; without dorun this would produce a WriteResult with 10,000 maps in it!

(m/fetch-count :points)
=> 10000
```
#### ad-hoc queries
```clojure
(m/fetch-one
  :points
  :where {:x {:$gt 10
              :$lt 20}
          :y 42
          :z {:$gt 500}})

=> {:x 12, :y 42, :z 504, :_id ... }
```

#### aggregation (requires mongodb 2.2 or later)
```clojure
(m/aggregate
  :expenses
  {:$match {:type "airfare"}}
  {:$project {:department 1, :amount 1}}
  {:$group {:_id "$department", :average {:$avg "$amount"}}})

=> {:serverUsed "...", :result [{:_id ... :average ...} {:_id ... :average ...} ...], :ok 1.0}
```
This pipeline of operations selects expenses with type = 'airfare', passes just the department and amount fields thru, and groups by department with an average for each.

Based on [10gen's Java Driver example of aggregation](http://www.mongodb.org/display/DOCS/Using+The+Aggregation+Framework+with+The+Java+Driver).

The aggregate function accepts any number of pipeline operations.

#### authentication
```clojure
(m/authenticate conn "myusername" "my password")

=> true
```
#### advanced initialization using mongo-options
```clojure
(m/make-connection :mydb :host "127.0.0.1" (m/mongo-options :auto-connect-retry true))
```
The available options are hyphen-separated lowercase keyword versions of the camelCase options supported by the Java driver. Prior to CongoMongo 0.4.0, the options matched the fields in the *MongoOptions* class. As of CongoMongo 0.4.0, the options match the method names in the *MongoClientOptions* class instead (and an *IllegalArgumentException* will be thrown if you use an illegal option). The full list (with the 2.10.1 Java driver) is:
```clojure
(:auto-connect-retry :connect-timeout :connections-per-host :cursor-finalizer-enabled
 :db-decoder-factory :db-encoder-factory :description :legacy-defaults
 :max-auto-connect-retry-time :max-wait-time :read-preference :socket-factory
 :socket-keep-alive :socket-timeout :threads-allowed-to-block-for-connection-multiplier
 :write-concern)
```
#### initialization using a Mongo URI
```clojure
(m/make-connection "mongodb://user:pass@host:27071/databasename")
;; note that authentication is handled when given a user:pass@ section
```

A query string may also be specified containing the options supported by the *MongoClientURI* class (as of CongoMongo 0.4.0; previously the *MongoURI* class was used).
#### easy json
```clojure
(m/fetch-one :points
             :as :json)

=> "{ \"_id\" : \"0c23396ffe79e34a508cf400\" ,
      \"x\" : 0 , \"y\" : 0 , \"z\" : 0 }"
```

#### custom type conversions

For example, use Joda types for dates:

```clojure
(extend-protocol somnium.congomongo.coerce.ConvertibleFromMongo
  Date
  (mongo->clojure [^java.util.Date d keywordize] (new org.joda.time.DateTime d)))

(extend-protocol somnium.congomongo.coerce.ConvertibleToMongo
  org.joda.time.DateTime
  (clojure->mongo [^org.joda.time.DateTime dt] (.toDate dt)))
```

#### explain
Use :explain? on fetch to get performance information about a query. Returns a map of statistics about the query, not rows:

```clojure
(m/fetch :users :where {:login "alice"} :explain? true)
{:nscannedObjects 2281,
 :nYields 0,
 :nscanned 2281,
 :millis 2,
 :isMultiKey false,
 :cursor "BasicCursor",
 :n 1,
 :indexOnly false,
 :allPlans [{:cursor "BasicCursor", :indexBounds {}}],
 :nChunkSkips 0,
 :indexBounds {},
 :oldPlan {:cursor "BasicCursor", :indexBounds {}}}
```

Install
-------

Leiningen does not support dependency management on ClojureCLR.  To install I do the following

1) Clone/copy source files into your project
2) Set the environment variable `CLOJURE_LOAD_PATH` as follows

```clojure
(defn get-load-path []
  (set (string/split (Environment/GetEnvironmentVariable "CLOJURE_LOAD_PATH") #";")))

(defn set-load-path! [s]
  (let [
        new-path (apply str (interpose ";" s))
        ]
    (Environment/SetEnvironmentVariable "CLOJURE_LOAD_PATH" new-path)
    new-path))

(defn append-load-path!
  "appends file string to clojure load path"
  [new-path]
  (set-load-path! (conj (get-load-path) new-path)))
  ```

### License and copyright

Congomongo is made available under the terms of an MIT-style
license. Please refer to the source code for the full text of this
license and for copyright details.
