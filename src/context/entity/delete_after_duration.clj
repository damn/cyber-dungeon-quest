(ns context.entity.delete-after-duration
  (:require [x.x :refer [defcomponent]]
            [context.entity :as entity]
            [cdq.context :refer [->counter stopped?]]))

(defcomponent :entity/delete-after-duration counter
  (entity/create! [[k duration] entity context]
    (swap! entity assoc k (->counter context duration)))
  (entity/tick! [_ entity ctx]
    (when (stopped? ctx counter)
      (swap! entity assoc :entity/destroyed? true))))
