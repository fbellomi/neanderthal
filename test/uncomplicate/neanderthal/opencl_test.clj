(ns uncomplicate.neanderthal.opencl-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl
             [core :refer [with-default with-release *context* *command-queue*]]]
            [uncomplicate.neanderthal
             [core :refer :all]
             [opencl :refer :all]
             [math :refer [pow]]])
  (:import [uncomplicate.neanderthal.protocols Block]))

(defmacro test-clblock [engine-factory rge rv]
  `(with-default
     (let [m# 33
           n# (long (+ 1000 (pow 2 12)))
           cnt# n#
           x-magic# 2
           y-magic# 5
           magic# 17.0]
       (with-release [host-a# (doto (~rge m# n#) (entry! x-magic#))
                      host-b# (doto (~rge m# n#) (entry! y-magic#))
                      host-x# (doto (~rv cnt#) (entry! x-magic#))
                      host-y# (doto (~rv cnt#) (entry! y-magic#))
                      engine# (~engine-factory *context* *command-queue*)
                      cl-a# (create-ge-matrix engine# m# n# host-a#)
                      cl-b# (create-ge-matrix engine# m# n#)
                      cl-x# (create-vector engine# cnt#)
                      cl-y# (create-vector engine# cnt#)
                      cl-z# (create-vector engine# cnt#)
                      row-a# (row cl-a# 3)
                      subvector-x# (subvector cl-x# 3 (/ cnt# 2))]
         (facts
          "OpenCL Vector: equality and hashCode."
          (.equals cl-x# nil) => false
          cl-x# => cl-x#
          (transfer! host-x# cl-x#) => (transfer! host-x# cl-y#)
          (= cl-x# cl-z#) => false
          (transfer! host-y# cl-y#) =not=> cl-x#
          row-a# => cl-x#
          (row cl-a# 4) =not=> (col cl-a# 4))

         (facts
          "OpenCL Matrix: equality and hashCode."
          cl-a# => cl-a#
          (transfer! host-a# cl-b#) => cl-a#
          (transfer! host-b# cl-b#) =not=> cl-a#
          cl-a# =not=> cl-x#
          )

         (facts
          "Matrix rows and columns."
          (dim subvector-x#) => (/ cnt# 2)
          (transfer! (entry! host-x# 3 magic#) cl-x#) => cl-x#
          (entry (transfer! subvector-x# (~rv (/ cnt# 2))) 0) => magic#
          (entry (transfer! (copy! (subvector cl-x# 3 (mrows cl-a#))
                                   (copy (col cl-a# 6)))
                            (~rv (mrows cl-a#))) 0) => magic#)))))

(defmacro test-blas1 [engine-factory rv]
  `(facts
    "BLAS methods"
    (with-default
      (let [cnt# (long (+ 1000 (pow 2 12)))
            x-magic# 2
            y-magic# 5]
        (with-release [host-x# (doto (~rv cnt#) (entry! x-magic#))
                       host-y# (doto (~rv cnt#) (entry! y-magic#))
                       engine# (~engine-factory *context* *command-queue*)
                       cl-x# (create-vector engine# cnt#)
                       cl-y# (create-vector engine# cnt#)]

          (dim cl-x#) => cnt#

          (entry! host-x# 6 -100000.0)
          (transfer! host-x# cl-x#)
          (transfer! host-y# cl-y#)

          (float (dot cl-x# cl-y#)) => (float (dot host-x# host-y#))

          (float (asum cl-x#)) => (float (asum host-x#))

          (float (sum cl-x#)) => (float (sum host-x#))

          (nrm2 cl-x#) => (roughly (nrm2 host-x#))

          (iamax cl-x#) => 6

          (transfer! (scal! 2 cl-x#) (~rv cnt#)) => (scal! 2 host-x#)

          (transfer! (axpy! 2 cl-x# cl-y#) (~rv cnt#)) => (axpy! 2 host-x# host-y#)))

      (let [cnt# (long (+ 1000 (pow 2 12)))
            x-magic# 2
            y-magic# 5]
        (with-release [ host-x# (doto (~rv cnt#) (entry! 3.5))
                       host-y# (doto (~rv cnt#) (entry! 1.1))
                       engine# (~engine-factory *context* *command-queue*)
                       cl-x# (create-vector engine# cnt#)
                       cl-y# (create-vector engine# cnt#)]

          (transfer! host-x# cl-x#) => cl-x#
          (transfer! host-y# cl-y#) => cl-y#

          (with-release [cl-zero# (zero cl-x#)]
            (transfer! cl-zero# (~rv cnt#))) => (~rv cnt#)

            (swp! cl-x# cl-y#) => cl-x#
            (swp! cl-x# cl-y#) => cl-x#

            (transfer! cl-x# (~rv cnt#)) => host-x#

            (copy! cl-x# cl-y#) => cl-y#

            (transfer! cl-y# host-y#) => host-x#)))))

(defmacro test-blas2 [engine-factory rge rv]
  `(facts
    "BLAS 2"
    (with-default
      (let [m-cnt# 2050
            n-cnt# 337
            a-magic# 3
            x-magic# 2
            y-magic# 5]
        (with-release [host-a# (doto (~rge m-cnt# n-cnt#) (entry! a-magic#))
                       host-x# (doto (~rv n-cnt#) (entry! x-magic#))
                       host-y# (doto (~rv m-cnt#) (entry! y-magic#))
                       engine# (~engine-factory *context* *command-queue*)
                       cl-a# (transfer! host-a# (create-ge-matrix engine# m-cnt# n-cnt#))
                       cl-x# (transfer! host-x# (create-vector engine# n-cnt#))
                       cl-y# (transfer! host-y# (create-vector engine# m-cnt#))]

          (transfer! (mv! 10 cl-a# cl-x# 100 cl-y#) (~rv m-cnt#))
          => (mv! 10 host-a# host-x# 100 host-y#))))))

(defn buffer [^Block b]
  (.buffer b))

(defmacro test-blas3 [engine-factory rge rv]
  `(facts
    "BLAS 3"
    (let [m-cnt# 123
          k-cnt# 456
          n-cnt# 789]
      (with-default
        (with-release [host-a# (~rge m-cnt# k-cnt# (repeatedly (* m-cnt# k-cnt#) rand))
                       host-b# (~rge k-cnt# n-cnt#
                                     (map (partial * 2) (repeatedly (* k-cnt# n-cnt#) rand)))
                       host-c# (~rge m-cnt# n-cnt#
                                     (map (partial * 2) (repeatedly (* m-cnt# n-cnt#) rand)))
                       engine# (~engine-factory *context* *command-queue*)
                       cl-a# (transfer! host-a# (create-ge-matrix engine# m-cnt# k-cnt#))
                       cl-b# (transfer! host-b# (create-ge-matrix engine# k-cnt# n-cnt#))
                       cl-c# (transfer! host-c# (create-ge-matrix engine# m-cnt# n-cnt#))]

          (< (double
              (nrm2 (~rv (buffer (axpy! -1 (mm! 10 host-a# host-b# 100 host-c#)
                                        (transfer! (mm! 10 cl-a# cl-b# 100 cl-c#)
                                                   (~rge m-cnt# n-cnt#)))))))
             (* 2 m-cnt# n-cnt# k-cnt# 1e-8)) => true)))))

(defmacro test-all [engine-factory rge rv]
  `(do
     (test-clblock ~engine-factory ~rge ~rv)
     (test-blas1 ~engine-factory ~rv)
     (test-blas2 ~engine-factory ~rge ~rv)
     (test-blas3 ~engine-factory ~rge ~rv)))
