(ns generation-p.core
  (:gen-class)
  (:require [clojure.data.generators :as data.gen]
            [generation-p.model :as m]
            [generation-p.social :as social]
            [generation-p.biology :as bio]))

;; Genetic Algorithm Pixel Art Bot
;; Solving an opimtization problem: trying to maximize number of impressions

;; resources
;; https://en.wikipedia.org/wiki/Genetic_algorithm
;; https://en.wikipedia.org/wiki/Selection_(genetic_algorithm)
;; https://en.wikipedia.org/wiki/Interactive_evolutionary_computation
;; https://en.wikipedia.org/wiki/Evolutionary_art

(defn -main
  "I don't do a whole lot ... yet."
  [& args]

  ;; TODO mutations -- evolution is stochastic
  ;; TODO meta mutations -- mutate mutation parameters: crossover parameters, crossover methods, etc
  ;; TODO think about generations
  ;; TODO fitness...
  ;; TODO start interfacing with social, database

  ;; if number of individuals in current generation is less than the desired
  ;; generation poulation
  ;;   if generation is 0
  ;;     generate random invididual
  ;;   else
  ;;     glean fitness of inidividuals in prev generation
  ;;     produce new individual by stochastically select two parents from the
  ;;     previous generation, weighted by fitness, and including mutation

  (let [latest-gen-num (m/latest-generation-num)
        latest-gen     (m/read-generation latest-gen-num)
        curr-gen-num   (if (< (count latest-gen) bio/desired-generation-population-count)
                         latest-gen-num
                         (inc latest-gen-num))
        prev-gen       (m/read-generation (dec curr-gen-num))]
    (if (= 0 curr-gen-num)
      (let [new-individual (bio/spawn-random-individual curr-gen-num)
            social-id      (social/debut new-individual)]
        (m/create-individual (assoc new-individual ::m/social-id social-id)))
      (let [prev-gen          (m/read-generation (dec curr-gen-num))
            [parent0 parent1] (bio/matchmake prev-gen)
            new-individual    (bio/breed parent0 parent1)]
        (= new-individual parent0))))

  )
