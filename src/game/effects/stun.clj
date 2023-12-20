(ns game.effects.stun
  (:require [utils.core :refer [readable-number]]
            [game.context :refer [send-event!]]
            [context.effect-interpreter :as effect]))

(defmethod effect/text :effects/stun
  [_context [_ duration]]
  (str "Stuns for " (readable-number (/ duration 1000)) " seconds"))

; TODO target needs to have a state component so we can send events) (actually no then just nothing happens)
(defmethod effect/valid-params? :effects/stun
  [{:keys [effect/source effect/target]} _effect]
  (and target))

(defmethod effect/do! :effects/stun
  [{:keys [effect/target] :as context} [_ duration]]
  (send-event! context target :stun duration))
