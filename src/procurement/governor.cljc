(ns procurement.governor
  "ProcurementGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  purchase order an advisor may place for a client. The governor
  never dispatches hardware itself and never places a purchase order
  above a client's registered budget-authorization ceiling. Modeled on
  cloud-itonami-isco-4213's pawnbroking.governor. Task twist: a
  proposed purchase order amount is an arithmetic ceiling against the
  client's registered budget-authorization ceiling, and an order
  cannot be placed with an unverified supplier.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance      — the procurement practice client must be
                                registered.
    2. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never places a purchase order above a
                                client's registered budget-authorization
                                ceiling; it only gates what the advisor
                                may place).
    3. supplier basis         — a purchase order proposal must cite a
                                REGISTERED supplier.
    4. budget-ceiling         — the proposed order amount must not
                                exceed the client's registered
                                `:budget-ceiling` (an order beyond the
                                client's registered budget ceiling is
                                unauthorized procurement, not a valid
                                purchase order).
    5. supplier verified      — the supplier must have
                                `:supplier-verified?` true before any
                                purchase order can be placed (offering a
                                purchase order from an unverified supplier
                                is an unauthorized supplier relationship,
                                not a verified procurement).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-budget-order (no purchase order above the
                                client's registered budget-authorization
                                ceiling without the governor gate).
    7. :op :approve-unverified-supplier-onboarding (supplier
                                verification required before any new
                                purchase order, with human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [procurement.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-budget-order
                                     :approve-unverified-supplier-onboarding})

(defn- hard-violations [{:keys [request proposal]} client-record supplier-record]
  (let [{:keys [op order-amount]} proposal
        order? (= :place-order op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録予算天井を超える発注を直接実行しない）"})

      (and order? (nil? supplier-record))
      (conj {:rule :unknown-supplier :detail "未登録 supplier への発注提案は不可"})

      (and order? supplier-record (not= (:client-id supplier-record) (:client-id request)))
      (conj {:rule :supplier-wrong-client :detail "supplier が別 client のもの"})

      (and order? client-record (number? order-amount) (> order-amount (:budget-ceiling client-record)))
      (conj {:rule :order-exceeds-budget-ceiling
             :detail (str "発注額 " order-amount " > 登録済み予算天井 "
                          (:budget-ceiling client-record) "（登録済み予算天井を超える発注は認可外の調達であって有効な発注ではない）")})

      (and order? supplier-record (not (:supplier-verified? supplier-record)))
      (conj {:rule :supplier-not-verified
             :detail "未検証の supplier への発注提案は認可外の調達先関係であって認可調達ではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `procurement.store/Store`. Pure — never mutates
  the store, never places a purchase order above a client's registered
  budget-authorization ceiling."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        supplier-record (some->> (:supplier-id proposal) (store/supplier store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record supplier-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
