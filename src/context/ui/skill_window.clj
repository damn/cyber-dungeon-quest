(ns context.ui.skill-window
  (:require [gdl.context :refer [->window ->image-button ->text-tooltip]]
            [gdl.scene2d.actor :refer [add-listener!]]
            [context.entity.state :as state]
            [cdq.context :refer [get-property add-skill! skill-text]]
            [cdq.entity :as entity]))

(defn- pressed-on-skill-in-menu [{:keys [context/player-entity]
                                  :as context}
                                 skill]
  (when (and (pos? (:free-skill-points @player-entity))
             (not (entity/has-skill? @player-entity skill)))
    (swap! player-entity update :free-skill-points dec)
    (add-skill! context player-entity skill)))

; TODO render text label free-skill-points
; (str "Free points: " (:free-skill-points @player-entity))
(defn create [context]
  (->window context
            {:title "Skills"
             :id :skill-window
             :rows [(for [id [:spells/projectile
                              :spells/meditation
                              :spells/spawn]
                          :let [skill (get-property context id)
                                button (->image-button context
                                                       (:image skill)
                                                       (fn [{:keys [context/player-entity] :as ctx}]
                                                         ; TODO DRY with inventory window clicked-cells
                                                         (when (state/allow-ui-clicks? (:state-obj (:entity/state @player-entity)))
                                                           (pressed-on-skill-in-menu ctx skill))))]]
                      ; duplicated @ action-bar => not skill-text but skill-button ... ? with different on-clicked
                      (do
                       (add-listener! button (->text-tooltip context #(skill-text % skill)))
                       button))]
             :pack? true}))
