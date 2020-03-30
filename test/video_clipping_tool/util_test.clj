(ns video-clipping-tool.util-test
  (:require [video-clipping-tool.util :refer :all]
            [clojure.test :refer :all]))

(deftest seconds2time-test
  (testing "Seconds to timestamp"
    (is (= (seconds2time 60)
           {:hour 0
            :minute 1
            :second 0}))
    (is (= (seconds2time 135)
           {:hour 0
            :minute 2
            :second 15}))
    (is (= (seconds2time 14520)
           {:hour 4
            :minute 2
            :second 0}))))

(deftest timestamp-test
  (testing "Parsing"
    (is (= (parse-timestamp "30:20:10")
           {:hour 30
            :minute 20
            :second 10}))
    (is (= (parse-timestamp "09:08:09")
           {:hour 9
            :minute 8
            :second 9})))
  (testing "Parsing identity"
    (is (= (timestamp-to-str (parse-timestamp "30:20:10"))
           "30:20:10")))
  (testing "Addition with overflow"
    (is (= (timestamp-to-str
            (add-timestamps
             (parse-timestamp "99:00:59")
             (parse-timestamp "01:59:01")))
           "101:00:00"))))
