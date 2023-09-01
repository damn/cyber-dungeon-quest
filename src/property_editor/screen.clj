(ns property-editor.screen
  (:require [x.x :refer [defmodule]]
            [gdl.lc :as lc]
            [gdl.app :as app]
            [gdl.input :as input]
            [gdl.utils :refer [dispose]]
            [gdl.graphics.gui :as gui]
            [gdl.graphics.batch :refer [batch]]
            [gdl.scene2d.actor :as actor]
            [gdl.scene2d.stage :as stage]
            [gdl.scene2d.ui :as ui]
            [game.properties :as properties]))

(defn- stage      [] (:stage      (app/current-screen-value)))
(defn- split-pane [] (:split-pane (app/current-screen-value)))

(declare property-editor-window)

(defn- open-property-editor-window [id property-type]
  (let [window (property-editor-window id property-type)]
    (stage/add-actor (stage) window)
    (actor/set-center window
                      (/ (gui/viewport-width)  2)
                      (/ (gui/viewport-height) 2))))

(defmulti property-widget (fn [k v] k))

(defmethod property-widget :default [_ v]
  (ui/text-field (pr-str v)))

(defmethod property-widget :id [_ id] ; => not-editable => label. (also :slot)
  (ui/label (pr-str id)))

(defmethod property-widget :image [_ image]
  (ui/image (ui/texture-region-drawable (:texture image))))

(defmethod property-widget :species [_ id] ; == > link to another
  (ui/text-button (name id) #(open-property-editor-window id :species)))

(comment

 :items / :skills ; ==> link to multiple others
 ; also item needs to be in certain slot, each slot only once, etc. also max-items ...?
 (ui/table :rows (concat
                  (for [skill (:skills props)]
                    [(ui/image (ui/texture-region-drawable (:texture (:image (properties/get skill)))))
                     (ui/text-button " - " (fn [] (println "Remove " )))])
                  [[(ui/text-button " + " (fn [] (println "Add ")))]]))
 )

; => move to game.properties  ?
; can (properties/get-all property-type)  => (safe-get property-types property-type)
; => save info for overview -> how to get all creature/etc
; add :weapons (implemented or all slot=weapon ?)
(def ^:private property-types
  {:species {:title "Species"
             :property-keys [:id :hp :speed]
             :overview {:title "Species"
                        :fetch-key :hp}}
   :creature {:title "Creature"
              :property-keys [:id :image :species :level :skills :items]
              :overview {:title "Creatures"
                         :sort-by-fn #(vector (or (:level %) 9) (name (:species %)))
                         :extra-infos-widget #(ui/label (or (str (:level %) "-")))
                         :fetch-key :species}}
   :item {:title "Item"
          :property-keys [:id :image :slot :pretty-name :modifiers]
          :overview {:title "Items"
                     :sort-by-fn #(if-let [slot (:slot %)] (name slot) "")
                     :fetch-key :slot}}
   :skill {:title "Skill"
           :property-keys [:id :image :action-time :cooldown :cost :effect]
           :overview {:title "Skills"
                      :fetch-key :spell?}}})

; TODO check if property with id is of property-type ?
; => schema
; => validation before save/after load all props.
; visvalidateabletextfield
(defn- property-editor-window [id property-type]
  (let [{:keys [title property-keys]} (get property-types property-type)
        window (ui/window :title title
                          :modal? true
                          :cell-defaults {:pad 5})
        props (properties/get id)]
    (ui/add-rows window (concat (for [k property-keys]
                                  [(ui/label (name k)) (property-widget k (get props k))])
                                [[(ui/text-button "Save" (fn [] ))
                                  (ui/text-button "Cancel" #(actor/remove window))]]))
    (ui/pack window)
    window))

; TODO
; => non-toggle image-button (VisImageButton ?)
(defn- overview-table [property-type]
  (let [{:keys [title
                sort-by-fn
                extra-infos-widget
                fetch-key]} (:overview (get property-types property-type))
        entities (properties/all-with-key fetch-key)
        entities (if sort-by-fn
                   (sort-by sort-by-fn entities)
                   entities)
        number-columns 20]
    (ui/table :rows (concat [[{:actor (ui/label title) :colspan number-columns}]]
                            (for [entities (partition-all number-columns entities)]
                              (for [props entities
                                    :let [open-editor-fn #(open-property-editor-window
                                                           (:id props)
                                                           property-type)
                                          button (if (:image props)
                                                   (ui/image-button (:image props) open-editor-fn)
                                                   (ui/text-button (name (:id props)) open-editor-fn))
                                          top-widget (or (and extra-infos-widget
                                                              (extra-infos-widget props))
                                                         (ui/label ""))
                                          stack (ui/stack)]]
                                (do (actor/set-touchable top-widget :disabled)
                                    (.add stack button)
                                    (.add stack top-widget)
                                    stack)))))))

(defn- set-second-widget [widget]
  (.setSecondWidget ^com.kotcrab.vis.ui.widget.VisSplitPane (split-pane) widget))

(defn- left-widget []
  (ui/table :rows (concat
                   (for [[property-type {:keys [overview]}] property-types]
                     [(ui/text-button (:title overview) #(set-second-widget (overview-table property-type)))])
                   [[(ui/text-button "Back to Main Menu" #(app/set-screen :game.screens.main))]])))

(defmodule {:keys [stage]}
  (lc/create [_]
    (let [stage (stage/create gui/viewport batch)
          split-pane (ui/split-pane :first-widget (left-widget)
                                    :second-widget (ui/label "select property type left")
                                    :vertical? false
                                    :id :split-pane)
          table (ui/table :rows [[split-pane]]
                          :fill-parent? true)]
      (stage/add-actor stage table)
      {:stage stage
       :split-pane split-pane})) ; TODO only stage needed, can get split-pane through table
  (lc/dispose [_] (dispose stage))
  (lc/show [_] (input/set-processor stage))
  (lc/hide [_] (input/set-processor nil))
  (lc/render [_] (gui/render #(stage/draw stage batch)))
  (lc/tick [_ delta] (stage/act stage delta)))
