#_"This file is part of brevis.                                                                                                                                                 
                                                                                                                                                                                     
    brevis is free software: you can redistribute it and/or modify                                                                                                           
    it under the terms of the GNU General Public License as published by                                                                                                             
    the Free Software Foundation, either version 3 of the License, or                                                                                                                
    (at your option) any later version.                                                                                                                                              
                                                                                                                                                                                     
    brevis is distributed in the hope that it will be useful,                                                                                                                
    but WITHOUT ANY WARRANTY; without even the implied warranty of                                                                                                                   
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                                                                                                    
    GNU General Public License for more details.                                                                                                                                     
                                                                                                                                                                                     
    You should have received a copy of the GNU General Public License                                                                                                                
    along with brevis.  If not, see <http://www.gnu.org/licenses/>.                                                                                                          
                                                                                                                                                                                     
Copyright 2012, 2013 Kyle Harrington"

(ns brevis.example.parallel-swarm
  (:use [brevis.graphics.basic-3D]
        [brevis.physics collision core space utils]
        [brevis.shape box sphere cone]
        [brevis core osd vector utils]))

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
;;
;; Todo:
;; - auto-centering of camera (and skybox?)
;; - Voronoi neighborhoods (or some other acceleration)
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Globals

(def num-birds 6000)

(def avoidance-distance (atom 5))

(def speed 25)
(def max-acceleration 10)

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
              ;:shape (create-sphere)})
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
        #_(move bird (mul (get-position bird) -1))
        bird)
      (bound-acceleration (add (mul (get-acceleration bird) 0.5)
                               (mul new-acceleration speed))))))

(defn global-fly
  "Parallel fly over all birds."
  []
  (let [uids (all-object-uids)]
    #_(pmapall #(let [obj (get-object %)]
                (set-object %
                            (fly obj (get-dt) [])))
             uids)
    (doall (pmap #(let [obj (get-object %)]
                   #_(println %)
                   (set-object %
                               (fly obj (get-dt) [])))
                uids))
    ))

(enable-kinematics-update :bird); This tells the simulator to move our objects
;(add-update-handler :bird fly); This tells the simulator how to update these objects

(add-global-update-handler 0 global-fly)

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
  (set-neighborhood-radius 25)
  (default-display-text)
  (disable-collisions)
  #_(add-object (make-floor 500 500))
  (dotimes [_ num-birds]
    (add-object (random-bird))))

;; Start zee macheen
(defn -main [& args]
  (if-not (empty? args)
    (start-nogui initialize-simulation)
    (start-gui initialize-simulation)))

;; For autostart with Counterclockwise in Eclipse
(when (find-ns 'ccw.complete)
  (-main))
;(-main :nogui)