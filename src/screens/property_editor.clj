(ns screens.property-editor
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [gdl.app :as app :refer [change-screen!]]
            [gdl.context :refer [get-stage ->text-button ->image-button ->label ->text-field ->image-widget ->table ->stack ->window all-sound-files play-sound! ->vertical-group]]
            [gdl.scene2d.actor :as actor :refer [remove! set-touchable! parent add-listener! add-tooltip!]]
            [gdl.scene2d.group :refer [add-actor! clear-children! children]]
            [gdl.scene2d.ui.text-field :as text-field]
            [gdl.scene2d.ui.table :refer [add! add-rows cells]]
            [gdl.scene2d.ui.cell :refer [set-actor!]]
            [gdl.scene2d.ui.widget-group :refer [pack!]]
            context.modifier
            context.effect
            [context.properties :as properties :refer [property-types]]
            [cdq.context :refer [get-property all-properties]]))

(defn- ->horizontal-separator-cell [colspan]
  {:actor (com.kotcrab.vis.ui.widget.Separator. "default")
   :pad-top 2
   :pad-bottom 2
   :colspan colspan
   :fill-x? true
   :expand-x? true})

(defn- ->vertical-separator-cell []
  {:actor (com.kotcrab.vis.ui.widget.Separator. "vertical")
   :pad-top 2
   :pad-bottom 2
   :fill-y? true
   :expand-y? true})

; TODO use findByName & actor name as id with pr-str and ->edn for keywords
; userObject keep as is, can add both then !
; TODO rename 'add-rows!'
; TODO check syntax colors gdl.context names set special (gold)
; ADD SOUNDS TO SPELLS MANDATORY - move out of restoration - also hp / mana separate
; LET ME EDIT for example spawn which creature
; also :vampire, :lady-a , everything, game-speed, zoom-level,...
; => SCHEMA
; => let me ADD effect components like sound & SELECT sound ! file chooser
; do it properly right do it with your editor not text ....
; TODO refresh overview table after property-editor save something (callback ?)
; remove species, directly hp/speed ( no multiplier )

; used @ player-modal / error-window , can move to gdl.context
(defn- add-to-stage! [ctx actor]
  (-> ctx get-stage (add-actor! actor))
  (.setScrollFocus (get-stage ctx) actor) ; TODO not working
  (.setKeyboardFocus (get-stage ctx) actor)
  ; TODO set touch focus , have to click first to use scroll pad
  ; TODO use scrollpad ingame too
  )

;;

(defn- one-to-many-attribute->linked-property-type [k]
  (case k
    :creature/skills :property.type/skill
    :creature/items  :property.type/item))

; TODO label does not exist anymore.
; maybe no default widget & assert for all attributes are explicitly defined?
(def ^:private attribute->value-widget
  {:property/id :label
   :property/image :image
   :property/pretty-name :text-field
   :item/slot :label
   :item/modifier :nested-map
   :modifier/armor :text-field
   :modifier/max-mana :text-field
   :weapon/two-handed? :label
   :creature/level :text-field
   :creature/species :link-button
   :creature/skills :one-to-many
   :creature/items :one-to-many
   :creature/hp :text-field
   :creature/speed :text-field
   :spell? :label
   :skill/action-time :text-field
   :skill/cooldown :text-field
   :skill/cost :text-field
   :skill/effect :nested-map
   :effect/sound :sound
   :effect/damage :text-field
   :effect/target-entity :nested-map
   :maxrange :text-field
   :hit-effect :nested-map
   :map-size :text-field
   :max-area-level :text-field
   :spawn-rate :text-field})

(defn- removable? [k]
  (#{"effect" "modifier"} (namespace k)))

(defn- add-components? [k]
  (#{:item/modifier :skill/effect :hit-effect} k))

(defn- nested-map->components [k]
  (case k
    :item/modifier (keys context.modifier/modifier-definitions)
    :skill/effect (keys (methods context.effect/do!))
    :hit-effect   (keys (methods context.effect/do!)) ; TODO only those with 'source/target'
    ))

; TODO just reuse this order and make attributes->text fn
; all mixed together idk

(defn- sort-attributes [properties]
  (sort-by
   (fn [[k _v]]
     [(case k
        :property/id 0
        :property/image 1
        :property/pretty-name 2
        :spell? 3
        :creature/level 3
        :item/slot 3
        :weapon/two-handed? 4
        :creature/species 4
        9)
      (name k)])
   properties))

;;

(defmulti ->value-widget     (fn [_ctx [k _v]] (get attribute->value-widget k)))
(defmulti value-widget->data (fn [k _widget]   (get attribute->value-widget k)))

;;

(defn ->edn [v]
  (binding [*print-level* nil]
    (pr-str v)))

(defmethod ->value-widget :default [ctx [_ v]]
  (->label ctx (->edn v)))

(defmethod value-widget->data :default [_ widget]
  (actor/id widget))

;;

(defmethod ->value-widget :text-field [ctx [_ v]]
  (->text-field ctx (->edn v) {}))

(defmethod value-widget->data :text-field [_ widget]
  (edn/read-string (text-field/text widget)))

;;

(defn ->scroll-pane [_ actor]
  (let [widget (com.kotcrab.vis.ui.widget.VisScrollPane. actor)]
    (.setFlickScroll widget false)
    (.setFadeScrollBars widget false)
    widget))

(defn ->scrollable-choose-window [ctx rows]
  (let [window (->window ctx {:title "Choose"
                              :modal? true
                              :close-button? true
                              :center? true
                              :close-on-escape? true})
        table (->table ctx {:rows rows
                            :cell-defaults {:pad 1}})]
    (.width
     (.height (.add window (->scroll-pane ctx table))
              (float (- (:gui-viewport-height ctx) 50)))
     (float (+ 100 (/ (:gui-viewport-width ctx) 2))))
    (.pack window)
    window))

;;

; TODO too many ! too big ! scroll ... only show files first & preview?
; TODO make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (gdl.context/all-texture-files ctx))]
    [(->image-button ctx
                      (gdl.context/create-image ctx file)
                      (fn [_ctx]))]
    #_[(->text-button ctx
                    file
                    (fn [_ctx]))]))

(defmethod ->value-widget :image [ctx [_ image]]
  (->image-button ctx image
                  #(add-to-stage! % (->scrollable-choose-window % (texture-rows %)))
                  {:dimensions [96 96]}))

;;

(declare ->property-editor-window)

(defn open-property-editor-window! [context property-id]
  (add-to-stage! context (->property-editor-window context property-id)))

(defmethod ->value-widget :link-button [context [_ prop-id]]
  (->text-button context (name prop-id) #(open-property-editor-window! % prop-id)))

;;

(defn- window? [actor]
  (instance? com.badlogic.gdx.scenes.scene2d.ui.Window actor))

(defn- find-ancestor-window [actor]
  (if-let [p (parent actor)]
    (if (window? p) p (find-ancestor-window p))
    (throw (Error. (str "Actor has no parent window " actor)))))

(defn- pack-ancestor-window! [actor]
  (pack! (find-ancestor-window actor)))

(declare ->attribute-widget-table)

(defn- ->add-nested-map-button [ctx k attribute-widget-group]
  (->text-button ctx (str "Add " (name k))
   (fn [ctx]
     (let [window (->window ctx {:title "Choose"
                                 :modal? true
                                 :close-button? true
                                 :center? true
                                 :close-on-escape? true})]
       (add-rows window (for [nested-k (nested-map->components k)]
                          [(->text-button ctx (name nested-k)
                            (fn [ctx]
                              (remove! window)
                              (add-actor! attribute-widget-group
                                          (->attribute-widget-table ctx
                                                                    [nested-k nil] ; TODO default value ? important for target-entity otherwise no hit-effect & maxrange because cannot add ! ..............
                                                                    :horizontal-sep?
                                                                    (pos? (count (children attribute-widget-group)))))
                              (pack-ancestor-window! attribute-widget-group)))]))
       (pack! window)
       (add-to-stage! ctx window)))))

(declare ->attribute-widget-group)

(defmethod ->value-widget :nested-map [ctx [k props]]
  (let [attribute-widget-group (->attribute-widget-group ctx props)]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (->table ctx {:cell-defaults {:pad 5}
                  :rows (remove nil?
                                [(when (add-components? k)
                                   [(->add-nested-map-button ctx k attribute-widget-group)])
                                 (when (add-components? k)
                                   [(->horizontal-separator-cell 1)])
                                 [attribute-widget-group]])})))

(declare attribute-widget-group->data)

(defmethod value-widget->data :nested-map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

; FIXME
;Assert failed: Actor ids are not distinct: [:effect/damage :effect/damage :effect/stun]
;(or (empty? ids) (apply distinct? ids))
; => check before adding, always make into {}, no physical & magic damage mix ?!
; => damage consists of physical & magic part ?

;;

(defn- ->play-sound-button [ctx sound-file]
  (->text-button ctx ">>>" #(play-sound! % sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (all-sound-files ctx)]
               [(->text-button ctx (str/replace-first sound-file "sounds/" "")
                               (fn [{:keys [actor] :as ctx}]
                                 (clear-children! table)
                                 (add-rows table [(->sound-columns ctx table sound-file)])
                                 (remove! (find-ancestor-window actor))
                                 (pack-ancestor-window! table)
                                 (actor/set-id! table sound-file)))
                (->play-sound-button ctx sound-file)])]
    (add-to-stage! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [ctx table sound-file]
  [(->text-button ctx (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button ctx sound-file)])

(defmethod ->value-widget :sound [ctx [_ sound-file]]
  (let [table (->table ctx {:cell-defaults {:pad 5}})]
    (add-rows table [(if sound-file
                       (->sound-columns ctx table sound-file)
                       [(->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))

;;

(declare ->overview-table)

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (pack-ancestor-window! table))]
    (add-rows table
              [[(->text-button ctx "+"
                               (fn [ctx]
                                 (let [window (->window ctx {:title "Choose"
                                                             :modal? true
                                                             :close-button? true
                                                             :center? true
                                                             :close-on-escape? true})
                                       clicked-id-fn (fn [ctx id]
                                                       (remove! window)
                                                       (redo-rows ctx (conj (set property-ids) id)))]
                                   (add! window (->overview-table ctx property-type clicked-id-fn))
                                   (pack! window)
                                   (add-to-stage! ctx window))))]
               (for [prop-id property-ids]
                 (let [props (get-property ctx prop-id)
                       ; TODO also x2 dimensions
                       image-widget (->image-widget ctx ; TODO image-button (link)
                                                    (:property/image props)
                                                    {:id (:property/id props)})]
                   (add-tooltip! image-widget #((-> property-types
                                                    property-type
                                                    :overview
                                                    :tooltip-text-fn) % props))
                   image-widget))
               (for [prop-id property-ids]
                 (->text-button ctx "-"
                                #(redo-rows % (disj (set property-ids) prop-id))))])))

(defmethod ->value-widget :one-to-many [context [attribute property-ids]]
  (let [table (->table context {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (one-to-many-attribute->linked-property-type attribute)
                          property-ids)
    table))

; TODO use id of the value-widget itself and set/change it
(defmethod value-widget->data :one-to-many [_ widget]
  (->> (children widget) (keep actor/id) set))

;;

(defn ->attribute-widget-table [ctx [k v] & {:keys [horizontal-sep?]}]
  (let [label (->label ctx (name k))
        value-widget (->value-widget ctx [k v])
        table (->table ctx {:id k
                            :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (removable? k)
                          (->text-button ctx "-" (fn [_ctx]
                                                   (let [window (find-ancestor-window table)]
                                                     (remove! table)
                                                     (pack! window)))))
                        label
                        (->vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(->horizontal-separator-cell (count column))])
              column]]
    (actor/set-id! value-widget v)
    (add-rows table (remove nil? rows))
    table))

(defn- attribute-widget-table->value-widget [table]
  (-> table children last))

(defn- ->attribute-widget-tables [ctx props]
  (let [first-row? (atom true)]
    (for [[k v] (sort-attributes props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->attribute-widget-table ctx [k v] :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [ctx props]
  (let [group (->vertical-group ctx (->attribute-widget-tables ctx props))]
    ;(.left group)
    group
    ))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map actor/id (children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (value-widget->data k value-widget)])))

;;

(defn ->property-editor-window [context id]
  (let [props (get-property context id)
        {:keys [title]} (get property-types (context.properties/property-type props))
        window (->window context {:title (or title (name id))
                                  :modal? true
                                  :close-button? true
                                  :center? true
                                  :close-on-escape? true
                                  :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group context props)]
    (add-rows window [[widgets]
                      ; TODO SHOW IF CHANGES MADE then SAVE otherwise different color etc.
                      ; when closing (lose changes? yes no)
                      [(->text-button context "Save"
                                      (fn [_ctx]
                                        ; TODO error modal like map editor?
                                        ; TODO refresh overview creatures lvls,etc. ?
                                        (swap! app/current-context properties/update-and-write-to-file!
                                               (attribute-widget-group->data widgets))
                                        (remove! window)))]])
    (pack! window)
    window))

;;

(defn- ->overview-table
  "Creates a table with all-properties of property-type and buttons for each id
  which on-clicked calls clicked-id-fn."
  [ctx property-type clicked-id-fn]
  (let [{:keys [title
                sort-by-fn
                extra-info-text
                tooltip-text-fn
                columns
                image/dimensions]} (:overview (get property-types property-type))
        entities (all-properties ctx property-type)
        entities (if sort-by-fn
                   (sort-by sort-by-fn entities)
                   entities)
        number-columns columns]
    (->table ctx
             {:cell-defaults {:pad 2}
              :rows (concat [[{:actor (->label ctx title) :colspan number-columns}]]
                            (for [entities (partition-all number-columns entities)] ; TODO can just do 1 for?
                              (for [{:keys [property/id] :as props} entities
                                    :let [on-clicked #(clicked-id-fn % id)
                                          button (if (:property/image props)
                                                   (->image-button ctx (:property/image props) on-clicked
                                                                   {:dimensions dimensions})
                                                   (->text-button ctx (name id) on-clicked))
                                          top-widget (->label ctx (or (and extra-info-text
                                                                           (extra-info-text props))
                                                                      ""))
                                          stack (->stack ctx [button top-widget])]]
                                (do
                                 (when tooltip-text-fn
                                   (add-tooltip! button #(tooltip-text-fn % props)))
                                 (set-touchable! top-widget :disabled)
                                 stack))))})))

(defn- set-second-widget! [context widget]
  (let [table (:main-table (get-stage context))]
    (set-actor! (second (cells table)) widget)
    (pack! table)))

(defn- ->left-widget [context]
  (->table context {:cell-defaults {:pad 5}
                    :rows (concat
                           (for [[property-type {:keys [overview]}] property-types]
                             [(->text-button context
                                             (:title overview)
                                             #(set-second-widget! % (->overview-table % property-type open-property-editor-window!)))])
                           [[(->text-button context "Back to Main Menu" (fn [_context]
                                                                          (change-screen! :screens/main-menu)))]])}))

(defn screen [context background-image-fn]
  {:actors [(background-image-fn)
            (->table context {:id :main-table
                              :rows [[(->left-widget context) nil]]
                              :fill-parent? true})]})
