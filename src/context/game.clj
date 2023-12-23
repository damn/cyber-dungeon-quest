(ns context.game
  (:require context.counter
            context.entity
            context.mouseover-entity
            [context.ui.player-message :as player-message]
            [context.world :as world]
            [cdq.context :refer [rebuild-inventory-widgets reset-actionbar]]))

(defn start-game-context [context]
  (rebuild-inventory-widgets context) ; before adding entities ( player gets items )
  (reset-actionbar context) ; empties skills -> before adding player
  (let [context (merge context
                       (context.entity/->context :z-orders [:on-ground ; items
                                                            :ground    ; creatures, player
                                                            :flying    ; flying creatures
                                                            :effect])  ; projectiles, nova
                       (context.mouseover-entity/->context)
                       (player-message/->context)
                       (context.counter/->context)
                       {:context/game-paused? (atom true)})]
    (world/merge->context context)))
