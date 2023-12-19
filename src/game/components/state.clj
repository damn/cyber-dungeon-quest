(ns game.components.state
  (:require [reduce-fsm :as fsm]
            [x.x :refer [defcomponent]]
            [context.ecs :as entity]))

(defprotocol State
  (enter [_ context])
  (exit  [_ context])
  (tick [_ delta])
  (tick! [_ context delta])
  (render-below [_ c entity*])
  (render-above [_ c entity*])
  (render-info  [_ c entity*]))

(defprotocol PlayerState
  (pause-game? [_])
  (manual-tick! [_ context delta]))

(defcomponent :components/state {:keys [initial-state
                                        fsm
                                        state-obj
                                        state-obj-constructors]}
  (entity/create! [_ entity _ctx]
    (swap! entity assoc :components/state
           {:fsm (fsm initial-state nil) ; throws when initial-state is not part of states
            :state-obj ((initial-state state-obj-constructors) entity)
            :state-obj-constructors state-obj-constructors}))
  (entity/tick [[_ v] delta]
    (update v :state-obj tick delta))
  (entity/tick! [_ context _entity delta]
    (tick! state-obj context delta))
  (entity/render-below [_ c entity*] (render-below state-obj c entity*))
  (entity/render-above [_ c entity*] (render-above state-obj c entity*))
  (entity/render-info  [_ c entity*] (render-info  state-obj c entity*)))

(defn send-event! [context entity event & args]
  (let [{:keys [fsm
                state-obj
                state-obj-constructors]} (:components/state @entity)
        old-state (:state fsm)
        new-fsm (fsm/fsm-event fsm event)
        new-state (:state new-fsm)]
    (when (not= old-state new-state)
      (exit state-obj context)
      (let [new-state-obj (apply (new-state state-obj-constructors) (cons entity args))]
        (enter new-state-obj context)
        (swap! entity update :components/state #(assoc %
                                                       :fsm new-fsm
                                                       :state-obj new-state-obj))))))
