(ns context.world-map
  (:require [clojure.edn :as edn]
            [gdl.context :refer [all-properties]]
            gdl.disposable
            [gdl.graphics.camera :as camera]
            [gdl.maps.tiled :as tiled]
            [gdl.math.geom :as geom]
            [gdl.math.raycaster :as raycaster]
            [gdl.math.vector :as v]
            [data.grid2d :as grid2d]
            [utils.core :refer [->tile tile->middle]]
            [context.world.grid :refer [create-grid]]
            [game.context :refer [creature-entity ray-blocked? world-cell]]
            [game.world.grid :refer [circle->cells]]
            [game.world.cell :as cell :refer [cells->entities]]
            [mapgen.movement-property :refer (movement-property)]
            mapgen.module-gen))

; rename this to context.world (can have multiple world-maps later)
; rename grid just to grid
; get-grid => world-grid
; world-cell => world-cell
; and protocols also rename folders

; TODO forgot to filter nil cells , e.g. cached-adjcent cells or something

;;

;; just grep context/world-map and move into API

;;;

; TODO world-map context , all access to that data game.context fn
; check also other contexts keep private

; ONLY HERE context/world-map !!!
; if multiple maps in 'world => code doesnt change outside protocols

; maybe call context/world ?? world protocol

; ! entities creation fns and audiovisual also game.context protocols !
; idk how to call it

; Contentfield Entities
; -> :position sollten sie haben
; Ansonsten updates? / renders? ansonsten ist sinnlos sie dazuzuf�gen.
; TODO entities dont save a contenfield in their :position component but just the idx (for printing..), also simpler here?
(let [field-w 16 ; TODO not world-viewport but player-viewport ? cannot link to world-viewport (minimap ...)
      field-h 16]

  (defn- create-mapcontentfields [w h]
    (grid2d/create-grid (inc (int (/ w field-w))) ; inc wegen r�ndern
                      (inc (int (/ h field-h)))
                      (fn [idx]
                        {:idx idx,
                         :entities (atom #{})}))) ; move atom out

  (defn- get-field-idx-of-position [[x y]]
    [(int (/ x field-w))
     (int (/ y field-h))]))

(defn- get-contentfields [{:keys [context/world-map]}]
  (:contentfields world-map))

(defn- get-content-field [entity]
  (:content-field entity))

(defn remove-entity-from-content-field [entity]
  (swap! (:entities (get-content-field @entity)) disj entity))

(defn put-entity-in-correct-content-field [context entity]
  (let [old-field (get-content-field @entity)
        new-field (get (get-contentfields context)
                       (get-field-idx-of-position (:position @entity)))]
    (when-not (= old-field new-field)
      (swap! (:entities new-field) conj entity)
      (swap! entity assoc :content-field new-field)
      (when old-field
        (swap! (:entities old-field) disj entity)))))

(defn- get-player-content-field-idx [{:keys [context/player-entity]}]
  (:idx (get-content-field @player-entity)))

(comment
 (defn get-all-entities-of-current-map [context]
   (mapcat #(deref (:entities %)) (grid2d/cells (get-contentfields context))))

 (count
  (get-all-entities-of-current-map @app.state/current-context))

 )

(defn- on-screen? [entity* {:keys [world-camera world-viewport-width world-viewport-height]}]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera/position world-camera)
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ world-viewport-width  2)))
     (<= ydist (inc (/ world-viewport-height 2))))))

(defn- create-double-ray-endpositions
  "path-w in tiles."
  [[start-x start-y] [target-x target-y] path-w]
  {:pre [(< path-w 0.98)]} ; wieso 0.98??
  (let [path-w (+ path-w 0.02) ;etwas gr�sser damit z.b. projektil nicht an ecken anst�sst
        v (v/direction [start-x start-y]
                       [target-y target-y])
        [normal1 normal2] (v/get-normal-vectors v)
        normal1 (v/scale normal1 (/ path-w 2))
        normal2 (v/scale normal2 (/ path-w 2))
        start1  (v/add [start-x  start-y]  normal1)
        start2  (v/add [start-x  start-y]  normal2)
        target1 (v/add [target-x target-y] normal1)
        target2 (v/add [target-x target-y] normal2)]
    [start1,target1,start2,target2]))

(extend-type gdl.context.Context
  game.context/World
  (get-entities-in-active-content-fields [context]
    (mapcat #(deref (:entities %)); (comp deref :entities) or #(... %) ?
            (remove nil?
                    (map (get-contentfields context)  ; keep (get-contentfields)  ?  also @ potential field thing
                         (let [idx (get-player-content-field-idx context)]
                           (cons idx (grid2d/get-8-neighbour-positions idx)))))))

  (entities-at-position [context position]
    (when-let [cell (world-cell context position)]
      (filter #(geom/point-in-rect? position (:body @%))
              (:entities @cell))))

  (line-of-sight? [context source* target*]
    (and (:z-order target*)  ; is even an entity which renders something
         (or (not (:is-player source*))
             (on-screen? target* context))
         (not (ray-blocked? context (:position source*) (:position target*)))))

  (circle->entities [{:keys [context/world-map]} circle]
    (->> (circle->cells (:grid world-map) circle)
         (map deref)
         cells->entities
         (filter #(geom/collides? circle (:body @%)))))

  (ray-blocked? [{:keys [context/world-map]} start target]
    (let [{:keys [cell-blocked-boolean-array width height]} world-map]
      (raycaster/ray-blocked? cell-blocked-boolean-array width height start target)))

  (path-blocked? [context start target path-w]
    (let [[start1,target1,start2,target2] (create-double-ray-endpositions start target path-w)]
      (or
       (ray-blocked? context start1 target1)
       (ray-blocked? context start2 target2))))

  (explored? [{:keys [context/world-map] :as context} position]
    (get @(:explored-tile-corners world-map) position))

  (set-explored! [{:keys [context/world-map] :as context} position]
    (swap! (:explored-tile-corners world-map) assoc (->tile position) true))

  (world-grid [{:keys [context/world-map]}]
    (:grid world-map))

  (world-cell [{:keys [context/world-map]} position]
    (get (:grid world-map) (->tile position))))

(defn- first-level [context]
  (let [{:keys [tiled-map start-positions]} (mapgen.module-gen/generate
                                             ; TODO move to properties
                                             (assoc (edn/read-string (slurp "resources/maps/map.edn"))
                                                    :creature-properties (all-properties context :creature)))
        start-position (tile->middle
                        (rand-nth (filter #(= "all" (movement-property tiled-map %))
                                          start-positions)))]
    {:map-key :first-level
     :pretty-name "First Level"
     :tiled-map tiled-map
     :start-position start-position}))

(defn- create-grid-from-tiledmap [tiled-map]
  (create-grid (tiled/width  tiled-map)
                    (tiled/height tiled-map)
                    (fn [position]
                      (case (movement-property tiled-map position)
                        "none" :none
                        "air"  :air
                        "all"  :all))))

(defn- set-cell-blocked-boolean-array [arr cell*]
  (let [[x y] (:position cell*)]
    (aset arr
          x
          y
          (boolean (cell/blocked? cell* {:is-flying true})))))

(defn- create-cell-blocked-boolean-array [grid]
  (let [arr (make-array Boolean/TYPE
                        (grid2d/width grid)
                        (grid2d/height grid))]
    (doseq [cell (grid2d/cells grid)]
      (set-cell-blocked-boolean-array arr @cell))
    arr))

(defn- create-world-map [{:keys [map-key
                                 pretty-name
                                 tiled-map
                                 start-position] :as argsmap}]
  (let [grid (create-grid-from-tiledmap tiled-map)
        w (grid2d/width  grid)
        h (grid2d/height grid)]
    (merge ; TODO no merge, list explicit which keys are there
     (dissoc argsmap :map-key)
     ; TODO here also namespaced keys  !?
     {:width w
      :height h
      :cell-blocked-boolean-array (create-cell-blocked-boolean-array grid)
      :contentfields (create-mapcontentfields w h)
      :grid grid
      :explored-tile-corners (atom (grid2d/create-grid w h (constantly false)))})
    )
  ;(check-not-allowed-diagonals grid)
  )

; --> mach prozedural generierte maps mit prostprocessing (fill-singles/set-cells-behind-walls-nil/remove-nads/..?)
;& assertions 0 NADS z.b. ...?

; looping through all tiles of the map 3 times. but dont do it in 1 loop because player needs to be initialized before all monsters!
(defn- place-entities! [context tiled-map]
  (doseq [[posi creature-id] (tiled/positions-with-property tiled-map :creatures :id)]
    (creature-entity context
                     creature-id
                     (tile->middle posi)
                     {:initial-state :sleeping}))
  ; otherwise will be rendered, is visible, can also just setVisible layer false
  (tiled/remove-layer! tiled-map :creatures))

(defn- create-entities-from-tiledmap! [{:keys [context/world-map] :as context}]
  ; TODO they need player entity context ?!
  (place-entities! context (:tiled-map world-map))
  (creature-entity context
                   :vampire
                   (:start-position world-map)
                   {:is-player true}))

(deftype Disposable-State [] ; TODO let world-map record implement this so tiledmaps get disposed
  gdl.disposable/Disposable
  (dispose [_]
    ; TODO dispose tiledmap of context/world-map => make disposable record
    ; TODO dispose maps when starting a new session
    ; => newly loaded tiledmaps
    #_(when (bound? #'world-maps)
      (doseq [[mapkey mapdata] world-maps
              :let [tiled-map (:tiled-map mapdata)]
              :when tiled-map]
        (tiled/dispose tiled-map)))))

(defn merge->context [context]
  (let [context (merge context
                       {:context/world-map (create-world-map (first-level context))})]
    (merge context
           {:context/player-entity (create-entities-from-tiledmap! context)})))
