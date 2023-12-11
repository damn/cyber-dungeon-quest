(ns game.effects.restore-hp-mana
  (:require [data.val-max :refer [lower-than-max? set-to-max]]
            [game.effect :as effect]
            [game.components.skills :refer (ai-should-use?)])
  (:import com.badlogic.gdx.audio.Sound))

(defmethod ai-should-use? :restore-hp-mana [_ entity*]
  (or (lower-than-max? (:mana entity*))
      (lower-than-max? (:hp   entity*))))

; TODO make with 'target' then can use as hit-effect too !
(effect/defeffect :restore-hp-mana
  {:text (fn [_ _] "Restores full hp and mana.")
   :valid-params? (fn [_ {:keys [source]}]
                    source)
   :do! (fn [_ {:keys [source]} {:keys [assets]}]
          (.play ^Sound (get assets "sounds/bfxr_drugsuse.wav"))
          (swap! source #(-> %
                             (update :hp set-to-max)
                             (update :mana set-to-max))))})
