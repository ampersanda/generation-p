(ns generation-p.core
  (:gen-class)
  (:require [clojure.data.generators :as data.gen])
  (:import
   (java.io File)
   (java.awt.image AffineTransformOp BufferedImage WritableRaster)
   (java.awt.geom AffineTransform)
   (javax.imageio ImageIO)))

(def ^:const display-scale-factor 10.0)
(def ^:const img-width 50)
(def ^:const img-height 50)
(def ^:const num-channels 3)

;; https://lospec.com/palette-list/japanese-woodblock
(def ^:const palette--japanesewoodblock
  [[0x2b 0x28 0x21]
   [0x62 0x4c 0x3c]
   [0xd9 0xac 0x8b]
   [0xe3 0xcf 0xb4]
   [0x24 0x3d 0x5c]
   [0x5d 0x72 0x75]
   [0x5c 0x8b 0x93]
   [0xb1 0xa5 0x8d]
   [0xb0 0x3a 0x48]
   [0xd4 0x80 0x4d]
   [0xe0 0xc8 0x72]
   [0x3e 0x69 0x58]])

(def ^:const palette palette--japanesewoodblock)

(defn- vec->image
  ([pixel-vec]
    ;; assume square image
    (let [dim (-> pixel-vec
                  count
                  (/ 3)
                  Math/sqrt
                  int)]
      (def dim dim)
      (vec->image pixel-vec dim dim)))
  ([pixel-vec width height]
   ;; via https://stackoverflow.com/a/125013/1181141
    (let [image  (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
          raster (.getRaster image)]
      (.setPixels ^WritableRaster raster 0 0 width height (int-array pixel-vec))
      image)))

;; width and height are desired dimensions of resultant image -- the return val
;; of this function is a 1D vector of size (* height width num-channels)
(defn- random-vec [width height]
  (-> (* width height)
      (take (repeatedly #(apply data.gen/one-of palette)))
      flatten))

(defn- random-img [width height]
  (-> (random-vec width height)
      (vec->image width height)))

(defn- solid-gray-vec [width height val]
  (repeat (* width height num-channels) val))

(defn- black-vec [width height]
  (solid-gray-vec width height 0))

(defn- white-vec [width height]
  (solid-gray-vec width height 255))

(defn- save-img [img-name ^BufferedImage img]
  (ImageIO/write img "png" (File. (format "output/%s.png" img-name))))

;; https://stackoverflow.com/a/4216635
(defn- scale-up [^BufferedImage bi]
  (let [width    (* display-scale-factor (.getWidth bi))
        height   (* display-scale-factor (.getHeight bi))
        after    (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        xform    (AffineTransform.)
        _        (.scale xform display-scale-factor display-scale-factor)
        scale-op (AffineTransformOp. xform AffineTransformOp/TYPE_NEAREST_NEIGHBOR)]
    (.filter scale-op bi after)))

;; like k-point crossover, but instead of k points, (randomly) crossover after
;; runs of length n
(defn- crossover [n parent0 parent1]
  (let [parent0-partitioned (partition (* n num-channels) parent0)
        parent1-partitioned (partition (* n num-channels) parent1)]
    (flatten
     (for [i (range (count parent0-partitioned))]
       (if (data.gen/boolean)
         (nth parent0-partitioned i)
         (nth parent1-partitioned i))))))

;; get the coords of n patches that evenly cover a width x height 2D array,
;; taking num-channels into account
;; e.g.
;;
;; an image that is 2x2 will have a width of 6 (2 pixels * 3 color channels)
;;
;; (patches 1 6 2)
;;
;; (([0 0] [0 1] [0 2])  ;; first patch is nw pixel
;;  ([0 3] [0 4] [0 5])  ;; second patch is ne pixel
;;  ([1 0] [1 1] [1 2])  ;; third patch is sw pixel
;;  ([1 3] [1 4] [1 5])) ;; fourth patch is se pixel
(defn- patches [n width height]
  (let [partitioned-indices-i (partition n (range height))
        partitioned-indices-j (partition (* n num-channels) (range width))]
    (for [patch-i (range (count partitioned-indices-i))
          patch-j (range (count partitioned-indices-j))]
      (for [i (nth partitioned-indices-i patch-i)
            j (nth partitioned-indices-j patch-j)]
        [i j]))))

(def patches-memo (memoize patches))

(defn reshape [width flattened-arr]
  (vec (map vec (partition width flattened-arr))))

(defn patch-crossover [n width parent0 parent1]
  ;; loop over the image, patch by patch, taking a given patch from either
  ;; parent randomly
  ;; note: vector dimensions ideally are evenly divided by n
  ;; width param should already be multiplied by num-channels
  (let [parent0-2d (reshape width parent0)
        parent1-2d (reshape width parent1)]
    (flatten
     (loop [[patch & rest-patches] (patches-memo n
                                                 (count (first parent0-2d))
                                                 (count parent0-2d))
            img                    parent0-2d]
       (if patch
         (recur
          rest-patches
          (let [src-img (if (data.gen/boolean) parent0-2d parent1-2d)]
            (reduce
             (fn [res-img pixel]
               (->> (get-in src-img pixel)
                    (assoc-in res-img pixel)))
             img
             patch)))
         img)))))

(comment

  ;; TODO mutations -- evolution is stochastic
  ;; TODO meta mutations -- mutate mutation parameters, crossover parameters, crossover methods, etc
  ;; TODO think about generations
  ;; TODO fitness...
  ;; TODO start interfacing with social, database

  (def vec0 (random-vec img-width img-height))

  (def vec1 (random-vec img-width img-height))

  (def vecblack (black-vec img-width img-height))

  (def vecwhite (white-vec img-width img-height))

  (save-img "vec0" (scale-up (vec->image vec0)))

  (save-img "vec1" (scale-up (vec->image vec1)))

  (save-img "vecblack" (scale-up (vec->image vecblack)))

  (save-img "vecwhite" (scale-up (vec->image vecwhite)))

  (let [n 2
        progeny (patch-crossover n
                                 (* img-width num-channels)
                                 vecblack
                                 vecwhite)]
    (save-img (str "patched-" n) (scale-up (vec->image progeny))))

  (let [n 5
        progeny (patch-crossover n
                                 (* img-width num-channels)
                                 vec0
                                 vec1)]
    (save-img (str "progeny-patched-" n) (scale-up (vec->image progeny))))

  )

;; Genetic Algorithm Tweet Bot
;;
;; The population is initially seeded with N randomly generated tweets
;; An individual is represented as a 64x64 RGB image
;; Uniform crossover https://en.wikipedia.org/wiki/Crossover_(genetic_algorithm)#Uniform_crossover
;;   - would a k-point crossover produce more interesting output?
;; Fitness function is based on likes and retweets
;; TODO mutation?
;;   - capitalize
;;   - space/punctuate
;;   - simply change char
;;   - mutate crossover rule?
;;   - mutate hyper parameters?
;; Tweet twice per day
;; solving an opimtization problem: trying to maximize number of impressions
;;
;; Each generation, replace 50% of the population

;; Seed `n` individuals without reproducing
;; e.g. n = 4
;; gen 0:
;;   t0 abc 1
;;   t1 def 3
;;   t2 ghi 2
;;   t3 jkl 10

;; then selection https://en.wikipedia.org/wiki/Selection_(genetic_algorithm)
;; jkl 0.625  1       r == 0.5 <--
;; def 0.1875 0.375   r == 0.3 <--
;; ghi 0.125  0.1875
;; abc 0.0625 0.0625

;; then crossover + mutation
;; gen 1:
;;   t4 jkl + def = jei 1
;;            mut-> jeI
;;   (selection)
;;   t5 jkl + def = del 2
;;   (selection)
;;   t6 del + def = def ;; speciation heuristic?

;; fitness heurisitc
;; - if impressions alone form the basis of the fitness heuristic, then older
;;  tweets would be disproportionately fit due simply to having had more time to
;;  receive impressions.
;;  i think it should be something like fitness = a * impressions - b * age
;;  super old and/or unfit individuals would thus effictively be culled from the
;;  gene pool due to v low probability of being selected

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
