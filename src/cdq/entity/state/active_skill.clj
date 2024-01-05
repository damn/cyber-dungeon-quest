(ns cdq.entity.state.active-skill
  (:require [gdl.context :refer [draw-filled-circle draw-sector draw-image]]
            [cdq.context :refer [valid-params? effect-render-info stopped? finished-ratio ->counter]]
            [cdq.entity :as entity]
            [cdq.entity.state :as state]))

(defn- draw-skill-icon [c icon entity* [x y] action-counter-ratio]
  (let [[width height] (:world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ width 2)
        y (+ y (:half-height (:entity/body entity*)))
        center [x (+ y radius)]]
    (draw-filled-circle c center radius [1 1 1 0.125])
    (draw-sector c center radius
                 90 ; start-angle
                 (* action-counter-ratio 360) ; degree
                 [1 1 1 0.5])
    (draw-image c icon [(- x radius) y])))

(defrecord ActiveSkill [entity skill effect-context counter]
  state/PlayerState
  (player-enter [_]
    [[:tx/cursor :cursors/sandclock]])

  (pause-game? [_] false)
  (manual-tick [_ context])

  state/State
  (enter [_ ctx]
    ; TODO all only called here => start-skill-bla
    ; make all this context context.entity.skill extension ?
    [[:tx/sound (str "sounds/" (if (:spell? skill) "shoot.wav" "slash.wav"))]
     (-> @entity
         (entity/set-skill-to-cooldown ctx skill)
         ; should assert enough mana
         ; but should also assert usable-state = :usable
         ; but do not want to call again valid-params? (expensive)
         ; i know i do it before only @ player & creature idle so ok
         (entity/pay-skill-mana-cost skill))])

  (exit [_ _ctx])

  (tick [_ context]
    (let [effect (:skill/effect skill)
          effect-context (merge context effect-context)]
      (cond
       (not (valid-params? effect-context effect))
       [[:tx/event entity :action-done]]

       (stopped? context counter)
       [[:tx/effect effect-context effect]
        [:tx/event entity :action-done]])))

  (render-below [_ c entity*])
  (render-above [_ c entity*])
  (render-info [_ c {:keys [entity/position] :as entity*}]
    (let [{:keys [property/image skill/effect]} skill]
      (draw-skill-icon c image entity* position (finished-ratio c counter))
      (effect-render-info (merge c effect-context) effect))))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (let [{:keys [cast-speed attack-speed]} (:entity/modifiers entity*)
        modified-action-time (/ action-time
                                (or (if (:spell? skill) cast-speed attack-speed)
                                    1))]
    (max 0 modified-action-time)))

(defn ->CreateWithCounter [context entity [skill effect-context]]
  ; assert keys effect-context only with 'effect/'
  ; so we don't use an outdated 'context' in the State update
  ; when we call State protocol functions we call it with the current context
  (assert (every? #(= "effect" (namespace %)) (keys effect-context)))
  (->ActiveSkill entity
                 skill
                 effect-context
                 (->> skill
                      :skill/action-time
                      (apply-action-speed-modifier @entity skill)
                      (->counter context))))
