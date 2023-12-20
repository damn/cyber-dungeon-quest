(ns game.effects.restore-hp-mana
  (:require [gdl.context :refer [play-sound!]]
            [data.val-max :refer [lower-than-max? set-to-max]]
            [game.effect :as effect]))

(defmethod effect/useful? :restore-hp-mana [_ _effect-params _context entity*]
  (or (lower-than-max? (:mana entity*))
      (lower-than-max? (:hp   entity*))))

; TODO make with 'target' then can use as hit-effect too !
(effect/component :restore-hp-mana
  {:text (fn [_context _effect-val _params]
           "Restores full hp and mana.")
   :valid-params? (fn [_context _effect {:keys [source]}]
                    source)
   :do! (fn [context _effect-val {:keys [source]}]
          (play-sound! context "sounds/bfxr_drugsuse.wav")
          (swap! source #(-> %
                             (update :hp set-to-max)
                             (update :mana set-to-max))))})
