(ns game.effects.target-entity
  (:require [clojure.string :as str]
            [gdl.vector :as v]
            [gdl.audio :as audio]
            [gdl.graphics.color :as color]
            [gdl.graphics.shape-drawer :as shape-drawer]
            [game.media :as media]
            [game.line-of-sight :refer (in-line-of-sight?)]
            [game.components.skills :refer (ai-should-use?)]
            [game.entities.animation :as animation-entity]
            [game.entities.line :as line-entity]
            [game.components.skills :refer (effect-info-render)]
            [game.effect :as effect]
            game.effects.damage))

; TODO target still exists ?! necessary ? what if disappears/dead?
(defn- valid-params? [{:keys [source target]}]
  (and source
       target
       (in-line-of-sight? @source @target)
       (:hp @target))) ; TODO this is valid-params of hit-effect damage !!

(defn- in-range? [entity* target* maxrange] ; == circle-collides?
  (< (- (v/distance (:position entity*)
                    (:position target*))
        (:radius (:body entity*))
        (:radius (:body target*)))
     maxrange))

; TODO pass effect-params here.
(defmethod ai-should-use? :target-entity [[effect-id effect-value] entity]
  (in-range? @entity
             @(:target (:effect-params (:skillmanager @entity)))
             (:maxrange effect-value)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity* target*]
  (v/add (:position entity*)
         (v/scale (v/direction (:position entity*)
                               (:position target*))
                  (:radius (:body entity*)))))

(defn- end-point [entity* target* maxrange]
  (v/add (start-point entity* target*)
         (v/scale (v/direction (:position entity*)
                               (:position target*))
                  maxrange)))

(defn- hit-ground-effect [position]
  (audio/play "bfxr_fisthit.wav")
  (animation-entity/create!
   :position position
   :animation (game.media/fx-impact-animation [0 1])))

(defn- do-effect! [{:keys [source target value]}]
  (let [{:keys [hit-effects maxrange]} value]
    (if (in-range? @source @target maxrange)
      (do
       (line-entity/create! :start (start-point @source @target)
                            :end (:position @target)
                            :duration 50
                            :color (color/rgb 1 0 0 0.75)
                            :thick? true)
       (effect/do-effects! {:source source :target target} hit-effects))
      (do
       ; * clicking on far away monster
       ; * hitting ground in front of you ( there is another monster )
       ; * -> it doesn't get hit ! hmmm
       ; * either use 'MISS' or get enemy entities at end-point
       (hit-ground-effect (end-point @source @target maxrange))))))

(defmethod effect-info-render :target-entity [_ {:keys [source target value]}]
  (let [{:keys [maxrange]} value]
    (shape-drawer/line (start-point @source @target)
                       (end-point   @source @target maxrange)
                       (if (in-range? @source @target maxrange)
                         (color/rgb 1 0 0 0.5)
                         (color/rgb 1 1 0 0.5)))))

(effect/defeffect :target-entity
  {:text (fn [{:keys [value] :as params}]
           (str "Range " (:maxrange value) " meters\n"
                (str/join "\n"
                          (for [effect (:hit-effects value)]
                            (effect/text params effect)))))
   :valid-params? valid-params?
   :do! do-effect!})
