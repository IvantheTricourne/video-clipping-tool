(ns video-clipping-tool.core-test
  (:require [clojure.test :refer :all]
            [video-clipping-tool.core :refer :all]
            [clojure.java.io :as io]))

(deftest make-clip-range-test
  (testing "Parsing clip with direct times"
    (is (= (make-clip-range "00:00:00 00:00:30")
           ["00:00:00" "00:00:30"])))
  (testing "Parsing clip with operations"
    ;; special addition
    (is (= (make-clip-range "00:00:00 + 00:00:30")
           ["00:00:00" "00:00:30"]))
    (is (= (make-clip-range "00:00:30 + 00:00:30")
           ["00:00:30" "00:01:00"]))
    ;; everything else
    (is (= (make-clip-range "00:00:30 - 00:01:00")
           ["00:00:30" "00:01:00"]))
    (is (= (make-clip-range "00:00:30 to 00:01:00")
           ["00:00:30" "00:01:00"]))))

(deftest read-clip-file-test
  (testing "Parsing clip file"
    (is (= (read-clip-file (io/file (io/resource "test.clips")))
           {0 ["00:00:00" "00:00:05"]
            1 ["00:00:06" "00:00:10"]}))))

(deftest generate-clip-command-test
  (testing "Generating clip commands"
    (is (= (generate-clip-command
            (io/file "/tmp")
            (io/file "/tmp")
            "00:00:00"
            "00:00:05")
           ["ffmpeg"
            "-i"
            "/tmp"
            "-ss"
            "00:00:00"
            "-to"
            "00:00:05"
            "-async"
            "1"
            "-strict"
            "2"
            "/tmp"]))))

(deftest generate-combine-command-test
  (testing "Generating combine command"
    (is (= (generate-combine-command (io/file "/tmp") (io/file "/tmp"))
           ["ffmpeg" "-f" "concat" "-safe" "0" "-i" "/tmp" "-c" "copy" "/tmp"]))))

(deftest generate-clip-test
  (testing "Generating clip entry"
    (is (= (generate-clip 0 "00:00:00" "00:00:05"
                          (io/file "/tmp")
                          (io/file "/tmp"))
           {:cmd ["ffmpeg"
                  "-i"
                  "/tmp"
                  "-ss"
                  "00:00:00"
                  "-to"
                  "00:00:05"
                  "-async"
                  "1"
                  "-strict"
                  "2"
                  "/tmp/clip-0.mp4"]
            :start "00:00:00"
            :end "00:00:05"}))))

(deftest generate-clip-index-test
  (testing "Generating clip index"
    (is (= (generate-clip-index (read-clip-file (io/file (io/resource "test.clips")))
                                (io/file "tmp")
                                (io/file "tmp"))
           {0
            {:cmd
             ["ffmpeg"
              "-i"
              "tmp"
              "-ss"
              "00:00:00"
              "-to"
              "00:00:05"
              "-async"
              "1"
              "-strict"
              "2"
              "tmp/clip-0.mp4"],
             :start "00:00:00",
             :end "00:00:05"},
            1
            {:cmd
             ["ffmpeg"
              "-i"
              "tmp"
              "-ss"
              "00:00:06"
              "-to"
              "00:00:10"
              "-async"
              "1"
              "-strict"
              "2"
              "tmp/clip-1.mp4"],
             :start "00:00:06",
             :end "00:00:10"}}))))

(deftest generate-clip-order-test
  (testing "Gnerating clip order"
    (is (= (generate-clip-order
            (generate-clip-index (read-clip-file (io/file (io/resource "test.clips")))
                                 (io/file "tmp")
                                 (io/file "tmp")))
           "file 'tmp/clip-0.mp4'\nfile 'tmp/clip-1.mp4'"))))
