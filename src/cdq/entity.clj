(ns cdq.entity)

(defrecord Entity [])

(defprotocol HasReferenceToItself
  (reference [_]))

(defprotocol State
  (state [_]))

(defprotocol Skills
  (has-skill? [_ skill]))

(defprotocol EffectModifiers
  (effect-source-modifiers [_ effect-type])
  (effect-target-modifiers [_ effect-type]))

(defprotocol Faction
  (enemy-faction [_])
  (friendly-faction [_]))
