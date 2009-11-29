CongoMongo
===========

New Branch
------
Experimental features based on Clojure New branch and convenience macros.

New Fetch
---------
A few less keystrokes now

    (fetch :things
           where [:x     < 5
                         > 10
                  :y     in [1 2 3 4]
                  :z.bar #".*quux.*"]
           only  [:x :y]
           limit 20)
           
This macro calls the function '*fetch
with a map as its only argument, so it
is still available as a first-class
function if needed.

Insert! can now accept seqs so mass-insert is gone

    (insert! :things
             (for [x (range 1e6)] {:x x}))

Protcols are in use under the hood for coercions
and *should* enhance performance.
--------

CongoMongo is a work in progress. If you've used, improved, 
or abused it I'd love to hear about it. Contact me at somnium@gmx.us
