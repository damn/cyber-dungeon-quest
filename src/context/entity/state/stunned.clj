(ns context.entity.state.stunned
  (:require [gdl.context :refer [draw-circle]]
            [data.counter :as counter]
            [game.context :refer [send-event!]]
            [context.entity.state :as state]))

(defrecord Stunned [entity counter]
  state/PlayerState
  (pause-game? [_] false)
  (manual-tick! [_ context delta])

  state/State
  (enter [_ _ctx])
  (exit  [_ _ctx])
  (tick [this delta]
    (update this :counter counter/tick delta))
  (tick! [_ context delta]
    (when (counter/stopped? counter)
      (send-event! context entity :effect-wears-off)))
  (render-below [_ c {:keys [position]}]
    (draw-circle c position 0.5 [1 1 1 0.6]))
  (render-above [_ c entity*])
  (render-info  [_ c entity*]))

(defn ->CreateWithCounter [entity duration]
  (->Stunned entity (counter/create duration)))
