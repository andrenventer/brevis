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

;; This is for simulations that require space (such as physical or pseudo-physical worlds)
(ns brevis.physics.space
  (:gen-class)
  (:import (org.ode4j.ode OdeHelper DSapSpace OdeConstants DContactBuffer DGeom DFixedJoint DContactJoint))  (:import (org.ode4j.math DVector3))  (:import java.lang.Math)  
  (:use [cantor]
        [penumbra.opengl]
        [brevis.shape core box]
        [brevis.physics core collision utils])
  (:require [cantor.range]))

;; ## Real/Physical/Spatial

(defn make-real
  "Add Real attributes to an object map."
  [obj]
  (let [uid (gensym)
        obj (assoc obj        
			         :uid uid
			         :real true
			         :acceleration (or (:acceleration obj) (vec3 0 0 0))
               :density (or (:density obj) 1)
			         :shape (or (:shape obj) (create-box)))
        pos (or (:position obj) (vec3 0 0 0))
        mass (obj-to-mass obj)
        body (doto (OdeHelper/createBody (get-world))
               (.setMass mass)
               (.setData {:uid uid :type (:type obj)})                (.setPosition (.x pos) (.y pos) (.z pos)))                       geom (doto (obj-to-geom obj)
               (.setBody body)
               (.setOffsetWorldPosition (.x pos) (.y pos) (.z pos))
               #_(.enable))]
    (assoc obj
           :rotation (vec4 0 0 1 0.001);; Rotation is a quaternion
           :mass mass
           :body body
           :shininess 0
           :geom geom)))

(defn orient-object
  "Orient an object by changing its rotation such that its vertex points towards a target vector."
  [obj obj-vec target-vec]
  (if (or (zero? (length obj-vec)) 
                (zero? (length target-vec)))
    obj
    (let [dir (cross obj-vec target-vec)
          dir (div dir (length dir))
          vdot (dot obj-vec target-vec)
          vdot (max (min (/ vdot (* (length obj-vec) (length target-vec))) 1) -1)
          angle (degrees (Math/acos vdot))]
      ;(println obj-vec target-vec vdot dir angle)
      (assoc obj
             :rotation
             (if (zero? (length dir))
               (vec4 (.x obj-vec) (.y obj-vec) (.z obj-vec) 0.001)
               (vec4 (.x dir) (.y dir) (.z dir) angle))))))

#_(defn inside-boundary?
  "Returns true if an object is out of the boundary of the simulation."
  [obj]
  (cantor.range/inside? simulation-boundary (get-position obj)))

(defn sort-by-proximity
  "Return a list of objects sorted by proximity."
  [position objects]
  (sort-by #(length (sub position (get-position %)))
           objects))

(defn uids-to-objects
  "Return the list of objects that corresponds to some UIDs"
  [UIDs]
  UIDs)

;; Leverage ODE to do the work
(defn compute-neighborhood
  "Return a list of neighbor UIDs within the neighborhood radius"
  [obj objects]
;  (let [nbr-UIDs (into #{} (.getNeighborUIDs (:space @*physics*) (vec3-to-odevec (get-position obj))))]
  (let [aabb (.getAABB (:geom obj))
        center (.getCenter aabb)
        maxlen (apply max (list (.len0 aabb) (.len1 aabb) (.len2 aabb)))
        nbr-radius (+ maxlen @*neighborhood-radius*)
        ;create a geometry for the neighborhoood
        nbrhood (let [a (OdeHelper/createSphere (:space @*physics*) nbr-radius)] (.setPosition a center) a)                                            
        nbrs-atom (atom #{})
        nbr-callback (proxy [org.ode4j.ode.DGeom$DNearCallback] []
                       (call [#^java.lang.Object data #^DGeom o1 #^DGeom o2]
                         (let [b1 (.getBody o1)
                               b2 (.getBody o2)]
                           (when b1 (swap! nbrs-atom conj (.getData b1)))
                           (when b2 (swap! nbrs-atom conj (.getData b2))))))
        ]
    ;use hashspace collide with a single geom on the neighborhood-geom
    (OdeHelper/spaceCollide2 nbrhood (:space @*physics*) nil nbr-callback)
    (.remove (:space @*physics*) nbrhood)
    (doall (filter identity
                   (for [uid-map (into [] @nbrs-atom)]
                     (some #(when (= (:uid (nth objects %)) (:uid uid-map)) (nth objects %)) (range (count objects)))))))); this could return a null if the UID isn't found

(defn insert-into [s x]
  (let [[low high] (split-with #(< % x) s)]
    (concat low [x] high)))

(defn update-neighbors
  "Update all neighborhoods"
  [objs]  
  (let [candidates #{}
        positions (map #(let [p (get-position %)]                          
                          [(.x p) (.y p) (.z p)])
                       objs)]
    ;; First loop over dimensions, maybe there is an issue with tie-breaking like Jon had with <3D situations (i.e. planar)
    ;;   compute candidate neighbors
    (doall (for [dim-positions positions]
             (loop [rem (range (count dim-positions))
                    sorted []];; too lazy to actually sort
               (when-not (empty? rem)
                 (let [targ (first rem)
                       targ-val (nth dim-positions targ)]
                   (doseq [srtobj sorted]
                     (cond (> (Math/abs (- targ (nth dim-positions srtobj))) @*neighborhood-radius*)
                       (do (disj candidates [targ srtobj]) (disj candidates [srtobj targ]))
                       :else ;(<= (Math/abs (- targ srtobj)) @*neighborhood-radius*)
                       (conj candidates [targ srtobj])))
                   (recur (rest rem)
                          (conj sorted targ)))))))
    ;; Filter candidates and save the neighbors of each candidate
    (let [nbrhoods (loop [rem (into [] candidates)    
                          nbrhoods (zipmap (range (count objs))
                                           #{})]
                     (if (empty? rem) 
                       nbrhoods
                       (let [x (ffirst rem)
                             y (second (first rem))
                             dist (length (sub (nth positions x) (nth positions y)))]
                         (recur (rest rem)
                                (if (< dist @*neighborhood-radius*)
                                  (assoc nbrhoods
;                                         x (conj (nbrhoods x) y)
;                                         y (conj (nbrhoods y) x))
                                         x (conj (nbrhoods x) (:uid (nth objs y)))
                                         y (conj (nbrhoods y) (:uid (nth objs x))))
                                  nbrhoods)))))]
      ;; Assoc neighborhoods and go home
      #_(println "update-neighbors:" nbrhoods)
      (doall (for [k (range (count objs))]
               (assoc (nth objs k)
                      :neighbors (nbrhoods k)))))))
  
(defn move
  "Move an object to the specified position."
  [obj v]
  (when-not (:body obj)
    (println obj))
  (.setPosition (:body obj)
    (.x v) (.y v) (.z v))
  obj)

(defn update-object-kinematics
  "Update the kinematics of an object by applying acceleration and velocity for an infinitesimal amount of time."
  [obj dt]
  (let [newvel (add (get-velocity obj)
                    (mul (:acceleration obj) dt))
        m 0.05
        f (mul (:acceleration obj)
               m)]; f = ma    
    (.addForce (:body obj) (.x f) (.y f) (.z f))
    #_(.setLinearVel (:body obj) (.x newvel) (.y newvel) (.z newvel)); avoids conversion that set-velocity would do
    obj))

(defn make-floor
  "Make a floor object."
  [w h]
  (move (make-real {:color [0.8 0.8 0.8]
                    :shininess 80
                    :type :floor
                    :density 8050
                    :texture *checkers*
                    :shape (create-box w 0.1 h)})
        (vec3 0 -3 0)))

(defn init-world  "Return a map of ODE physics for 1 world."  []  (let [world (doto (OdeHelper/createWorld)     
                      (.setGravity 0 0 0)                                                                                   
                      #_(.setGravity 0 -9.81 0))        space (OdeHelper/createHashSpace)        contact-group (OdeHelper/createJointGroup)]
    (reset! *physics* {:world world                             :space space                       :contact-group contact-group
                       :time 0});      (let [[floor floor-joint] (make-floor 1000 1000)
    (println "Collision handlers:" (keys @*collision-handlers*))
    (println "Update handlers:" (keys @*update-handlers*))    
    #_(let [floor (make-floor 500 500)          
            environment {:objects [floor]
                         :joints nil}]
        (reset! *physics* (assoc @*physics*
                                 :environment environment))
        (add-object floor)        
        (:objects environment))))
(defn reset-world  "Reset the *physics* global."  []  (loop []    (when (pos? (.getNumGeoms (:space @*physics*)))      (.remove (:space *physics*) (.getGeom (:space @*physics*) 0))      (recur)))  (let [[floor floor-joint] (make-floor)
        environment {:objects [floor]
                     :joints [floor-joint]}]    (reset! *physics* (assoc @*physics*
                             :environment environment
                             :time 0)))) 

(defn increment-physics-time
  "Increment the physics time by dt"
  [dt]
  (reset! *physics* (assoc @*physics* 
                           :time (+ (:time @*physics*) dt))))



(defn update-objects
  "Update all objects in the simulation. Objects whose update returns nil                                                                                                
are removed from the simulation."
  [objects dt]  
  (let [updated-objects 
        (pmapall (fn [obj]                             
                   (let [f (get @*update-handlers* (:type obj))]
                     (if f
                       (f obj dt (remove #{obj} objects))
                       obj))) objects)
        singles (filter #(not (seq? %)) updated-objects);; These objects didn't produce children                                                                         
        multiples (apply concat (filter seq? updated-objects))];; These are parents and children
    (into [] (keep identity (concat singles multiples)))))

(defn update-world
  "Update the world."
  [[dt t] state]
  (when (and  state
              (not (:terminated? state)))
    (when (:contact-group @*physics*)
      (.empty (:contact-group @*physics*)))
    #_(println "Number of obj in space:" (.getNumGeoms (:space @*physics*)))
    (reset! *collisions* #{})
        
    (OdeHelper/spaceCollide (:space @*physics*) nil nearCallback)
    (.quickStep (:world @*physics*) (get-dt))
    (increment-physics-time (get-dt))
    
    ;(reset! *objects* (let [new-objs (handle-collisions (update-objects (vals @*objects*) (:dt state)) @*collision-handlers*)]                                      
    #_(println "\nTiming: obj, coll, nbrs:")
    #_(reset! *objects* (let [new-objs (update-neighbors 
                                       (handle-collisions 
                                         (update-objects (vals @*objects*) (get-dt))
                                         @*collision-handlers*))]
                        (zipmap (map :uid new-objs) new-objs)));; hacky and bad    
    
    ;; Update objects based upon their update method
    (reset! *objects* (let [in-objs (vals (merge @*objects* @*added-objects*))]
                        (reset! *added-objects* {})
                        (let [new-objs (update-objects in-objs (get-dt))
                              objs (zipmap (map :uid new-objs) new-objs)]
                          (apply (partial dissoc objs) @*deleted-objects*))))
    (reset! *deleted-objects* #{})
    ;; Update objects for collisions
    (when @collisions-enabled
	    (reset! *objects* (let [in-objs (vals (merge @*objects* @*added-objects*))]
	                        (reset! *added-objects* {})
	                        (let [new-objs (handle-collisions in-objs @*collision-handlers*)
                                objs (zipmap (map :uid new-objs) new-objs)]	                          
                           (apply (partial dissoc objs) @*deleted-objects*)))))
    (reset! *deleted-objects* #{})
    ;; Finally update neighborhoods
    (when @neighborhoods-enabled	    
	    (reset! *objects* (let [in-objs (vals (merge @*objects* @*added-objects*))]
	                        (reset! *added-objects* {})                        
	                        (let [new-objs (update-neighbors in-objs)]
	                          (zipmap (map :uid new-objs) new-objs)))))
    
    (assoc state
           :simulation-time (+ (:simulation-time state) (get-dt)))))
