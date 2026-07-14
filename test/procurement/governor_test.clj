(ns procurement.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [procurement.store :as store]
            [procurement.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Procurement Co"
                                :budget-ceiling 10000})
    (store/register-supplier! st {:supplier-id "S-1" :client-id "client-1"
                                  :name "supplier-042"
                                  :supplier-verified? true})
    st))

(defn- place-op [amount]
  {:op :place-order :effect :propose :supplier-id "S-1"
   :order-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-budget-and-verified
  (let [st (fresh-store)
        v (governor/check req {} (place-op 5000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-budget-boundary
  (testing "the budget-ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (place-op 10000) st)]
      (is (:ok? v)))))

(deftest hard-on-order-exceeds-budget-ceiling
  (testing "an order beyond the client's registered budget ceiling is unauthorized procurement, not a valid purchase order"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (place-op 50000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :order-exceeds-budget-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-supplier-not-verified
  (testing "offering a purchase order from an unverified supplier is an unauthorized supplier relationship, not a verified procurement"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Procurement Co"
                                  :budget-ceiling 10000})
      (store/register-supplier! st {:supplier-id "S-1" :client-id "client-1"
                                    :name "supplier-042"
                                    :supplier-verified? false})
      (let [v (governor/check req {} (assoc (place-op 5000) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :supplier-not-verified (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-supplier
  (let [st (fresh-store)
        v (governor/check req {} (assoc (place-op 5000) :supplier-id "S-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-supplier (:rule %)) (:violations v)))))

(deftest hard-on-foreign-supplier
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"
                                :budget-ceiling 5000})
    (let [v (governor/check {:client-id "client-2"} {} (place-op 5000) st)]
      (is (:hard? v))
      (is (some #(= :supplier-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (place-op 5000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (place-op 5000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-budget-order-even-at-high-confidence
  (testing "no purchase order above the client's registered budget-authorization ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-budget-order :effect :propose
                                    :supplier-id "S-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-unverified-supplier-onboarding-even-at-high-confidence
  (testing "supplier verification required before any new purchase order, with human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-unverified-supplier-onboarding :effect :propose
                                    :supplier-id "S-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (place-op 5000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
