(ns game.context)

(defprotocol EntityComponentSystem
  (get-entity [_ id])
  (entity-exists? [_ entity])
  (create-entity! [_ components-map]
                  "Entities should not have :id component, will get added.
                  Calls entity/create system on the components-map
                  Then puts it into an atom and calls entity/create! system on all components.")
  (tick-active-entities [_ delta])
  (render-visible-entities [_])
  (destroy-to-be-removed-entities! [_]
                                   "Calls entity/destroy and entity/destroy! on all entities which are marked as ':destroyed?'"))

(defprotocol Context
  (show-msg-to-player! [_ message]))

(defprotocol MouseOverEntity
  (update-mouseover-entity [_]))
