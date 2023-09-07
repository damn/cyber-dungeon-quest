(ns game.ui.mouseover-entity
  (:require [x.x :refer [defcomponent]]
            [gdl.input :as input]
            [gdl.graphics.color :as color]
            [gdl.graphics.shape-drawer :as shape-drawer]
            [gdl.graphics.world :as world]
            [gdl.graphics.gui :as gui]
            [gdl.scene2d.stage :as stage]
            [utils.core :refer [sort-by-order]]
            [game.db :as db]
            [game.render :as render]
            [game.session :as session]
            [game.line-of-sight :refer (in-line-of-sight?)]
            [game.maps.cell-grid :refer (get-bodies-at-position)]
            [game.player.entity :refer (player-entity)]))

(def show-entity-props-on-mouseover false)

; * clickable text
; * hp bar
; * sleeping aggro range
; * debug show id
; * show selection outline

#_(db/defctypefn :render-debug :mouseover? [entity* [x y]]
  (when show-entity-props-on-mouseover
    (g/render-readable-text x y
                            {:centerx true :centery true}
                            ^{:scale 1.5}
                            [(str "id " (:id entity*))])))

(def ^:private outline-alpha 0.4)
(color/defrgb ^:private enemy-color    1 0 0 outline-alpha)
(color/defrgb ^:private friendly-color 0 1 0 outline-alpha)
(color/defrgb ^:private neutral-color  1 1 1 outline-alpha)

(defcomponent :mouseover? _
  (render/below [_ {:keys [body faction]} [x y]]
    (shape-drawer/with-line-width 3
      (shape-drawer/ellipse [x y]
                            (:half-width body)
                            (:half-height body)
                            (case faction ; TODO enemy faction of player
                              :evil friendly-color
                              :good enemy-color
                              neutral-color)))))

(defn- get-current-mouseover-entity []
  (let [tile-posi (world/mouse-position)
        hits (get-bodies-at-position tile-posi)]
    ; TODO needs z-order ? what if 'shout' element or FX ?
    (when hits
      (->> render/render-on-map-order
           ; TODO re-use render-ingame code to-be-rendered-entities-on-map
           (sort-by-order hits #(:z-order @%))
           ; topmost body selected first, reverse of render-order
           reverse
           ; = same code @ which entities should get rendered...
           (filter #(in-line-of-sight? @player-entity @%))
           first))))

(def ^:private cache (atom nil))
(def ^:private is-saved (atom false))

(def state (reify session/State
             (load! [_ data]
               (reset! cache nil)
               (reset! is-saved false))
             (serialize [_])
             (initial-data [_])))

(defn get-mouseover-entity []
  @cache)

(defn saved-mouseover-entity
  "When the player keeps holding leftmouse down after having a mouseoverbody it is saved."
  []
  (and @is-saved @cache))

(defn- keep-saved? [entity]
  (and (input/is-leftbutton-down?)
       (db/exists? entity)
       (in-line-of-sight? @player-entity @entity)))

(defn- save? [entity]
  (and (input/is-leftm-pressed?) ; dont move & get stuck on any entity under mouse, only save when starting to click on that
       (input/is-leftbutton-down?)
       (not= entity player-entity))) ; movement follows this / targeting / ...

(defn update-mouseover-entity [stage]
  (when-not (and @is-saved
                 (keep-saved? @cache))
    (when-let [entity @cache]
      (swap! entity dissoc :mouseover?))
    (if-let [entity (when-not (stage/hit stage (gui/mouse-position))
                      (get-current-mouseover-entity))]
      (do
       (reset! cache entity)
       (reset! is-saved (save? entity))
       (swap! entity assoc :mouseover? true))
      (do
       (reset! cache nil)
       (reset! is-saved false)))))
