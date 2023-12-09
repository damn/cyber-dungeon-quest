(ns game.components.skills
  (:require [x.x :refer [defcomponent]]
            [data.val-max :refer [apply-val]]
            [gdl.audio :as audio]
            [gdl.graphics.image :as image]
            [gdl.graphics.color :as color]
            [gdl.graphics.shape-drawer :as shape-drawer]
            [gdl.graphics.world :as world]
            [gdl.vector :as v]
            [utils.core :refer [mapvals]]
            [game.properties :as properties]
            [game.components.faction :as faction]
            [game.ui.mouseover-entity :refer (saved-mouseover-entity get-mouseover-entity)]
            [game.utils.counter :as counter]
            [game.effect :as effect]
            [game.entity :as entity]
            [game.maps.cell-grid :as cell-grid]))

(defn- nearest-enemy-entity [entity*]
  (let [enemy-faction (faction/enemy (:faction entity*))]
    (-> entity*
        :position
        cell-grid/get-cell
        deref
        enemy-faction
        :entity)))

(defn- make-effect-params [entity]
  (merge {:source entity}
         (if (:is-player @entity)
           (let [target (or (saved-mouseover-entity)
                            (get-mouseover-entity))
                 target-position (or (and target (:position @target))
                                     (world/mouse-position))]
             {:target target
              :target-position target-position
              :direction (v/direction (:position @entity)
                                      target-position)})
           (let [target (nearest-enemy-entity @entity)]
             {:target target
              :direction (when target
                           (v/direction (:position @entity)
                                        (:position @target)))}))))


(defn- effect-params [entity*]
  (:effect-params (:skillmanager entity*)))

(defn- draw-skill-icon [context icon entity* [x y]]
  (let [[width height] (image/world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ width 2)
        y (+ y (:half-height (:body entity*)))
        action-counter (get-in entity* [:skillmanager :action-counter])
        center [x (+ y radius)]]
    (shape-drawer/filled-circle center radius (color/rgb 1 1 1 0.125))
    (shape-drawer/sector center
                         radius
                         0 ; start-angle
                         (* (counter/ratio action-counter) 360) ; degree
                         (color/rgb 1 1 1 0.5))
    (image/draw context icon [(- x radius) y])))

(def show-skill-icon-on-active true)

(defcomponent :skills skills
  (entity/render-info [_ context {:keys [position active-skill?] :as entity*}]
    (doseq [{:keys [id image effect]} (vals skills)
            :when (= id active-skill?)]
      (when show-skill-icon-on-active
        (draw-skill-icon context image entity* position))
      (effect/render-info effect (effect-params entity*)))))

(defn- update-cooldown [skill delta]
  (if (:cooling-down? skill)
    (update skill :cooling-down?
            #(let [counter (counter/tick % delta)]
               (if (counter/stopped? counter)
                 false
                 counter)))
    skill))

(defn- update-cooldowns [skills delta]
  (mapvals #(update-cooldown % delta)
           skills))

(defn has-skill? [entity* id]
  (contains? (:skills entity*) id))

(defn assoc-skill [skills id]
  {:pre [(not (contains? skills id))]}
  (assoc skills id (properties/get id)))

(defn- enough-mana? [entity* {:keys [cost] :as skill}]
  (or (nil? cost)
      (zero? cost)
      (<= cost ((:mana entity*) 0))))

(defn usable-state [entity* skill]
  (cond
   (:cooling-down? skill)
   :cooldown
   (not (enough-mana? entity* skill))
   :not-enough-mana
   (not (effect/valid-params? (:effect skill)
                              (effect-params entity*)))
   :invalid-params
   :else
   :usable))

(defmulti ai-should-use? (fn [[effect-type effect-value] entity*] effect-type))
(defmethod ai-should-use? :default [_ entity*]
  true)

(defmulti choose-skill :choose-skill-type)

(defmethod choose-skill :npc [entity*]
  (->> entity*
       :skills
       vals
       (sort-by #(or (:cost %) 0))
       reverse
       (filter #(and (= :usable (usable-state entity* %))
                     (ai-should-use? (:effect %) entity*)))
       first
       :id))

(defn- apply-speed-multipliers [entity* skill delta]
  (let [{:keys [cast-speed attack-speed]} (:modifiers entity*)
        modified-delta (if (:spell? skill)
                         (* delta (or cast-speed   1))
                         (* delta (or attack-speed 1)))]
    (max 0 (int modified-delta))))

(defn- start-skill [entity* skill]
  (let [entity* (if-not (or (nil? (:cost skill))
                            (zero? (:cost skill)))
                  (update entity* :mana apply-val #(- % (:cost skill)))
                  entity*)]
    (-> entity*
        (assoc :active-skill? (:id skill))
        (assoc-in [:skillmanager :action-counter] (counter/create (:action-time skill))))))

(defn- stop-skill [entity* skill]
  (-> entity*
      (dissoc :active-skill?)
      (update :skillmanager dissoc :action-counter)
      (assoc-in [:skillmanager :effect-params] nil)
      (assoc-in [:skills (:active-skill? entity*) :cooling-down?] (when (:cooldown skill)
                                                                    (counter/create (:cooldown skill))))))

(defn- check-stop! [entity delta]
  (let [id (:active-skill? @entity)
        skill (-> @entity :skills id)
        delta (apply-speed-multipliers @entity skill delta)
        effect-params (effect-params @entity)
        effect (:effect skill)]
    (if-not (effect/valid-params? effect effect-params)
      (swap! entity stop-skill skill)
      (do
       (swap! entity update-in [:skillmanager :action-counter] counter/tick delta)
       (when (counter/stopped? (get-in @entity [:skillmanager :action-counter]))
         (swap! entity stop-skill skill)
         (effect/do! effect effect-params))))))

(defn- check-start! [entity]
  (swap! entity assoc-in [:skillmanager :effect-params] (make-effect-params entity))
  (let [skill (when-let [id (choose-skill @entity)]
                (id (:skills @entity)))]
    (when skill
      (assert (= :usable (usable-state @entity skill)))
      (audio/play (str "sounds/" (if (:spell? skill) "shoot.wav" "slash.wav")))
      (swap! entity start-skill skill))))

(defcomponent :skillmanager _
  (entity/tick! [_ entity delta]
    (swap! entity update :skills update-cooldowns delta)
    (if (:active-skill? @entity)
      (check-stop! entity delta)
      (check-start! entity)))
  (entity/stun! [_ entity]
    (when-let [skill-id (:active-skill? @entity)]
      (swap! entity stop-skill (skill-id (:skills @entity))))))

(defcomponent :skills _
  (entity/create! [[k v] entity]
    (swap! entity (fn [e*] (-> e*
                               (assoc :skillmanager {})
                               (update k #(zipmap % (map properties/get %))))))))
