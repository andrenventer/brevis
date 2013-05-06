(ns brevis.example.swarm
  (:require [clojure.zip :as zip])
  (:use [brevis.graphics.basic-3D]
        [brevis.physics collision core space utils]
        [brevis.shape box sphere cone]
        [brevis.core]
        [cantor]))  

;; ## Swarm
;;
;; Swarm simulations are models of flocking behavior in collections of organisms.   
;;
;; These algorithms were first explored computationally in:
;;
;;   Reynolds, Craig W. "Flocks, herds and schools: A distributed behavioral model." ACM SIGGRAPH Computer Graphics. Vol. 21. No. 4. ACM, 1987.
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Globals

(def num-birds 50)

(def memory (atom 0.0))
(def avoidance (atom 0.8))
(def clustering (atom 0.05))
(def centering (atom 0.01))

(def max-acceleration 10)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Birds

(defn bird?
  "Is a thing a bird?"
  [thing]
  (= (:type thing) :bird))

(defn random-bird-position
  "Returns a random valid bird position."
  []
  (vec3 (- (rand 100) 50) 
        (+ 9.5 (rand 10));; having this bigger than the neighbor radius will help with speed due to neighborhood computation
        (- (rand 100) 50)))

(defn make-bird
  "Make a new bird with the specified program. At the specified location."
  [position]  
  (move (make-real {:type :bird
              :color [1 0 0]
              :shape (create-sphere)})
              ;:shape (create-cone)})
        position))
  
(defn random-bird
  "Make a new random bird."
  []
  (make-bird (random-bird-position)))

(defn bound-acceleration
  "Keeps the acceleration within a reasonable range."
  [v]
  v
  #_(if (> (length v) max-acceleration)
    (mul (div v (length v)) max-acceleration)
    v))

(defn fly
  "Change the acceleration of a bird."
  [bird dt nbrs]
  #_(println "fly: bird=" (:uid bird) " nbrs=" nbrs " " (count nbrs))
  (let [closest-bird (if (zero? (count nbrs))
                       bird
                       (first nbrs))
        centroid (if (zero? (count nbrs))
                   (get-position bird)
                   (div (reduce add (map get-position nbrs)) 
                        (count nbrs)))
        d-closest-bird (sub (get-position closest-bird) (get-position bird))
        d-centroid (sub centroid (get-position bird))
        d-center (sub (vec3 0 10 0) (get-position bird))
        new-acceleration (bound-acceleration
                           (add (mul (:acceleration bird) @memory)
                                (mul d-center @centering)
                                (mul d-closest-bird @avoidance)
                                (mul d-centroid @clustering)))]
    #_(println (:uid bird) new-acceleration)
    (assoc bird
           :acceleration new-acceleration)))

(defn update-bird
  "Update a bird based upon its flocking behavior and the physical kinematics."
  [bird dt objects]  
  (let [objects (filter bird? objects)
;        nbrs (sort-by-proximity (get-position bird) objects)
        nbrs (compute-neighborhood bird objects)  ]      
;        nbrs (map (fn [bird-uid]
;                    (some #(= (:uid %) bird-uid) objects))
;                  (:neighbors bird))]
;        floor (some #(when (= (:type %) :floor) %) objects)]
    #_(println nbrs)
    #_(doseq [el (:vertices (:shape bird))]
      (println el))
    #_(println " ")
    #_(println "update-bird: bird=" (:uid bird) " nbrs=" nbrs " " (count nbrs))
    (update-object-kinematics 
      (fly bird dt nbrs) dt)))

(add-update-handler :bird update-bird); This tells the simulator how to update these objects

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Collision handling
;;
;; Collision functions take [collider collidee] and return [collider collidee]
;;   Both can be modified; however, two independent collisions are actually computed [a b] and [b a].

(defn bump
  "Collision between two birds. This is called on [bird1 bird2] and [bird2 bird1] independently
so we only modify bird1."
  [bird1 bird2]
  [(assoc bird1 :color [(rand) (rand) (rand)])
   bird2])

(defn land
  "Collision between a bird and the floor."
  [bird floor]
  (when (or (nil? bird) (nil? floor))
    (println "Bird" bird) (println "Floor" floor))
  [(move (set-velocity (assoc bird
                        :acceleration (vec3 0 0 0))
                       (vec3 0 0 0))
         (vec3 0 0 0))
   floor])

(add-collision-handler :bird :bird bump)
(add-collision-handler :bird :floor land)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## brevis control code

(defn initialize-simulation
  "This is the function where you add everything to the world."
  []  
  (init-world)
  (set-dt 0.1)
  (add-object (make-floor 500 500))
  (dotimes [_ num-birds]
    (add-object (random-bird))))

;; Start zee macheen
(defn -main [& args]
  (start-gui initialize-simulation))

(-main)