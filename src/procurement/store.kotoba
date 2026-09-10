(ns procurement.store
  "SSoT for the ISCO-08 3323 independent procurement & sourcing
  practice actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a receiving-dock intake
  and inventory-tagging robot manages incoming shipments under this
  advisor/governor pair, which never dispatches hardware itself and
  never places a purchase order above a client's registered budget-
  authorization ceiling).
  Modeled on cloud-itonami-isco-4213's pawnbroking.store.

  Domain:

    client   — a registered procurement practice client
               (:client-id, :name, :budget-ceiling number).
               `:budget-ceiling` is the registered maximum purchase
               authorization a proposed order amount must not exceed — an
               order beyond the client's registered budget ceiling is
               unauthorized procurement, not a valid purchase order.
    supplier — a registered supplier {:supplier-id :name
               :supplier-verified? boolean}. `:supplier-verified?`
               records whether the supplier has been formally verified
               for procurement — offering a purchase order from an
               unverified supplier is an unauthorized supplier
               relationship, not a verified procurement.
    record   — a committed operating record (a placed purchase order) —
               written ONLY via commit-record!.
    ledger   — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (supplier [s supplier-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-supplier! [s supplier])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (supplier [_ supplier-id] (get-in @a [:suppliers supplier-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-supplier! [s supplier]
    (swap! a assoc-in [:suppliers (:supplier-id supplier)] supplier) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :suppliers {} :records [] :ledger []}
                                   seed)))))
