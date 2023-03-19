(ns guestbook.messages
  (:require
   [clojure.string :as string]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [guestbook.validation :refer [validate-message]]
   [guestbook.components :refer [text-input textarea-input]]))
;; All code is copied in from guestbook.core
(rf/reg-event-fx
 :messages/load
 (fn [{:keys [db]} _]
   {:db (assoc db
               :messages/loading? true
               :messages/list nil
               :messages/filter nil)
    :ajax/get {:url "/api/messages"
               :success-path [:messages]
               :success-event [:messages/set]}}))
(rf/reg-event-fx
 :messages/load-by-author
 (fn [{:keys [db]} [_ author]]
   {:db (-> db
            (assoc :messages/loading? true
                   :messages/filter {:author author}
                   :messages/list nil))
    :ajax/get {:url (str "/api/messages/by/" author)
               :success-path [:messages]
               :success-event [:messages/set]}}))


;; (rf/reg-event-fx
;;  :messages/load
;;  (fn [{:keys [db]} _]
;;    {:db (assoc db :messages/loading? true)
;;     :ajax/get {:url "/api/messages"
;;                :success-path [:messages]
;;                :success-event [:messages/set]}}))
;; (rf/reg-event-fx
;;  :messages/load-by-author
;;  (fn [{:keys [db]} [_ author]]
;;    {:db (assoc db :messages/loading? true)
;;     :ajax/get {:url (str "/api/messages?author=" author)
;;                :success-path [:messages]
;;                :success-event [:messages/set]}}))
(rf/reg-event-db
 :messages/set
 (fn [db [_ messages]]
   (-> db
       (assoc :messages/loading? false
              :messages/list messages))))
(rf/reg-sub :messages/loading?
            (fn [db _]
              (:messages/loading? db)))
(rf/reg-sub
 :messages/list
 (fn [db _]
   (:messages/list db [])))





(defn reload-messages-button []  ;; Copied from guestbook.core...
  (let [loading? (rf/subscribe [:messages/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?} (if @loading?
                             "Loading Messages"
                             "Refresh Messages")]))
(defn message-list-placeholder []
  [:ul.messages
   [:li
    [:p "Loading Messages..."]
    [:div {:style {:width "10em"}}
     [:progress.progress.is-dark {:max 100} "30%"]]]])


(defn message-list [messages] ;; Copied from guestbook.core...
  [:ul.messages
   (for [{:keys [timestamp message name author]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name
      ;; Add the author (e.g. <@username>)
       " <"
       (if author
         [:a {:href (str "/user/" author)} (str "@" author)]
         [:span.is-italic "account not found"])
       ">"]])])
(defn add-message? [filter-map msg]
  (every?
   (fn [[k matcher]]
     (let [v (get msg k)]
       (cond
         (set? matcher)
         (matcher v)
         (fn? matcher)
         (matcher v)
         :else
         (= matcher v)))) filter-map))
(rf/reg-event-db
 :message/add
 (fn [db [_ message]]
   (if (add-message? (:messages/filter db) message) (update db :messages/list conj message) db)))


;; (rf/reg-event-db
;;  :message/add
;;  (fn [db [_ message]]
;;    (update db :messages/list conj message)))
(rf/reg-event-db
 :form/set-field
 [(rf/path :form/fields)]
 (fn [fields [_ id value]]
   (assoc fields id value)))
(rf/reg-event-db
 :form/clear-fields
 [(rf/path :form/fields)]
 (fn [_ _]
   {}))
(rf/reg-sub
 :form/fields
 (fn [db _]
   (:form/fields db)))
(rf/reg-sub
 :form/field
 :<- [:form/fields]
 (fn [fields [_ id]]
   (get fields id)))
(rf/reg-event-db
 :form/set-server-errors
 [(rf/path :form/server-errors)]
 (fn [_ [_ errors]] errors))
(rf/reg-sub
 :form/server-errors
 (fn [db _]
   (:form/server-errors db)))
(rf/reg-sub
 :form/validation-errors
 :<- [:form/fields]
 (fn [fields _]
   (validate-message fields)))
(rf/reg-sub :form/validation-errors?
            :<- [:form/validation-errors]
            (fn [errors _]
              (not (empty? errors))))
(rf/reg-sub
 :form/errors
 :<- [:form/validation-errors]
 :<- [:form/server-errors]
 (fn [[validation server] _]
   (merge validation server)))
(rf/reg-sub
 :form/error
 :<- [:form/errors]
 (fn [errors [_ id]]
   (get errors id)))
(rf/reg-event-fx
 :message/send!-called-back
 (fn [_ [_ {:keys [success errors]}]] (if success
                                        {:dispatch [:form/clear-fields]}
                                        {:dispatch [:form/set-server-errors errors]})))
(rf/reg-event-fx :message/send!
                 (fn [{:keys [db]} [_ fields]]
                   {:db (dissoc db :form/server-errors)
                    :ws/send! {:message [:message/create! fields]
                               :timeout 10000
                               :callback-event [:message/send!-called-back]}}))
(defn errors-component [id & [message]]
  (when-let [error @(rf/subscribe [:form/error id])] [:div.notification.is-danger (if message
                                                                                    message
                                                                                    (string/join error))]))
;; (defn text-input [{val :value attrs :attrs :keys [on-save]}] ;; Copied from guestbook.core...
;;   (let [draft (r/atom nil) value (r/track #(or @draft @val ""))]
;;     (fn []
;;       [:input.input (merge attrs {:type :text
;;                                   :on-focus #(reset! draft (or @val ""))
;;                                   :on-blur (fn []
;;                                              (on-save (or @draft ""))
;;                                              (reset! draft nil))
;;                                   :on-change #(reset! draft (.. % -target -value))
;;                                   :value @value})])))
;; (defn textarea-input [{val :value attrs :attrs :keys [on-save]}] ;; Copied from guestbook.core...
;;   (let [draft (r/atom nil) value (r/track #(or @draft @val ""))]
;;     (fn []
;;       [:textarea.textarea (merge attrs
;;                                  {:on-focus #(reset! draft (or @val ""))
;;                                   :on-blur (fn []
;;                                              (on-save (or @draft ""))
;;                                              (reset! draft nil))
;;                                   :on-change #(reset! draft (.. % -target -value))
;;                                   :value @value})])))
(defn message-form []
;; Copied from guestbook.core...
  [:div
   [errors-component :server-error]
   [errors-component :unauthorized "Please log in before posting."]
   [:div.field
    [:label.label {:for :name} "Name"]
    [errors-component :name]
    [text-input {:attrs {:name :name}
                 :value (rf/subscribe [:form/field :name])
                 :on-save #(rf/dispatch [:form/set-field :name %])}]]
   [:div.field
    [:label.label {:for :message} "Message"]
    [errors-component :message]
    [textarea-input
     {:attrs {:name :message}
      :value (rf/subscribe [:form/field :message]) :on-save #(rf/dispatch [:form/set-field :message %])}]]
   [:input.button.is-primary
    {:type :submit
     :disabled @(rf/subscribe [:form/validation-errors?])
     :on-click #(rf/dispatch [:message/send! @(rf/subscribe [:form/fields])])
     :value "comment"}]])
