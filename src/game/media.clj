(ns game.media
  (:require [x.x :refer [defmodule]]
            [gdl.lc :as lc]
            [gdl.graphics.image :as image]
            [gdl.graphics.animation :as animation]
            [gdl.graphics.freetype :as freetype])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.g2d.BitmapFont))

(declare font
         ^:private fx)

(defmodule _
  (lc/create [_ context]
    (.bindRoot #'font (freetype/generate (.internal Gdx/files "exocet/films.EXL_____.ttf") 16))
    (.bindRoot #'fx (image/spritesheet context "fx/uf_FX.png" 24 24)))
  (lc/dispose [_]
    (.dispose ^BitmapFont font)))

; TODO do projectiles why animation ? only 1 frame
(defn black-projectile [context]
  (animation/create [(image/get-sprite context fx [1 12])] :frame-duration 500))
