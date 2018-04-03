;; # Namespace scope
;;
;; Collection of math function:
;;
;; * Several constants from Java, C, Processing, etc.
;; * Functions based on FastMath exposed as macros or functions (trigonometry, powers/logarithms/roots, rounding)
;; * Primitive operators (as in primitive-math package)
;; * Additional math functions (signum, constrain, interpolation)
;; * Statistics

(ns fastmath.core
  "Collection of fast math functions and plethora of constants known from other math libraries.

  #### Primitive math operators

  Based on [Primitive Math by Zach Tellman](https://github.com/ztellman/primitive-math) several operators are introduced and replace `clojure.core` functions. All operators are macros and can't be used as functions. List includes:

  Known from Clojure: `*` `+` `-` `/` `>` `<` `>=` `<=` `==` `rem` `quot` `mod` `bit-or` `bit-and` `bit-xor` `bit-not` `bit-shift-left` `bit-shift-right` `unsigned-bit-shift-right` `inc` `dec` `zero?` `neg?` `pos?` `min` `max` `even?` `odd?`

  And additionally:

  * `bool-and` - `and` working on booleans
  * `bool-or` - boolean `or`
  * `bool-xor` - boolean `xor`
  * `bool-not` - boolean `not`
  * `<<` - bit shift left
  * `>>` - signed bit shift right
  * `>>>` - unsigned bit shift right
  * `not==` - not equal

  To turn on primitive math on your namespace call [[use-primitive-operators]].
  To turn off and revert original versions call [[unuse-primitive-operators]]

  #### Fast Math

  Almost all math functions are backed by [FastMath](https://github.com/jeffhain/jafama) library. Most of them are macros. Some of them are wrapped in Clojure functions. Almost all operates on primitive `double` and returns `double` (with an exception [[round]] or [[qround]] which returns `long`).

  #### Other functions

  Additionally namespace contains functions which are common in frameworks like OpenFrameworks and Processing.

  * For random/noise functions check [[fastmath.random]] namespace.
  * [[fastmath.vector]] contains vector (2,3,4 dim. + double array + clojure vector) protocol and implementations.
  * [[fastmath.complex]] contains complex number operations."
  {:metadoc/categories {:trig "Trigonometry"
                        :pow "Powers / logarithms"
                        :conv "Conversions"
                        :err "Error"
                        :dist "Distance"
                        :round "Rounding"
                        :sign "Sign"
                        :bitwise "Bitwise"
                        :mod "Mod"
                        :compare "Comparison"
                        :prim "Primitive"
                        :special "Special functions"
                        :bool "Boolean"}}
  (:require [metadoc.examples :refer :all])
  (:refer-clojure
   :exclude [* + - / > < >= <= == rem quot mod bit-or bit-and bit-xor bit-not bit-shift-left bit-shift-right unsigned-bit-shift-right inc dec zero? neg? pos? min max even? odd?])
  (:import [net.jafama FastMath]
           [fastmath.java PrimitiveMath]
           [clojure.lang Numbers]
           [org.apache.commons.math3.util Precision]
           [org.apache.commons.math3.special Erf Gamma Beta]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; ## Macros

(defmacro ^:private javaclass-proxy
  "Wrapps operation into macro"
  ([class arity alt-name name]
   (let [cf (str class "/" name)
         f (symbol cf)
         x (symbol "x")
         y (symbol "y") 
         doc (or (:doc (meta alt-name)) (str cf " function wrapped in macro."))
         arity-1 `([~x] (list '~f ~x))
         arity-2 `([~x ~y] (list '~f ~x ~y))]
     (condp = arity
       :two `(defmacro ~alt-name ~doc ~arity-2)
       :onetwo `(defmacro ~alt-name ~doc ~arity-1 ~arity-2)
       `(defmacro ~alt-name ~doc ~arity-1))))
  ([class arity name]
   `(javaclass-proxy ~class ~arity ~name ~name)))

(defmacro ^:private fastmath-proxy [& rest] `(javaclass-proxy "net.jafama.FastMath" ~@rest))
(defmacro ^:private primitivemath-proxy [& rest] `(javaclass-proxy "fastmath.java.PrimitiveMath" ~@rest))
(defmacro ^:private erf-proxy [& rest] `(javaclass-proxy "org.apache.commons.math3.special.Erf" ~@rest))
(defmacro ^:private gamma-proxy [& rest] `(javaclass-proxy "org.apache.commons.math3.special.Gamma" ~@rest))
(defmacro ^:private beta-proxy [& rest] `(javaclass-proxy "org.apache.commons.math3.special.Beta" ~@rest))

(defmacro ^:private variadic-proxy
  "Creates left-associative variadic forms for any operator.
  https://github.com/ztellman/primitive-math/blob/master/src/primitive_math.clj#L10"
  ([name]
   `(variadic-proxy ~name ~name))
  ([name fn]
   `(variadic-proxy ~name ~fn identity))
  ([name fn single-arg-form]
   (let [x (symbol "x")
         y (symbol "y")
         rest (symbol "rest")
         fname (symbol (str "fastmath.java.PrimitiveMath/" fn))
         doc (or (:doc (meta name)) (str "A primitive math version of `" name "`"))]
     `(defmacro ~name
        ~doc
        ([~x]
         ~((eval single-arg-form) x))
        ([~x ~y]
         (list '~fname ~x ~y))
        ([~x ~y ~'& ~rest]
         (list* '~name (list '~name ~x ~y) ~rest))))))

(defmacro ^:private variadic-predicate-proxy
  "Turns variadic predicates into multiple pair-wise comparisons.
  https://github.com/ztellman/primitive-math/blob/master/src/primitive_math.clj#L27"
  ([name]
   `(variadic-predicate-proxy ~name ~name))
  ([name fn]
   `(variadic-predicate-proxy ~name ~fn (constantly true)))
  ([name fn single-arg-form]
   (let [x (symbol "x")
         y (symbol "y")
         rest (symbol "rest")
         fname (symbol (str "fastmath.java.PrimitiveMath/" fn))
         doc (or (:doc (meta name)) (str "A primitive math version of `" name "`"))]
     `(defmacro ~name
        ~doc
        ([~x]
         ~((eval single-arg-form) x))
        ([~x ~y]
         (list '~fname ~x ~y))
        ([~x ~y ~'& ~rest]
         (list 'fastmath.java.PrimitiveMath/and (list '~name ~x ~y) (list* '~name ~y ~rest)))))))

;; ## Basic operations

(variadic-proxy ^{:metadoc/categories #{:primitive}} + add)
(variadic-proxy ^{:metadoc/categories #{:primitive}} - subtract (fn [x] `(list 'fastmath.java.PrimitiveMath/negate ~x)))
(variadic-proxy ^{:metadoc/categories #{:primitive}} * multiply)
(variadic-proxy ^{:metadoc/categories #{:primitive}} / divide (fn [x] `(list 'fastmath.java.PrimitiveMath/reciprocal ~x)))
(primitivemath-proxy :one ^{:metadoc/categories #{:primitive}} inc)
(primitivemath-proxy :one ^{:metadoc/categories #{:primitive}} dec)
(primitivemath-proxy :two ^{:metadoc/categories #{:mod}} rem remainder)
(primitivemath-proxy :two ^{:metadoc/categories #{:mod}} quot quotient)
(primitivemath-proxy :two ^{:metadoc/categories #{:mod}} mod modulus)
(variadic-proxy ^{:metadoc/categories #{:bitwise}} bit-and bitAnd)
(variadic-proxy ^{:metadoc/categories #{:bitwise}} bit-or bitOr)
(variadic-proxy ^{:metadoc/categories #{:bitwise}} bit-xor bitXor)
(primitivemath-proxy :one ^{:metadoc/categories #{:bitwise}} bit-not bitNot)
(variadic-proxy ^{:metadoc/categories #{:bool}} bool-and and)
(variadic-proxy ^{:metadoc/categories #{:bool}} bool-or or)
(variadic-proxy ^{:metadoc/categories #{:bool}} bool-xor xor)
(primitivemath-proxy :one ^{:metadoc/categories #{:bool}} bool-not not)
(variadic-proxy ^{:metadoc/categories #{:stat}} min)
(variadic-proxy ^{:metadoc/categories #{:stat}} max)
(primitivemath-proxy :one ^{:metadoc/categories #{:compare}} zero? isZero)
(primitivemath-proxy :one ^{:metadoc/categories #{:compare}} neg? isNeg)
(primitivemath-proxy :one ^{:metadoc/categories #{:compare}} pos? isPos)
(primitivemath-proxy :one ^{:metadoc/categories #{:compare}} even? isEven)
(primitivemath-proxy :one ^{:metadoc/categories #{:compare}} odd? isOdd)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} << shiftLeft)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} >> shiftRight)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} >>> unsignedShiftRight)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} bit-shift-left shiftLeft)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} bit-shift-right shiftRight)
(primitivemath-proxy :two ^{:metadoc/categories #{:bitwise}} unsigned-bit-shift-right unsignedShiftRight)

(add-examples rem
  (example-session "Remainder (compared to `clojure.core` version)."
    (rem 3.123 0.333)
    (rem -3.123 0.333)
    (rem -3.123 -0.333)
    (rem 3.123 -0.333)
    (clojure.core/rem 3.123 0.333)
    (clojure.core/rem -3.123 0.333)
    (clojure.core/rem -3.123 -0.333)
    (clojure.core/rem 3.123 -0.333)))

(add-examples quot
  (example-session "Quotient (compared to `clojure.core` version)."
    (quot 3.123 0.333)
    (quot -3.123 0.333)
    (quot -3.123 -0.333)
    (quot 3.123 -0.333)
    (clojure.core/quot 3.123 0.333)
    (clojure.core/quot -3.123 0.333)
    (clojure.core/quot -3.123 -0.333)
    (clojure.core/quot 3.123 -0.333)))

(add-examples mod
  (example-session "Modulus (compared to `clojure.core` version)."
    (mod 3.123 0.333)
    (mod -3.123 0.333)
    (mod -3.123 -0.333)
    (mod 3.123 -0.333)
    (clojure.core/mod 3.123 0.333)
    (clojure.core/mod -3.123 0.333)
    (clojure.core/mod -3.123 -0.333)
    (clojure.core/mod 3.123 -0.333)))

(variadic-predicate-proxy ^{:metadoc/categories #{:compare}} < lt)
(variadic-predicate-proxy ^{:metadoc/categories #{:compare}} > gt)
(variadic-predicate-proxy ^{:metadoc/categories #{:compare}} <= lte)
(variadic-predicate-proxy ^{:metadoc/categories #{:compare}} >= gte)
(variadic-predicate-proxy ^{:doc "Equality. See also [[eq]] for function version." :metadoc/categories #{:compare}} == eq)
(variadic-predicate-proxy ^{:metadoc/categories #{:compare}} not== neq)

;; Primitive math eq
(defn eq 
  "Primitive math equality function for doubles. See [[==]]."
  {:metadoc/categories #{:compare}}
  ([^double a] true)
  ([^double a ^double b]
   (== a b))
  ([^double a ^double b ^double c]
   (bool-and (== a b) (== b c)))
  ([^double a ^double b ^double c ^double d]
   (bool-and (== a b) (== b c) (== c d))))

;; Processing math constants
(def ^:const ^double ^{:doc "Value of \\\\(\\pi\\\\)"} PI Math/PI)
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{\\pi}{2}\\\\)"} HALF_PI (/ PI 2.0))
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{\\pi}{3}\\\\)"} THIRD_PI (/ PI 3.0))
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{\\pi}{4}\\\\)"} QUARTER_PI (/ PI 4.0))
(def ^:const ^double ^{:doc "Value of \\\\(2 {\\pi}\\\\)"} TWO_PI (* PI 2.0))
(def ^:const ^double ^{:doc "Alias for [[TWO_PI]]"} TAU TWO_PI)
(def ^:const ^double ^{:doc "Value of \\\\(e\\\\)"} E Math/E)

(def ^:const ^double ^{:doc "Very small number \\\\(\\varepsilon\\\\)"} EPSILON 1.0e-10)

(def ^:const ^double ^{:doc "Euler-Mascheroni constant"} GAMMA Gamma/GAMMA)

(def ^:const ^double ^{:doc "Smallest machine number. Value is calculated during evaluation and may differ on different processors."}
  MACHINE-EPSILON (* 0.5 (double (loop [d (double 1.0)]
                                   (if (not== 1.0 (+ 1.0 (* d 0.5)))
                                     (recur (* d 0.5))
                                     d)))))
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{1}{3}\\\\)"} THIRD (/ 3.0))
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{2}{3}\\\\)"} TWO_THIRD (/ 2.0 3.0))
(def ^:const ^double ^{:doc "Value of \\\\(\\frac{1}{6}\\\\)"} SIXTH (/ 6.0))

;; Trigonometry
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} sin)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} cos)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} tan)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} asin)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} acos)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} atan)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} sinh)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} cosh)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} tanh)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} asinh)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} acosh)
(fastmath-proxy :one ^{:metadoc/categories #{:trig}} atanh)

(fastmath-proxy :one ^{:doc "Fast and less accurate [[sin]]." :metadoc/categories #{:trig}} qsin sinQuick)
(fastmath-proxy :one ^{:doc "Fast and less accurate [[cos]]." :metadoc/categories #{:trig}} qcos cosQuick)

(add-examples qsin
  (example-session "Compare [[sin]] and [[qsin]]." (sin 1.123) (qsin 1.123)))

(add-examples qcos
  (example-session "Compare [[cos]] and [[qcos]]." (cos 1.123) (qcos 1.123)))

;; Additional trigonometry functions
(defn ^{:doc "Cotangent" :metadoc/categories #{:trig}} cot ^double [^double v] (FastMath/tan (- HALF_PI v)))
(defn ^{:doc "Secant" :metadoc/categories #{:trig}} sec ^double [^double v] (/ (FastMath/cos v)))
(defn ^{:doc "Cosecant" :metadoc/categories #{:trig}} csc ^double [^double v] (/ (FastMath/sin v)))

;; Additional cyclometric functions
(defn ^{:doc "Arccotangent" :metadoc/categories #{:trig}} acot ^double [^double v] (- HALF_PI (FastMath/atan v)))
(defn ^{:doc "Arcsecant" :metadoc/categories #{:trig}} asec ^double [^double v] (FastMath/acos (/ 1.0 v)))
(defn ^{:doc "Arccosecant" :metadoc/categories #{:trig}} acsc ^double [^double v] (FastMath/asin (/ 1.0 v)))
(fastmath-proxy :two ^{:metadoc/categories #{:trig}} atan2)

;; Additional hyperbolic functions
(defn ^{:doc "Hyperbolic cotangent" :metadoc/categories #{:trig}} coth ^double [^double v] (/ (FastMath/tanh v)))
(defn ^{:doc "Hyperbolic secant" :metadoc/categories #{:trig}} sech ^double [^double v] (/ (FastMath/cosh v)))
(defn ^{:doc "Hyperbolic cosecant" :metadoc/categories #{:trig}} csch ^double [^double v] (/ (FastMath/sinh v)))

;; Additional inverse hyperbolic functions
(defn ^{:doc "Area hyperbolic cotangent" :metadoc/categories #{:trig}} acoth ^double [^double v] (FastMath/atanh (/ v)))
(defn ^{:doc "Area hyperbolic secant" :metadoc/categories #{:trig}} asech ^double [^double v] (FastMath/acosh (/ v)))
(defn ^{:doc "Area hyperbolic cosecant" :metadoc/categories #{:trig}} acsch ^double [^double v] (FastMath/asinh (/ v)))

;; exp and log
(fastmath-proxy :one ^{:metadoc/categories #{:pow}} exp)
(fastmath-proxy :one ^{:metadoc/categories #{:pow}} log)
(fastmath-proxy :one ^{:doc "\\\\(\\ln_{10}{x}\\\\)" :metadoc/categories #{:pow}} log10)
;; Alias for natural logarithm
(fastmath-proxy :one ^{:metadoc/categories #{:pow}} ln log)

;; Roots (square and cubic)
(fastmath-proxy :one ^{:doc "\\\\(\\sqrt{x}\\\\)" :metadoc/categories #{:pow}} sqrt)
(fastmath-proxy :one ^{:doc "\\\\(\\sqrt[3]{x}\\\\)" :metadoc/categories #{:pow}} cbrt)

;; Quick version of exponential \\(e^x\\)
(fastmath-proxy :one ^{:doc "Quick and less accurate version of [[exp]]." :metadoc/categories #{:pow}} qexp expQuick)

(add-examples qexp
  (example-session "Compare [[exp]] and [[qexp]]." (exp 1.123) (qexp 1.123)))

;; Radians to degrees (and opposite) conversions
(def ^:const ^double ^{:doc "\\\\(\\frac{180}{\\pi}\\\\)"} rad-in-deg (/ 180.0 PI))
(def ^:const ^double ^{:doc "\\\\(\\frac{\\pi}{180}\\\\)"} deg-in-rad (/ PI 180.0))
(defn ^{:doc "Convert degrees into radians."
        :metadoc/categories #{:conv}
        :metadoc/examples [(example "Let's convert 180 degrees to radians." (radians 180))]}
  radians ^double [^double deg] (* deg-in-rad deg))
(defn ^{:doc "Convert radians into degrees."
        :metadoc/categories #{:conv}
        :metadoc/examples [(example "Let's convert \\\\(\\pi\\\\) radians to degrees." (degrees PI))]}
  degrees ^double [^double rad] (* rad-in-deg rad))

;; Erf
(erf-proxy :onetwo ^{:doc "Error function. For two arguments return difference between `(erf x)` and `(erf y)`." :metadoc/categories #{:err}} erf)
(erf-proxy :one ^{:doc "Complementary error function." :metadoc/categories #{:err}} erfc)
(erf-proxy :one ^{:doc "Inverse [[erf]]." :metadoc/categories #{:err}} inv-erf erfInv)
(erf-proxy :one ^{:doc "Inverse [[erfc]]." :metadoc/categories #{:err}} inv-erfc erfcInv)

;; Gamma

(gamma-proxy :one ^{:doc "Gamma function \\\\(\\Gamma(x)\\\\)" :metadoc/categories #{:special}} gamma gamma)
(gamma-proxy :one ^{:doc "Gamma function \\\\(\\ln\\Gamma(x)\\\\)" :metadoc/categories #{:special}} log-gamma logGamma)
(gamma-proxy :one ^{:doc "Logarithmic derivative of \\\\(\\Gamma\\\\)." :metadoc/categories #{:special}} dgamma digamma)

;; Beta

(beta-proxy :two ^{:doc "Logarithm of Beta function." :metadoc/categories #{:special}} log-beta logBeta)

;; Sinc
(defn sinc
  "Sinc function."
  {:metadoc/categories #{:trig}}
  ^double [^double v]
  (let [x (* PI (FastMath/abs v))]
    (if (< x 1.0e-5) 1.0
        (/ (FastMath/sin x) x))))

;;
(defn sigmoid
  "Sigmoid function"
  {:metadoc/categories #{:pow}}
  ^double [^double x]
  (/ (inc (exp (- x)))))

(def ^:const ^double ^{:doc "\\\\(\\ln{2}\\\\)"} LN2 (log 2.0))
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\ln{2}}\\\\)"} INV_LN2 (/ LN2))
(def ^:const ^double ^{:doc "\\\\(\\frac{\\ln{2}}{2}\\\\)"} LN2_2 (* 0.5 LN2))
(def ^:const ^double ^{:doc "\\\\(\\ln{10}\\\\)"} LN10 (log 10.0))
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\ln{0.5}}\\\\)"} INV_LOG_HALF (/ (log 0.5)))

(defn log2
  "Logarithm with base 2.

  \\\\(\\ln_2{x}\\\\)"
  {:metadoc/categories #{:pow}}
  ^double [^double x]
  (* (FastMath/log x) INV_LN2))

;; \\(\log_b x\\)
(defn logb
  "Logarithm with base `b`.

  \\\\(\\ln_b{x}\\\\)"
  {:metadoc/categories #{:pow}}
  ^double [^double b ^double x]
  (/ (FastMath/log x) (FastMath/log b)))

;; Quick logarithm
(fastmath-proxy :one ^{:doc "Fast and less accurate version of [[log]]." :metadoc/categories #{:pow}} qlog logQuick)

(add-examples qlog
  (example-session "Compare [[log]] and [[qlog]]." (log 23.123) (qlog 23.123)))

;; \\(\log_2 e\\)
(def ^:const ^double ^{:doc "\\\\(\\log_{2}{e}\\\\)"} LOG2E (log2 E))

;; \\(\log_{10} e\\)
(def ^:const ^double ^{:doc "\\\\(\\log_{10}{e}\\\\)"} LOG10E (log10 E))

;; Powers (normal, quick)
(fastmath-proxy :two ^{:metadoc/categories #{:pow}} pow)
(fastmath-proxy :two ^{:doc "Fast and less accurate version of [[pow]]." :metadoc/categories #{:pow}} qpow powQuick)

(add-examples qpow
  (example-session "Compare [[pow]] and [[qpow]]." (pow 1.23 43.3) (qpow 1.23 43.3)))

;; Fast version of power, second parameter should be integer
(fastmath-proxy :two ^{:doc "Fast version of pow where exponent is integer." :metadoc/categories #{:pow}} fpow powFast)

(add-examples fpow
  (example-session "Example" (fpow 1.23 4) (fpow 1.23 4.123) (fpow 1.23 4.999) (fpow 1.23 5) (fpow 1.23 -2)))

;; Square and cubic
(defn sq "Same as [[pow2]]. \\\\(x^2\\\\)" {:metadoc/categories #{:pow}} ^double [^double x] (* x x))
(defn pow2 "Same as [[sq]]. \\\\(x^2\\\\)" {:metadoc/categories #{:pow}} ^double [^double x] (* x x))
(defn pow3 "\\\\(x^3\\\\)" {:metadoc/categories #{:pow}} ^double [^double x] (* x (* x x)))

(defn safe-sqrt
  "Safe sqrt, for value <= 0 result is 0.

  \\\\(
  \\left\\\\{
  \\begin{array}{lr}
  0 & : x \\leq 0\\\\\\\\
  \\sqrt{x} & : x > 0
  \\end{array}
  \\\\right.
  \\\\)"
  {:metadoc/categories #{:pow}}
  ^double [^double value]
  (if (neg? value) 0.0 (sqrt value)))

;; Approximated sqrt via binary operations (error 1.0E-2)
(fastmath-proxy :one ^{:doc "Approximated [[sqrt]] using binary operations with error `1.0E-2`." :metadoc/categories #{:pow}} qsqrt sqrtQuick)
(fastmath-proxy :one ^{:doc "Inversed version of [[qsqrt]]. Quick and less accurate." :metadoc/categories #{:pow}} rqsqrt invSqrtQuick)

(add-examples qsqrt
  (example-session "Compare [[sqrt]] and [[qsqrt]]." (sqrt 23.123) (qsqrt 23.123)))

(defn hypot
  "Hypot.
  See also [[hypot-sqrt]]."
  {:metadoc/categories #{:dist}}
  (^double [^double x ^double y]
   (FastMath/hypot x y))
  (^double [^double x ^double y ^double z]
   (FastMath/hypot x y z)))

(defn hypot-sqrt
  "Hypot, sqrt version: \\\\(\\sqrt{x^2+y^2}\\\\) or \\\\(\\sqrt{x^2+y^2+z^2}\\\\).
  Should be faster than [[hypot]]."
  {:metadoc/categories #{:dist}}
  (^double [^double x ^double y]
   (sqrt (+ (* x x) (* y y))))
  (^double [^double x ^double y ^double z]
   (sqrt (+ (* x x) (* y y) (* z z)))))

;; distance
(defn dist
  "Euclidean distance between points `(x1,y1)` and `(x2,y2)`. See [[fastmath.vector]] namespace to see other metrics which work on vectors."
  {:metadoc/categories #{:dist}
   :metadoc/examples [(example "Distance between two points." (dist 1 3 -2 10))]}
  ^double [^double x1 ^double y1 ^double x2 ^double y2]
  (sqrt (+ (sq (- x2 x1)) (sq (- y2 y1)))))

(defn qdist
  "Quick version of Euclidean distance between points. [[qsqrt]] is used instead of [[sqrt]]."
  {:metadoc/categories #{:dist}
   :metadoc/examples [(example "Distance between two points (quick version)." (qdist 1 3 -2 10))
              (example "Distance between two points (accurate version)." (dist 1 3 -2 10))]}
  ^double [^double x1 ^double y1 ^double x2 ^double y2]
  (qsqrt (+ (sq (- x2 x1)) (sq (- y2 y1)))))

;; Rounding functions
(defn floor "\\\\(\\lfloor x \\rfloor\\\\). See: [[qfloor]]." {:metadoc/categories #{:round}} ^double [^double x] (FastMath/floor x))
(defn ceil "\\\\(\\lceil x \\rceil\\\\). See: [[qceil]]." {:metadoc/categories #{:round}} ^double [^double x] (FastMath/ceil x))
(defn ^{:doc "Round to `long`. See: [[rint]], [[qround]]."
        :metadoc/categories #{:round}
        :metadoc/examples [(example "Round long." (round PI))]} round ^long [^double x] (FastMath/round x))
(defn ^{:doc "Round to `double`. See [[round]], [[qround]]."
        :metadoc/categories #{:round}
        :metadoc/examples [(example "Round to double." (rint PI))]} rint ^double [^double x] (FastMath/rint x))

(primitivemath-proxy :one ^{:doc "Fast version of [[floor]]. Returns `long`. See: [[floor]]." :metadoc/categories #{:round}} qfloor fastFloor)
(primitivemath-proxy :one ^{:doc "Fast version of [[ceil]]. Returns `long`. See: [[ceil]]." :metadoc/categories #{:round}} qceil fastCeil)
(primitivemath-proxy :one ^{:doc "Fast version of [[round]]. Returns `long`. See: [[rint]], [[round]]." :metadoc/categories #{:round}} qround fastRound)

(fastmath-proxy :two ^{:doc "From `FastMath` doc: returns dividend - divisor * n,
where n is the mathematical integer closest to dividend/divisor. Returned value in `[-|divisor|/2,|divisor|/2]`"
                       :metadoc/categories #{:mod}} remainder)

(add-examples remainder
  (example-session "Compare with [[rem]] and [[mod]]."
    (remainder 3.123 0.2)
    (rem 3.123 0.2)
    (mod 3.123 0.2)))

(defn abs "\\\\(|x|\\\\) - `double` version. See [[iabs]]." {:metadoc/categories #{:round}} ^double [^double x] (FastMath/abs x))
(defn iabs "\\\\(|x|\\\\) - `long` version. See [[abs]]." {:metadoc/categories #{:round}} ^long [^long x] (if (neg? x) (- x) x))

(defn trunc
  "Truncate fractional part, keep sign. Returns `double`."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example-session "Examples" (trunc 1.234) (trunc -1.544))]}
  ^double [^double v] (if (neg? v) (ceil v) (floor v)))

(defn itrunc
  "Truncate fractional part, keep sign. Returns `long`."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example-session "Examples" (itrunc 1.234) (itrunc -1.544))]}
  ^long [^double v] (if (neg? v) (qceil v) (qfloor v)))

;; return approximate value
(defn approx
  "Round `v` to specified (default: 2) decimal places. Be aware of `double` number accuracy."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example "Default rounding (2 digits)." (approx 1.232323))
                      (example "Rounding up to 4 digits." (approx 1.232323 4))]}
  (^double [^double v] (Precision/round v (int 2)))
  (^double [^double v ^long digits] (Precision/round v (int digits))))

(defn approx-eq
  "Checks equality approximately. See [[approx]]."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example "Default rounding (2 digits)." (approx-eq 1.232323 1.231999))
              (example "Rounding up to 4 digits." (approx-eq 1.232323 1.23231999 4))
              (example "Keep an eye on rounding" (approx-eq 1.2349 1.2350))]}
  ([^double a ^double b] (== (approx a) (approx b)))
  ([^double a ^double b ^long digits] (== (approx a digits)
                                          (approx b digits))))

(defn frac
  "Fractional part, always returns values from 0.0 to 1.0 (exclusive). See [[sfrac]] for signed version."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example-session "Examples" (frac 0.555) (frac -0.555))]}
  ^double [^double v] (abs (- v (unchecked-long v))))

(defn sfrac
  "Fractional part, always returns values from -1.0 to 1.0 (exclusive). See [[frac]] for unsigned version."
  {:metadoc/categories #{:round}
   :metadoc/examples [(example-session "Examples" (sfrac 0.555) (sfrac -0.555))]}
  ^double [^double v] (- v (trunc v)))

;; Find power of 2 exponent for double number where  
;; \\(2^(n-1)\leq x\leq 2^n\\)  
;; where n-1 is result of `low-2-exp` and n is result of `high-2-exp`
;; `(low-2-exp TWO_PI) => 2` \\(2^2\eq 4\leq 6.28\\)  
;; `(high-2-exp TWO_PI) => 3` \\(6.28\leq 2^3\eq 8\\)
(defn low-2-exp
  "Find greatest exponent (power of 2) which is lower than `x`. See [[high-2-exp]]."
  {:metadoc/categories #{:pow}
   :metadoc/examples [(example "Result 4 means, that \\\\(2^4=16\\\\) is lower than 23.11. Next exponent (5) gives greater value (32)." (low-2-exp 23.11))
              (example "For `x` less than 1.0 gives negative exponent." (low-2-exp 0.11))]}
  ^long [^double x] (-> x log2 floor unchecked-long))

(defn high-2-exp
  "Find lowest exponent (power of 2) which is greater than `x`. See [[low-2-exp]]."
  {:metadoc/categories #{:pow}
   :metadoc/examples [(example "Result 5 means, that \\\\(2^5=32\\\\) is greater than 23.11. Lower exponent (4) gives lower value (16)." (high-2-exp 23.11))
              (example "For `x` less than 1.0 gives negative exponent." (high-2-exp 0.11))]}
  ^long [^double v] (-> v log2 ceil unchecked-long))

(defn round-up-pow2
  "Round long to the next power of 2"
  {:metadoc/categories #{:round}
   :metadoc/examples [(example-session "Examples" (round-up-pow2 1023) (round-up-pow2 1024) (round-up-pow2 1025))]}
  ^long [^long v]
  (as-> (dec v) v
    (bit-or v (>> v 1))
    (bit-or v (>> v 2))
    (bit-or v (>> v 4))
    (bit-or v (>> v 8))
    (bit-or v (>> v 16))
    (bit-or v (>> v 32))
    (inc v)))

(defn next-float-up
  "Next double value. Optional value `delta` sets step amount."
  {:metadoc/examples [(example "Next double." (next-float-up 1234.56789))
              (example "Next double with delta." (next-float-up 1234.56789 1000))]}
  (^double [^double v]
   (let [ui (Double/doubleToRawLongBits (if (zero? v) 0.0 v))]
     (Double/longBitsToDouble (if (neg? v) (dec ui) (inc ui)))))
  (^double [^double v ^long delta]
   (let [ui (Double/doubleToRawLongBits (if (zero? v) 0.0 v))]
     (Double/longBitsToDouble (if (neg? v) (- ui delta) (+ ui delta))))))

(defn next-float-down
  "Previous double value. Optional value `delta` sets step amount."
  {:metadoc/examples [(example "Prev. double." (next-float-down 1234.56789))
              (example "Prev. double with delta." (next-float-down 1234.56789 1000))]}
  (^double [^double v]
   (let [ui (Double/doubleToRawLongBits (if (zero? v) 0.0 v))]
     (Double/longBitsToDouble (if (pos? v) (dec ui) (inc ui)))))
  (^double [^double v ^long delta]
   (let [ui (Double/doubleToRawLongBits (if (zero? v) 0.0 v))]
     (Double/longBitsToDouble (if (pos? v) (- ui delta) (+ ui delta))))))

;; More constants

;; \\(\sqrt{2}\\)
(def ^:const ^double ^{:doc "\\\\(\\sqrt{2}\\\\)"} SQRT2 (sqrt 2.0))
(def ^:const ^double ^{:doc "\\\\(\\frac{\\sqrt{2}}{2}\\\\)"} SQRT2_2 (* 0.5 SQRT2))

;; \\(\sqrt{3}\\)
(def ^:const ^double ^{:doc "\\\\(\\sqrt{3}\\\\)"} SQRT3 (sqrt 3.0))

;; \\(\sqrt{5}\\)
(def ^:const ^double ^{:doc "\\\\(\\sqrt{5}\\\\)" }SQRT5 (sqrt 5.0))

;; \\(\sqrt{\pi}\\)
(def ^:const ^double ^{:doc "\\\\(\\sqrt{\\pi}\\\\)"} SQRTPI (sqrt PI))
(def ^:const ^double ^{:doc "\\\\(\\sqrt{2\\pi}\\\\)"} SQRT2PI (sqrt TWO_PI))

;; 
(def ^:const ^double ^{:doc "Golden ratio \\\\(\\varphi\\\\)"} PHI (* (inc SQRT5) 0.5))

;; math.h predefined constants names
(def ^:const ^double ^{:doc "\\\\(e\\\\)"} M_E E)
(def ^:const ^double ^{:doc "\\\\(\\log_{2}{e}\\\\)"} M_LOG2E LOG2E)
(def ^:const ^double ^{:doc "\\\\(\\log_{10}{e}\\\\)"} M_LOG10E LOG10E)
(def ^:const ^double ^{:doc "\\\\(\\ln{2}\\\\)"} M_LN2 LN2)
(def ^:const ^double ^{:doc "\\\\(\\ln{10}\\\\)"} M_LN10 LN10)
(def ^:const ^double ^{:doc "\\\\(\\pi\\\\)"} M_PI PI)
(def ^:const ^double ^{:doc "\\\\(\\frac{\\pi}{2}\\\\)"} M_PI_2 HALF_PI)
(def ^:const ^double ^{:doc "\\\\(\\frac{\\pi}{4}\\\\)"} M_PI_4 QUARTER_PI)
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\pi}\\\\)"} M_1_PI (/ PI))
(def ^:const ^double ^{:doc "\\\\(\\frac{2}{\\pi}\\\\)"} M_2_PI (/ 2.0 PI))
(def ^:const ^double ^{:doc "\\\\(\\frac{2}{\\sqrt\\pi}\\\\)"} M_2_SQRTPI (/ 2.0 SQRTPI))
(def ^:const ^double ^{:doc "\\\\(\\sqrt{2}\\\\)"} M_SQRT2 SQRT2)
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\sqrt{2}}\\\\)"} M_SQRT1_2 (/ SQRT2))

(def ^:const ^double ^{:doc "\\\\(2\\pi\\\\)"} M_TWOPI TWO_PI)
(def ^:const ^double ^{:doc "\\\\(\\frac{3\\pi}{4}\\\\)"} M_3PI_4 (* PI 0.75))
(def ^:const ^double ^{:doc "\\\\(\\sqrt\\pi\\\\)"} M_SQRT_PI SQRTPI)
(def ^:const ^double ^{:doc "\\\\(\\sqrt{3}\\\\)"} M_SQRT3 SQRT3)
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\ln{10}}\\\\)"} M_IVLN10 (/ LN10))
(def ^:const ^double ^{:doc "\\\\(\\ln{2}\\\\)"} M_LOG2_E LN2)
(def ^:const ^double ^{:doc "\\\\(\\frac{1}{\\ln{2}}\\\\)"} M_INVLN2 (/ LN2))

(defn signum
  "Return 1 if `value` is > 0, 0 if it is 0, -1 otherwise. See also [[sgn]].

  \\\\(
  \\left\\\\{
  \\begin{array}{lr}
  1.0 & : x > 0\\\\\\\\
  -1.0 & : x < 0\\\\\\\\
  0.0 & : x = 0
  \\end{array}
  \\\\right.
  \\\\)"
  {:metadoc/categories #{:sign}}
  ^double [^double value]
  (cond (pos? value) 1.0
        (neg? value) -1.0
        true 0.0))

(defn sgn
  "Return -1 when `value` is negative, 1 otherwise. See also [[signum]].

  \\\\(
  \\left\\\\{
  \\begin{array}{lr}
  1.0 & : x \\geq 0\\\\\\\\
  -1.0 & : x < 0\\\\\\\\
  \\end{array}
  \\\\right.
  \\\\)"
  {:metadoc/categories #{:sign}}
  ^double [^double value]
  (if (neg? value) -1.0 1.0))

(defmacro constrain
  "Clamp `value` to the range `[mn,mx]`."
  {:metadoc/categories #{:conv}}
  [value mn mx]
  `(max (min ~value ~mx) ~mn))

(add-examples constrain
  (example-session "Examples" (constrain 0.5 1 2) (constrain 1.5 1 2) (constrain 2.5 1 2)))

(defn norm
  "Normalize `v` from the range `[start,stop]` to the range `[0,1]` or map `v` from the range `[start1,stop1]` to the range `[start2,stop2]`. See also [[make-norm]]."
  {:inline (fn
             ([v start stop] `(PrimitiveMath/norm ~v ~start ~stop))
             ([v start1 stop1 start2 stop2] `(PrimitiveMath/norm ~v ~start1 ~stop1 ~start2 ~stop2)))
   :inline-arities #{3 5}
   :metadoc/categories #{:conv}
   :metadoc/examples [(example "Normalize from [1,-1] to [0,1]" (norm 0.234 -1.0 1.0))
              (example "Normalize from [-1,1] to [0,1]" (norm 0.234 1.0 -1.0))
              (example "Normalize cos() to [0,255]" (norm (cos HALF_PI) -1.0 1.0 0.0 255.0))
              (example "Normalize cos() to [255,0]" (norm (cos HALF_PI) -1.0 1.0 255.0 0.0))]}
  (^double [^double v ^double start ^double stop] ;; norm
   (PrimitiveMath/norm v start stop))
  ([v start1 stop1 start2 stop2] ;; map
   (PrimitiveMath/norm v start1 stop1 start2 stop2)))

(defn make-norm
  "Make [[norm]] function for given range. Resulting function accepts `double` value (with optional target `[dstart,dstop]` range) and returns `double`."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "Make cos() normalizer from [-1.0,1.0] to [0.0, 1.0]." (let [norm-cos (make-norm -1.0 1.0 0.0 1.0)]
                                                                                        (norm-cos (cos 2.0))))
                      (example "Make normalizer from [0,255] to any range." (let [norm-0-255 (make-norm 0 255)]
                                                                              [(norm-0-255 123 -10 -20)
                                                                               (norm-0-255 123 20 10)]))]}
  ([^double start ^double stop]
   (fn ^double [^double v ^double dstart ^double dstop]
     (PrimitiveMath/norm v start stop dstart dstop)))
  ([^double start ^double stop ^double dstart ^double dstop]
   (fn ^double [^double v]
     (PrimitiveMath/norm v start stop dstart dstop))))

(defn cnorm
  "Constrained version of norm. Result of [[norm]] is applied to [[constrain]] to `[0,1]` or `[start2,stop2]` ranges."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example-session "Constrain result of norm." (cnorm 1.5 0 1 100 200) (cnorm 555 200 500))]}
  ([v start1 stop1 start2 stop2]
   (constrain (PrimitiveMath/norm v start1 stop1 start2 stop2) ^double start2 ^double stop2))
  (^double [v start stop]
   (constrain (PrimitiveMath/norm v start stop) 0.0 1.0)))

;;; Interpolation functions

;; Linear interpolation between `start` and `stop`.
(defn lerp
  "Linear interpolation between `start` and `stop` for amount `t`. See also [[mlerp]], [[cos-interpolation]], [[quad-interpolation]] or [[smooth-interpolation]]."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example-session "Examples" (lerp 0.0 1.0 0.123) (lerp 0.0 100.0 0.123) (lerp 100 200 0.5))
              (example "Interpolate outside given range." (lerp -1.0 1.0 1.5))]}
  ^double [^double start ^double stop ^double t]
  (+ start (* t (- stop start))))

(defmacro mlerp
  "[[lerp]] as macro. For inline code. See also [[lerp]], [[cos-interpolation]], [[quad-interpolation]] or [[smooth-interpolation]]."
  {:metadoc/categories #{:conv}}
  [start stop t]
  `(+ ~start (* ~t (- ~stop ~start))))

(add-examples mlerp
  (example-session "Examples" (mlerp 0.0 1.0 0.123) (mlerp 0.0 100.0 0.123) (mlerp 100 200 0.5))
  (example "Interpolate outside given range." (mlerp -1.0 1.0 1.5)))

;; Cosine interpolation between `start` and `stop`
(defn cos-interpolation
  "oF interpolateCosine interpolation. See also [[lerp]]/[[mlerp]], [[quad-interpolation]] or [[smooth-interpolation]]."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "Example" (cos-interpolation 0.0 1.0 0.123))]}
  ^double [^double start ^double stop ^double t]
  (mlerp start stop (* 0.5 (- 1.0 (cos (* t PI))))))

(defn smooth-interpolation
  "Smoothstep based interpolation. See also [[lerp]]/[[mlerp]], [[quad-interpolation]] or [[cos-interpolation]]."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "Example" (smooth-interpolation 0.0 1.0 0.123))]}
  ^double [^double start ^double stop ^double t]
  (mlerp start stop (* t t (- 3.0 (* 2.0 t)))))

(defn quad-interpolation
  "Quad interpolation. See also [[lerp]]/[[mlerp]], [[cos-interpolation]] or [[smooth-interpolation]]."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "Example" (quad-interpolation 0.0 1.0 0.123))]}
  ^double [^double start ^double stop ^double t]
  (mlerp start stop (let [t' (* 2.0 t)]
                      (if (< t' 1.0)
                        (* 0.5 (* t' t'))
                        (* -0.5 (dec (* (dec t') (- t' 3.0))))))))

(defn smoothstep
  "GL [smoothstep](https://www.khronos.org/registry/OpenGL-Refpages/gl4/html/smoothstep.xhtml)."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "x from range." (smoothstep 100 200 120))
                      (example "corner case (< x edge0)" (smoothstep 100 200 50))
                      (example "corner case (> x edge1)" (smoothstep 100 200 250))]}
  ^double [^double edge0 ^double edge1 ^double x]
  (let [t (cnorm x edge0 edge1)]
    (* t t (- 3.0 (* 2.0 t)))))

;;`(wrap 0 -1 1) => 0.0`  
;;`(wrap -1.1 -1 1) => 0.8999999999999999`  
;;`(wrap 1.1 -1 1) => -0.8999999999999999`
(defn wrap
  "Wrap overflowed value into the range, similar to [ofWrap](http://openframeworks.cc/documentation/math/ofMath/#!show_ofWrap)."
  {:metadoc/categories #{:conv}
   :metadoc/examples [(example "Example 1" (wrap 0 -1 1))
                      (example "Example 2 (value outside range)" (wrap -1.1 -1 1.5))
                      (example "Example 3 (reversed range)" (wrap 0.7 0.5 1.0))]}
  ^double [^double start ^double stop ^double value]
  (let [p (> start stop)
        from (if p stop start)
        to (if p start stop)
        cycle (- to from)]
    (if (zero? cycle)
      to
      (->> cycle
           (/ (- value from))
           (floor)
           (* cycle)
           (- value)))))

;; gcd  version
(defn gcd ^long [^long a ^long b]
  "Fast binary GCD (Stein's algorithm)"
  {:metadoc/categories #{:mod}
   :metadoc/examples [(example-session "Usage"
                        (gcd 340 440)
                        (gcd (* 123 5544331) (* 123 123))
                        (gcd -234 -432))]}
  (letfn [(local-gcd ^long [^long a ^long b]
            (cond
              (== a b) a
              (zero? a) b
              (zero? b) a
              (bool-and (even? a) (even? b)) (<< (gcd (>> a 1) (>> b 1)) 1)
              (bool-and (even? a) (odd? b)) (recur (>> a 1) b)
              (bool-and (odd? a) (even? b)) (recur a (>> b 1))
              (bool-and (odd? a) (odd? a)) (if (> a b)
                                             (recur (>> (- a b) 1) b)
                                             (recur (>> (- b a) 1) a))))]
    (local-gcd (iabs a) (iabs b))))

;;

(def ^:const double-array-type (Class/forName "[D"))
(def ^:const double-double-array-type (Class/forName "[[D"))

(def ^{:doc "Convert double array into sequence.

  Alias for `seq`."} double-array->seq seq)

(defn seq->double-array
  "Convert sequence to double-array.
  
  If sequence is double-array do not convert."
  {:metadoc/examples [(example-session "Convert"
                        (seq->double-array [1 2 3])
                        (seq (seq->double-array [1 2 3]))
                        (double-array->seq (seq->double-array [1 2 3])))]}
  ^doubles [vs]
  (if (= (type vs) double-array-type)
    vs
    (double-array vs)))

(declare seq->double-double-array)

(defn double-double-array->seq
  "Convert double array of double arrays into sequence of sequences."
  {:metadoc/examples [(example "Convert" (double-double-array->seq (seq->double-double-array [[4 3 2] (double-array [1 2 3])])))]}
  [res]
  (seq (map seq res)))

(defn seq->double-double-array
  "Convert sequence to double-array of double-arrays.
  
  If sequence is double-array of double-arrays do not convert"
  {:metadoc/examples [(example-session "Convert"
                        (seq->double-double-array [[1 2] [3 4]])
                        (double-double-array->seq (seq->double-double-array [[1 2] [3 4]])))]}
  #^"[[D" [vss]
  (if (= (type vss) double-double-array-type)
    vss
    (into-array (map seq->double-array vss))))

;; ## Copy of primitive math machinery
;;
;; Simplified to be used after `ns` is defined.

(def ^:private vars-to-exclude
  '[* + - / > < >= <= == rem quot mod bit-or bit-and bit-xor bit-not bit-shift-left bit-shift-right unsigned-bit-shift-right inc dec zero? neg? pos? min max even? odd? bool-and bool-or bool-xor bool-not << >> >>> not==])

(defn- using-primitive-operators? []
  (= #'fastmath.core/+ (resolve '+)))

(defn use-primitive-operators
  "Replaces Clojure's arithmetic and number coercion functions with primitive equivalents.  These are
   defined as macros, so they cannot be used as higher-order functions. This is an idempotent operation. Undo with [[unuse-primitive-operators]]."
  []
  (when-not (using-primitive-operators?)
    (doseq [v vars-to-exclude]
      (ns-unmap *ns* v))
    (require (vector 'fastmath.core :refer vars-to-exclude))))

(defn unuse-primitive-operators
  "Undoes the work of [[use-primitive-operators]]. This is idempotent."
  []
  (when (using-primitive-operators?)
    (doseq [v vars-to-exclude]
      (ns-unmap *ns* v))
    (refer 'clojure.core)))

;;;;; Add images

(def single-list `(sin cos tan cot sec csc asin acos atan acot asec acsc
                       sinh cosh tanh coth sech csch asinh acosh atanh acoth asech acsch
                       qsin qcos exp log log10 ln sqrt cbrt qexp qsqrt rqsqrt
                       erf erfc inv-erf inv-erfc sinc log2 qlog
                       sq pow2 pow3 safe-sqrt floor ceil round rint abs iabs trunc
                       frac sfrac low-2-exp high-2-exp round-up-pow2 next-float-up next-float-down
                       signum sgn sigmoid
                       gamma log-gamma dgamma))

(def interp-list `(quad-interpolation smooth-interpolation wrap lerp cos-interpolation))

(def fn-list (concat single-list interp-list))

(defmacro ^:private add-image-examples
  []
  `(do
     ~@(for [x fn-list]
         `(add-examples ~x
            (example-image ~(str "Plot of " (name x)) ~(str "images/m/" (name x) ".png"))))))

(add-image-examples)
