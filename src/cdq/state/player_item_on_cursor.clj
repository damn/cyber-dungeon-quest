(ns cdq.state.player-item-on-cursor
  (:require [gdl.context :refer [mouse-on-stage-actor? button-just-pressed? draw-centered-image
                                 world-mouse-position gui-mouse-position

                                 ; TODO return txs
                                 play-sound!
                                 ]]
            [gdl.input.buttons :as buttons]
            [gdl.math.vector :as v]
            [cdq.context :refer [item-entity]]

            ; TODO return txs
            [cdq.context :refer [show-msg-to-player! send-event! set-item! stack-item! remove-item!]]

            [cdq.entity :as entity]
            [cdq.entity.inventory :as inventory]
            [cdq.state :as state]
            [cdq.state.player-idle :refer [click-distance-tiles]]))

(def ^:private denied-2hw-shield
  [[:tx/sound "sounds/bfxr_denied.wav"]
   [:tx/msg-to-player "Two-handed weapon and shield is not possible."]])

(defn- clicked-cell [entity* cell]
  (let [inventory (:entity/inventory entity*)
        item (get-in inventory cell)
        item-on-cursor (:entity/item-on-cursor entity*)]
    (cond
     ; PUT ITEM IN EMPTY CELL
     (and (not item)
          (inventory/valid-slot? cell item-on-cursor))
     (if (inventory/two-handed-weapon-and-shield-together? inventory cell item-on-cursor)
       denied-2hw-shield
       [[:tx/sound "sounds/bfxr_itemput.wav"]
        [:tx/set-item entity* cell item-on-cursor]
        [:tx/dissoc entity* :entity/item-on-cursor]
        [:tx/event entity* :dropped-item]])

     ; STACK ITEMS
     (and item (inventory/stackable? item item-on-cursor))
     [[:tx/sound "sounds/bfxr_itemput.wav"]
      [:tx/stack-item entity* cell item-on-cursor]
      [:tx/dissoc entity* :entity/item-on-cursor]
      [:tx/event entity* :dropped-item]]

     ; SWAP ITEMS
     (and item
          (inventory/valid-slot? cell item-on-cursor))
     (if (inventory/two-handed-weapon-and-shield-together? inventory cell item-on-cursor)
       denied-2hw-shield
       [[:tx/sound "sounds/bfxr_itemput.wav"]
        [:tx/remove-item entity* cell]
        [:tx/set-item entity* cell item-on-cursor]
        ; need to dissoc and drop otherwise state enter does not trigger picking it up again
        ; TODO? coud handle pickup-item from item-on-cursor state also
        [:tx/dissoc entity* :entity/item-on-cursor]
        [:tx/event entity* :dropped-item]
        [:tx/event entity* :pickup-item item]]))))

; It is possible to put items out of sight, losing them.
; Because line of sight checks center of entity only, not corners
; this is okay, you have thrown the item over a hill, thats possible.


(defn- placement-point [player target maxrange]
  (v/add player
         (v/scale (v/direction player target)
                  (min maxrange
                       (v/distance player target)))))

(defn- item-place-position [ctx entity*]
  (placement-point (:entity/position entity*)
                   (world-mouse-position ctx)
                   ; so you cannot put it out of your own reach
                   (- cdq.state.player-idle/click-distance-tiles 0.1)))

(defn- world-item? [ctx]
  (not (mouse-on-stage-actor? ctx)))

(defrecord PlayerItemOnCursor [item]
  state/PlayerState
  (player-enter [_])
  (pause-game? [_] true)
  (manual-tick [_ entity* context]
    (when (and (button-just-pressed? context buttons/left)
               (world-item? context))
      [[:tx/event entity* :drop-item]]))
  (clicked-inventory-cell [_ entity* cell]
    (clicked-cell entity* cell))
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ entity* _ctx]
    [[:tx/cursor :cursors/hand-grab]
     (assoc entity* :entity/item-on-cursor item)])

  (exit [_ entity* ctx]
    ; at context.ui.inventory-window/clicked-cell when we put it into a inventory-cell
    ; we do not want to drop it on the ground too additonally,
    ; so we dissoc it there manually. Otherwise it creates another item
    ; on the ground
    (when (:entity/item-on-cursor entity*)
      [[:tx/sound "sounds/bfxr_itemputground.wav"]
       (item-entity ctx (item-place-position ctx entity*) (:entity/item-on-cursor entity*))
       [:tx/dissoc entity* :entity/item-on-cursor]]))

  (tick [_ entity* _ctx])
  (render-below [_ entity* ctx]
    (when (world-item? ctx)
      (draw-centered-image ctx (:property/image item) (item-place-position ctx entity*))))
  (render-above [_ entity* ctx])
  (render-info  [_ entity* ctc]))

(defn draw-item-on-cursor [{:keys [context/player-entity] :as context}]
  (when (and (= :item-on-cursor (entity/state @player-entity))
             (not (world-item? context)))
    (draw-centered-image context
                         (:property/image (:entity/item-on-cursor @player-entity))
                         (gui-mouse-position context))))
