(ns procurement.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [procurement.actor :as actor]
            [procurement.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Procurement Co"
                                :budget-ceiling 10000})
    (store/register-supplier! st {:supplier-id "S-1" :client-id "client-1"
                                  :name "supplier-042"
                                  :supplier-verified? true})
    st))

(deftest commits-a-within-budget-verified-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :place-order :stake :low
                 :supplier-id "S-1" :order-amount 5000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-budget-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :place-order :stake :low
                 :supplier-id "S-1" :order-amount 50000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-budget-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-budget-order :stake :low
                 :supplier-id "S-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
