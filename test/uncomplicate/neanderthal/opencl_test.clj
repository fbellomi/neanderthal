(ns uncomplicate.neanderthal.opencl-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.neanderthal
             [protocols :as p]
             [opencl :refer :all]
             [core :refer :all]
             [real :refer :all]
             [math :refer [pow]]]
            [uncomplicate.clojurecl
             [core :refer [with-default with-release *command-queue*]]])
  (:import [uncomplicate.neanderthal.protocols
            RealVector RealMatrix]))

(def cnt (long (pow 2 25)))
(def x-magic 2.0)
(def y-magic 4.0)

(facts
 "RealVector methods"
 (with-default
   (let [host-x (doto (sv cnt) (fset! x-magic))
         host-y (doto (sv cnt) (fset! y-magic))]
     (with-release [settings (cl-settings *command-queue*)
                    cl-x (cl-sv settings cnt)
                    cl-y (cl-sv settings cnt)]

       (dim cl-x) => cnt

       (fset! cl-x x-magic)
       (fset! cl-y y-magic)
       (time (dot cl-x cl-y)) => (time (float (dot host-x host-y)))

       (time (asum cl-x)) => (* (float x-magic) (float cnt))

       (fset! host-x 7 (inc (float x-magic)))
       (write! cl-x host-x)
       (time (iamax cl-x)) => 7

       (read! (scal! 2 cl-x) (sv cnt)) => (scal! 2 host-x)

       (read! (axpy! cl-y 2 cl-x) (sv cnt))
       => (axpy! host-y 2 host-x)



))))


(facts
 "Carrier methods"
 (with-default
   (let [host-x (doto (sv cnt) (fset! 3.5))
         host-y (doto (sv cnt) (fset! 1.1))]
     (with-release [settings (cl-settings *command-queue*)
                    cl-x (cl-sv settings cnt)
                    cl-y (cl-sv settings cnt)]

       (p/byte-size cl-x) => Float/BYTES

       (write! cl-x host-x) => cl-x
       (write! cl-y host-y) => cl-y

       (with-release [cl-zero (zero cl-x)]
         (read! cl-zero (sv cnt))) => (sv cnt)

         (swp! cl-x cl-y) => cl-x
         (swp! cl-x cl-y) => cl-x

         (read! cl-x (sv cnt)) => host-x

         (copy! cl-x cl-y) => cl-y

         (read! cl-y host-y) => host-x))))