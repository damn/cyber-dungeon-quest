(ns cdq.context)

(defmulti transact! (fn [[k] _ctx] k))

(defprotocol TransactionHandler
  (transact-all! [_ txs]))

(defprotocol EntityComponentSystem
  (create-entity! [_ components-map]
                  "Entities should not have :entity/id component, will get added.
                  Calls create/create! on components.
                  Returns the entity.")
  (get-entity [_ id])
  (tick-entity! [_ entity] "Calls the 'tick!' system on all components of the entity.")
  (render-entities* [_ entities*] "Draws entities* in the correct z-order and in the order of render-systems for each z-order.")
  (remove-destroyed-entities! [_] "Calls destroy! on all entities which are marked as ':entity/destroyed?'"))

(defprotocol PlayerMessage
  (show-msg-to-player! [_ message])
  (->player-message-actor [_]))

(defprotocol PlayerModal
  (show-player-modal! [_ {:keys [title text button-text on-click]}]))

(defprotocol MouseOverEntity
  (update-mouseover-entity! [_]))

(defprotocol World
  (line-of-sight? [_ source* target*])
  (ray-blocked?  [_ start target])
  (path-blocked? [_ start target path-w] "path-w in tiles. casts two rays.")
  ; TODO explored-grid
  (explored?     [_ position])
  (set-explored! [_ position])
  (content-grid [_])
  (world-grid [_]))

(defprotocol EntityWorld
  (add-entity! [_ entity])
  (remove-entity! [_ entity]))

(defprotocol EffectInterpreter
  (effect-text        [_ effect])
  (valid-params?      [_ effect])
  (effect-render-info [_ effect])
  (effect-useful?     [_ effect]))

(defprotocol Modifier
  (apply-modifier!   [_ entity modifier])
  (reverse-modifier! [_ entity modifier])
  (modifier-text     [_ modifier]))

(defprotocol Builder
  (creature [_ creature-id position extra-components])
  (audiovisual [_ position property-id])
  (item-entity [_ position item])
  (line-entity [_ {:keys [start end duration color thick?]}]))

; TODO get from world?
(defprotocol PotentialField
  (update-potential-fields! [_ entities])
  (potential-field-follow-to-enemy [_ entity]))

(defprotocol FiniteStateMachine
  (send-event! [_ entity event]
               [_ entity event params]))

(defprotocol PropertyStore
  (get-property [_ id])
  (all-properties [_ type]))

(defprotocol InventoryWindow
  (inventory-window [_])
  (rebuild-inventory-widgets [_])
  (set-item-image-in-widget [_ cell item])
  (remove-item-from-widget [_ cell]))

(defprotocol Inventory
  (set-item!        [_ entity cell item])
  (remove-item!     [_ entity cell])
  (stack-item!      [_ entity cell item])
  (try-pickup-item! [_ entity item]))

(defprotocol Counter
  (->counter [_ duration])
  (stopped?       [_ counter])
  (reset          [_ counter])
  (finished-ratio [_ counter])
  (update-elapsed-game-time! [_]))

(defprotocol Skills
  (add-skill!             [_ entity skill])
  (remove-skill!          [_ entity skill])
  (set-skill-to-cooldown! [_ entity skill])
  (pay-skill-mana-cost!   [_ entity skill])
  (skill-usable-state [effect-context entity* skill]))

(defprotocol Actionbar
  (->action-bar    [_])
  (reset-actionbar [_])
  (selected-skill  [_])
  (actionbar-add-skill    [_ skill])
  (actionbar-remove-skill [_ skill]))

(defprotocol DebugRender
  (debug-render-before-entities [_])
  (debug-render-after-entities  [_]))

(defprotocol Cursor
  (set-cursor! [_ cursor-key]))

(defprotocol TooltipText
  (tooltip-text [_ property]))

(defprotocol ErrorModal
  (->error-window [_ throwable]))
