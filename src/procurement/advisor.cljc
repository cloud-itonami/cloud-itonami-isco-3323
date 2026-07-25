(ns procurement.advisor
  "Procurement Advisor — the advisor named in this repository's
  README, proposing a procurement operation (place an order, approve an
  over-budget order, approve an unverified supplier onboarding) from
  a supplier request and budget authorization. Swappable
  mock/llm; the advisor ONLY proposes — `procurement.governor` checks
  the budget-ceiling and supplier verification independently
  and always escalates over-budget-order and unverified-supplier-
  onboarding decisions. Modeled on cloud-itonami-isco-4213's advisor.

  A proposal: {:op :place-order|:approve-over-budget-order|:approve-unverified-supplier-onboarding
               :effect :propose :supplier-id str :order-amount number
               :stake kw :confidence n :rationale str}"
  ;; clojure.edn, not clojure.core/read-string: this parses untrusted
  ;; advisor output, and the core reader executes #=(...) at read time.
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake supplier-id order-amount] :as request}]
  {:op op
   :effect :propose
   :supplier-id supplier-id
   :order-amount order-amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a procurement advisor. Given a request, propose an :op, the
   :supplier-id and :order-amount, an honest :confidence and a :stake.
   Never propose an order amount beyond the client's registered budget
   ceiling, or an order from an unverified supplier — the governor
   checks both against the registered supplier record. Over-budget
   orders and unverified supplier onboarding always require human
   sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
