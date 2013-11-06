(ns brevis.scratch.minimal  
  (:require [penumbra.app :as app]            
            [clojure.math.numeric-tower :as math]
            [penumbra.text :as text]
            [penumbra.data :as data]
            [penumbra.opengl.frame-buffer :as fb]
            [penumbra.opengl.effects :as glfx])
  (:use [brevis.graphics.basic-3D]
        [brevis.physics collision core space utils]
        [brevis.shape box sphere cone]
        [brevis osd vector globals input utils]
        [brevis.core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Swarm
;;
;; ![](img/brevis_example_swarm.png?raw=true)
;;
;; Swarm simulations are models of flocking behavior in collections of organisms.   
;;
;; These algorithms were first explored computationally in:
;;
;;   Reynolds, Craig W. "Flocks, herds and schools: A distributed behavioral model." ACM SIGGRAPH Computer Graphics. Vol. 21. No. 4. ACM, 1987.
;;
;; Todo:
;; - auto-centering of camera (and skybox?)
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Globals

(def num-birds 500)

(def avoidance-distance (atom 10))

(def speed 25)
(def max-acceleration 100)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Birds

(defn bird?
  "Is a thing a bird?"
  [thing]
  (= (get-type thing) "bird"))

(defn random-bird-position
  "Returns a random valid bird position."
  []
  (let [w num-birds
        h w]
    (vec3 (- (rand w) (/ w 2)) 
          (+ 59.5 (rand 10));; having this bigger than the neighbor radius will help with speed due to neighborhood computation
          (- (rand h) (/ h 2)))))

(defn make-bird
  "Make a new bird with the specified program. At the specified location."
  [position]  
  (move (make-real {:type :bird
                    :color (vec4 1 0 0 1)
                    :shape (create-cone 2.2 1.5)})
        position))
  
(defn random-bird
  "Make a new random bird."
  []
  (make-bird (random-bird-position)))    

(defn bound-acceleration
  "Keeps the acceleration within a reasonable range."
  [v]  
  (if (> (length v) max-acceleration)
    (mul (div v (length v)) max-acceleration)
    v))

(defn fly
  "Change the acceleration of a bird."
  [bird dt nbrs]
  (let [nbrs (filter bird? (get-neighbor-objects bird))      
        ;tmp (println (count nbrs))
        ;tmp (do (doseq [nbr nbrs] (print (get-position nbr))) (println)) 
        closest-bird (when-not (empty? nbrs)
                       (first nbrs)
                       #_(rand-nth nbrs))
        new-acceleration (if-not closest-bird
                           ;; No neighbor, move randomly
                           (elmul (vec3 (- (rand) 0.5) (- (rand) 0.5) (- (rand) 0.5))
                                  (mul (get-position bird) -1.0))
                           (let [dvec (sub (get-position bird) (get-position closest-bird)) 
                                 len (length dvec)]
                             (add (sub (get-velocity closest-bird) (get-velocity bird)); velocity matching
                                  (if (<= len @avoidance-distance)
                                    ;; If far from neighbor, get closer
                                    dvec
                                    ;; If too close to neighbor, move away
                                    (add (mul dvec -1.0)
                                         (vec3 (rand 0.1) (rand 0.1) (rand 0.1)))))));; add a small random delta so we don't get into a loop                                    
        new-acceleration (if (zero? (length new-acceleration))
                           new-acceleration
                           (mul new-acceleration (/ 1 (length new-acceleration))))]
    (set-acceleration
      (if (> (length (get-position bird)) 500)
        (move bird (vec3 0 25 0))
        bird)
      (add (mul (get-acceleration bird) 0.5)
           (mul new-acceleration speed)))))

(enable-kinematics-update :bird); This tells the simulator to move our objects
(add-update-handler :bird fly); This tells the simulator how to update these objects

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Collision handling
;;
;; Collision functions take [collider collidee] and return [collider collidee]
;;   Both can be modified; however, two independent collisions are actually computed [a b] and [b a].

(defn bump
  "Collision between two birds. This is called on [bird1 bird2] and [bird2 bird1] independently
so we only modify bird1."
  [bird1 bird2]  
  [(set-color bird1 (vec4 (rand) (rand) (rand) 1))
   bird2])

(defn land
  "Collision between a bird and the floor."
  [bird floor]
  [(set-velocity (set-acceleration bird (vec3 0 10.5 0)) (vec3 0 0 0));; maybe move as well       
   floor])

(add-collision-handler :bird :bird bump)
(add-collision-handler :bird :floor land)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## brevis control code

(defn initialize-simulation
  "This is the function where you add everything to the world."
  []  
  (init-world)
  (init-view)
  (set-dt 0.1)
  (set-neighborhood-radius 1000)
  (default-display-text)
  (add-object (make-floor 500 500))
  (dotimes [_ num-birds]
    (add-object (random-bird))))

;; Start zee macheen
(defn -main [& args]
  (start-gui initialize-simulation))

;; For autostart with Counterclockwise in Eclipse
(when (find-ns 'ccw.complete)
  (-main))
;(-main :nogui)