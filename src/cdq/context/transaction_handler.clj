(ns cdq.context.transaction-handler
  (:require gdl.context
            [cdq.context :refer [transact! transact-all!]]))

(def ^:private log-txs? true)


(comment
 (count @txs-coll)

 (clojure.pprint/pprint
  (for [[txk txs] (group-by first @txs-coll)]
    [txk (count txs)]))

 )

(def txs-coll (atom []))

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  (instance? gdl.backends.libgdx.context.image_drawer_creator.Image %) "<Image>"
                  (instance? gdl.graphics.animation.ImmutableAnimation %) "<Animation>"
                  (instance? gdl.context.Context %) "<Context>"
                  :else %)
                tx)))

(extend-type gdl.context.Context
  cdq.context/TransactionHandler
  (transact-all! [ctx txs]
    (doseq [tx txs :when tx]
      (try (let [result (transact! tx ctx)]
             (if (nil? result)
               (do
                ;(when-not (= :tx/cursor (first tx)) (println (debug-print-tx tx)))
                (when log-txs? (swap! txs-coll conj tx)))
               (transact-all! ctx result)))
           (catch Throwable t
             (println "Error with transaction: \n" (debug-print-tx tx))
             (throw t))))))
