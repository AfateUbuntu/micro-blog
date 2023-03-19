(ns guestbook.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as dom]
   [re-frame.core :as rf]

   [reitit.coercion.spec :as reitit-spec]
   [reitit.frontend :as rtf]
   [reitit.frontend.easy :as rtfe]

   [clojure.string :as string]

   [guestbook.routes.app :refer [app-routes]]
   [guestbook.websockets :as ws]
   [guestbook.auth :as auth]
   [guestbook.messages :as messages]
   [guestbook.ajax :as ajax]

   [mount.core :as mount]))
;; (-> (.getElementsByClassName js/document "content")
;;     first
;;     (.-innerHTML)
;;     (set! (str "Hello, Auto!" (-> (.getElementsByClassName js/document "content")
;;                                   first
;;                                   (.-innerHTML)))))

(rf/reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db {:messages/loading? true
         :session/loading? true}
    :dispatch-n [[:session/load] [:messages/load]]}))

(def router
  (rtf/router
   (app-routes)
   {:data {:coercion reitit-spec/coercion}}))

(rf/reg-event-db
 :router/navigated
 (fn [db [_ new-match]]
   (assoc db :router/current-route new-match)))

(rf/reg-sub
 :router/current-route
 (fn [db]
   (:router/current-route db)))

(defn init-routes! []
  (rtfe/start!
   router
   (fn [new-match]
     (when new-match
       (rf/dispatch [:router/navigated new-match])))
   {:use-fragment false}))

;; (defn navbar []
;;   (let [user-state @(rf/subscribe [:auth/user-state])]
;;     [:nav.navbar.is-primary
;;      [:div.navbar-brand
;;       [:a.navbar-item href= "#/" "Guestbook"]]
;;      [:div.navbar-menu
;;       [:div.navbar-start
;;        [:a.navbar-item href= "#/messages" "Messages"]
;;        [:a.navbar-item href= "#/about" "About"]]
;;       [:div.navbar-end
;;        (case user-state
;;          :loading [:a.navbar-item "Loading..."]
;;          :authenticated [:a.navbar-item href= "#/logout" "Logout"]
;;          :anonymous [:a.navbar-item href= "#/login" "Login"])]]]))

(defn navbar []
  (let [burger-active (r/atom false)]
    (fn []
      [:nav.navbar.is-info
       [:div.container
        [:div.navbar-brand
         [:a.navbar-item {:href "/" :style {:font-weight "bold"}} "guestbook"]
         [:span.navbar-burger.burger {:data-target "nav-menu"
                                      :on-click #(swap! burger-active not)
                                      :class (when @burger-active "is-active")}
          [:span]
          [:span]
          [:span]]]
        [:div#nav-menu.navbar-menu
         {:class (when @burger-active "is-active")}
         [:div.navbar-start
          [:a.navbar-item
           {:href "/"}
           "Home"]]
         [:div.navbar-end
          [:div.navbar-item
           (case @(rf/subscribe [:auth/user-state])
             :loading
             [:div {:style {:width "5em"}}
              [:progress.progress.is-dark.is-small {:max 100} "30%"]]
             :authenticated
             [:div.buttons
              [auth/nameplate @(rf/subscribe [:auth/user])]
              [auth/logout-button]]
             :anonymous
             [:div.buttons
              [auth/login-button]
              [auth/register-button]])]]]]])))

(defn page [{{:keys [view name]} :data path	:path :as	match}]
  [:section.section>div.container
   (if view
     [view match]
     [:div "No view specified for route: " name " (" path ")"])])

(defn app []
  (let [current-route @(rf/subscribe [:router/current-route])]
    [:div.app
     [navbar]
     [page current-route]]))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting Components...")
  (init-routes!)
  (dom/render [#'app] (.getElementById js/document "content"))
  (.log js/console "Components Mounted!"))

(defn init! []
  (.log js/console "Initializing App...")
  (mount/start)
  (rf/dispatch [:app/initialize])
  (mount-components))
(.log js/console "guestbook.core evaluated!")






