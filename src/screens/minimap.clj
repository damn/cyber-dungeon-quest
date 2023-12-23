(ns screens.minimap
  (:require [gdl.app :refer [current-context change-screen!]]
            gdl.screen
            [gdl.input.keys :as input.keys]
            [gdl.maps.tiled :as tiled]
            [gdl.graphics.color :as color]
            [gdl.graphics.camera :as camera]
            [gdl.context :refer [draw-filled-circle render-world-view key-just-pressed?]]
            [cdq.context :refer [explored?]])
  (:import com.badlogic.gdx.graphics.OrthographicCamera))

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(def ^:private zoom-setting (atom nil))

(defn- calculate-zoom [{:keys [^OrthographicCamera world-camera
                               context/world-map]}]
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @(:explored-tile-corners world-map))))
        viewport-width  (.viewportWidth  world-camera)
        viewport-height (.viewportHeight world-camera)
        [px py] (camera/position world-camera)
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)
        x-diff (max (- px (left 0)) (- (right 0) px))
        y-diff (max (- (top 1) py) (- py (bottom 1)))
        vp-ratio-w (/ (* x-diff 2) viewport-width)
        vp-ratio-h (/ (* y-diff 2) viewport-height)
        new-zoom (max vp-ratio-w vp-ratio-h)]
    new-zoom ))

; TODO FIXME deref'fing current-context at each tile corner
; massive performance issue - probably
; => pass context through java tilemap render class
; or prepare colors before
(defn- tile-corner-color-setter [color x y]
  (if (explored? @current-context [x y])
    color/white
    color/black))

(deftype Screen []
  gdl.screen/Screen
  (show [_ {:keys [world-camera] :as context}]
    (reset! zoom-setting (calculate-zoom context))
    (camera/set-zoom! world-camera @zoom-setting))

  (hide [_ _ctx])

  (render [_ {:keys [world-camera context/world-map] :as context}]
    (tiled/render-map context
                      (:tiled-map world-map)
                      tile-corner-color-setter)
    (render-world-view context
                       #(draw-filled-circle % (camera/position world-camera) 0.5 color/green))
    (when (or (key-just-pressed? context input.keys/tab)
              (key-just-pressed? context input.keys/escape))
      (camera/set-zoom! world-camera 1)
      (change-screen! :screens/game))))
