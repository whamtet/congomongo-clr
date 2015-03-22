(ns
  ^{:author "Jeff Sapp"}
  somnium.congomongo.error
  #_(:use [somnium.congomongo.config :only [*mongo-config*]])
  #_(:import [com.mongodb DB]))

;need to be ported - too lazy right now.  How about you do it and then submit a pull request?

#_(defn get-last-error
  "Gets the error (if there is one) from the previous operation"
  []
  (let [e (into {} (.getLastError ^DB (:db *mongo-config*)))]
    (when (e "err") e)))

#_(defn get-previous-error
  "Returns the last error that occurred"
  []
  (let [e (into {} (.getPreviousError ^DB (:db *mongo-config*)))]
    (when (e "err") e)))

#_(defn reset-error!
  "Resets the error memory for the database"
  []
  (into {} (.resetError ^DB (:db *mongo-config*))))

#_(defn force-error!
  "This method forces an error"
  []
  (into {} (.forceError ^DB (:db *mongo-config*))))
