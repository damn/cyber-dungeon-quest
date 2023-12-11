(ns game.components.delete-after-animation-stopped?
  (:require [x.x :refer [defcomponent]]
            [gdl.graphics.animation :as animation]
            [game.entity :as entity]))

(defcomponent :delete-after-animation-stopped? _
  (entity/create! [_ e]
    (-> @e :animation :looping? not assert))
  (entity/tick! [_ _ctx e delta]
    (when (-> @e :animation animation/stopped?)
      (swap! e assoc :destroyed? true))))
