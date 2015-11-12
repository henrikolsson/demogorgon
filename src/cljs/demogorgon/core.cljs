(ns demogorgon.core
  (:require [reagent.core :as r]
            [ajax.core :refer [GET POST]]
            [secretary.core :as secretary :refer-macros [defroute]]))

(defroute "/users/:id" {:as params}
  (js/console.log (str "User: " (:id params))))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(def data
  (r/atom []))

(def sorted-by (r/atom nil))

(defn sort-data [key]
  (if (= @sorted-by key)
    (do (reset! data (reverse @data)))
    (do (reset! sorted-by key)
        (reset! data (sort-by #(get %1 key) @data)))))

(defn list-component []
  (GET "/api/last-games"
       {:keywords? true
        :handler (fn [response] (reset! data response))
        :error-handler error-handler})
  (fn []
    [:table
     [:tr
      (map (fn [key]  
             [:th {:key (name key)} [:a {:href "#" :on-click #(sort-data key)} (name key)]])
           (keys (first @data)))]
     (map (fn [item]
            [:tr {:key (get item "key")}
             (map (fn [entry]
                    [:td {:key (first entry)} (second entry)])
                  item)])
          @data)]))

(defn render []
  (r/render-component [list-component]
                      (.-body js/document)))

(defn init []
  (enable-console-print!)
  (println "hello!")
  (render))

(init)
