(ns game.render-ingame
  (:require [gdl.draw :as draw]
            [gdl.graphics.color :as color]
            [gdl.graphics.camera :as camera]
            [game.line-of-sight :refer (in-line-of-sight?)]
            [game.maps.data :refer [get-current-map-data]]
            [game.maps.cell-grid :as cell-grid]
            [game.render :as render]
            [game.player.status-gui :refer [render-player-hp-mana]]
            [game.utils.msg-to-player :refer [render-message-to-player]]
            [game.update-ingame :refer (thrown-error)]
            [game.player.entity :refer (player-entity)]
            [game.maps.contentfields :refer [get-entities-in-active-content-fields]]
            [game.maps.potential-field :as potential-field])
  (:import com.badlogic.gdx.graphics.Color))

(defn- geom-test [drawer {:keys [world-mouse-position]}]
  (let [position world-mouse-position
        cell-grid (:cell-grid (get-current-map-data))
        radius 0.8
        circle {:position position :radius radius}]
    (draw/circle drawer position radius (color/rgb 1 0 0 0.5))
    (doseq [[x y] (map #(:position @%)
                       (cell-grid/circle->touched-cells cell-grid circle))]
      (draw/rectangle drawer x y 1 1 (color/rgb 1 0 0 0.5)))
    (let [{[x y] :left-bottom :keys [width height]} (gdl.math.geom/circle->outer-rectangle circle)]
      (draw/rectangle drawer x y width height (color/rgb 0 0 1 1)))))

(comment
 (count (filter #(:sleeping @%) (get-entities-in-active-content-fields)))
 )

(defn- visible-entities* [context]
  (->> (get-entities-in-active-content-fields)
       (map deref)
       (filter #(in-line-of-sight? @player-entity % context))))

(defn- tile-debug [drawer {:keys [world-camera world-viewport-width world-viewport-height]}]
  (let [cell-grid (:cell-grid (get-current-map-data))
        [left-x right-x bottom-y top-y] (camera/frustum world-camera)]
    (draw/grid drawer (int left-x)
                       (int bottom-y)
                       (inc (int world-viewport-width))
                       (+ 2 (int world-viewport-height))
                       1
                       1
                       (color/rgb 0.5 0.5 0.5 0.5))
    (doseq [[x y] (camera/visible-tiles world-camera)
            :let [cell (get cell-grid [x y])
                  faction :good
                  {:keys [distance entity]} (get-in @cell [faction])]
            :when distance]
      #_(draw/rectangle drawer (+ x 0.1) (+ y 0.1) 0.8 0.8
                        (if blocked?
                          Color/RED
                          Color/GREEN))
      (let [ratio (/ (int (/ distance 10)) 15)]
        (draw/filled-rectangle drawer x y 1 1
                               (color/rgb ratio (- 1 ratio) ratio 0.6)))
      #_(@#'g/draw-string x y (str distance) 1)
      #_(when (:monster @cell)
          (@#'g/draw-string x y (str (:id @(:monster @cell))) 1)))))

(defn render-map-content [drawer {:keys [world-mouse-position] :as context}]
  #_(tile-debug drawer context)

  (render/render-entities* drawer
                           context
                           (visible-entities* context))

  #_(geom-test drawer context)

  ; highlight current mouseover-tile
  #_(let [[x y] (mapv int world-mouse-position)
        cell-grid (:cell-grid (get-current-map-data))
        cell (get cell-grid [x y])]
    (draw/rectangle drawer x y 1 1 (color/rgb 0 1 0 0.5))
    #_(g/render-readable-text x y {:shift false}
                            [color/white
                             (str [x y])
                             color/gray
                             (pr-str (:potential-field @cell))])))

(defn render-gui [drawer context]
  (render-player-hp-mana drawer context)
  (render-message-to-player))
