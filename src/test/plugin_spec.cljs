(ns plugin-spec
  (:require
    [cljs.test :refer [deftest is are testing]]
    [plugin]))

(def procrastination 
       [{:word "procrastination",
  :phonetic "/pɹəʊˌkɹæs.tɪˈneɪ.ʃən/",
  :phonetics
  [{:text "/pɹəʊˌkɹæs.tɪˈneɪ.ʃən/", :audio ""}
   {:text "/-ʃn̩/",
    :audio
    "https://api.dictionaryapi.dev/media/pronunciations/en/procrastination-us.mp3",
    :sourceUrl
    "https://commons.wikimedia.org/w/index.php?curid=1217796",
    :license
    {:name "BY-SA 3.0",
     :url "https://creativecommons.org/licenses/by-sa/3.0"}}],
  :meanings
  [{:partOfSpeech "noun",
    :definitions
    [{:definition
      "The act of postponing, delaying or putting off, especially habitually or intentionally.",
      :synonyms [],
      :antonyms []}],
    :synonyms ["deferral" "prolongation"],
    :antonyms ["precrastination"]}],
  :license
  {:name "CC BY-SA 3.0",
   :url "https://creativecommons.org/licenses/by-sa/3.0"},
  :sourceUrls ["https://en.wiktionary.org/wiki/procrastination"]}]
)

(def kill-word 
  [{:word "kill",
  :phonetic "/kɪl/",
  :phonetics
  [{:text "/kɪl/",
    :audio
    "https://api.dictionaryapi.dev/media/pronunciations/en/kill-us.mp3",
    :sourceUrl
    "https://commons.wikimedia.org/w/index.php?curid=268482"}],
  :meanings
  [{:partOfSpeech "noun",
    :definitions
    [{:definition "The act of killing.",
      :synonyms [],
      :antonyms [],
      :example
      "The assassin liked to make a clean kill, and thus favored small arms over explosives."}
     {:definition "Specifically, the death blow.",
      :synonyms [],
      :antonyms [],
      :example
      "The hunter delivered the kill with a pistol shot to the head."}
     {:definition "The result of killing; that which has been killed.",
      :synonyms [],
      :antonyms [],
      :example "The fox dragged its kill back to its den."}
     {:definition
      "The grounding of the ball on the opponent's court, winning the rally.",
      :synonyms [],
      :antonyms []}],
    :synonyms [],
    :antonyms []}
   {:partOfSpeech "verb",
    :definitions
    [{:definition "To put to death; to extinguish the life of.",
      :synonyms [],
      :antonyms [],
      :example
      "Smoking kills more people each year than alcohol and drugs combined."}
     {:definition "To render inoperative.",
      :synonyms [],
      :antonyms [],
      :example
      "He killed the engine and turned off the headlights, but remained in the car, waiting."}
     {:definition "To stop, cease or render void; to terminate.",
      :synonyms [],
      :antonyms [],
      :example
      "My computer wouldn't respond until I killed some of the running processes."}
     {:definition "To amaze, exceed, stun or otherwise incapacitate.",
      :synonyms [],
      :antonyms [],
      :example "That joke always kills me."}
     {:definition "To cause great pain, discomfort or distress to.",
      :synonyms [],
      :antonyms [],
      :example "These tight shoes are killing my feet."}
     {:definition
      "To produce feelings of dissatisfaction or revulsion in.",
      :synonyms [],
      :antonyms [],
      :example
      "It kills me to learn how many poor people are practically starving in this country while rich moguls spend such outrageous amounts on useless luxuries."}
     {:definition "To use up or to waste.",
      :synonyms [],
      :antonyms [],
      :example
      "He told the bartender, pointing at the bottle of scotch he planned to consume, \"Leave it, I'm going to kill the bottle.\""}
     {:definition "To exert an overwhelming effect on.",
      :synonyms [],
      :antonyms [],
      :example
      "Between the two of us, we killed the rest of the case of beer."}
     {:definition "To overpower, overwhelm or defeat.",
      :synonyms [],
      :antonyms [],
      :example
      "The team had absolutely killed their traditional rivals, and the local sports bars were raucous with celebrations."}
     {:definition "To force a company out of business.",
      :synonyms [],
      :antonyms []}
     {:definition "To produce intense pain.",
      :synonyms [],
      :antonyms [],
      :example
      "You don't ever want to get rabies. The doctor will have to give you multiple shots and they really kill."}
     {:definition "To punish severely.",
      :synonyms [],
      :antonyms [],
      :example "My parents are going to kill me!"}
     {:definition
      "To strike (a ball, etc.) with such force and placement as to make a shot that is impossible to defend against, usually winning a point.",
      :synonyms [],
      :antonyms []}
     {:definition
      "To cause (a ball, etc.) to be out of play, resulting in a stoppage of gameplay.",
      :synonyms [],
      :antonyms []}
     {:definition "To succeed with an audience, especially in comedy.",
      :synonyms [],
      :antonyms []}
     {:definition "To cause to assume the value zero.",
      :synonyms [],
      :antonyms []}
     {:definition
      "(IRC) To disconnect (a user) involuntarily from the network.",
      :synonyms [],
      :antonyms []}
     {:definition "To deadmelt.", :synonyms [], :antonyms []}],
    :synonyms
    ["annihilate"
     "assassinate"
     "bump off"
     "dispatch"
     "ice"
     "knock off"
     "liquidate"
     "murder"
     "rub out"
     "slaughter"
     "slay"
     "top"
     "whack"
     "break"
     "deactivate"
     "disable"
     "turn off"
     "fritter away"
     "while away"],
    :antonyms []}],
  :license
  {:name "CC BY-SA 3.0",
   :url "https://creativecommons.org/licenses/by-sa/3.0"},
  :sourceUrls ["https://en.wiktionary.org/wiki/kill"]}
 {:word "kill",
  :phonetic "/kɪl/",
  :phonetics
  [{:text "/kɪl/",
    :audio
    "https://api.dictionaryapi.dev/media/pronunciations/en/kill-us.mp3",
    :sourceUrl
    "https://commons.wikimedia.org/w/index.php?curid=268482"}],
  :meanings
  [{:partOfSpeech "noun",
    :definitions
    [{:definition
      "(north-east US) A creek; a body of water; a channel or arm of the sea.",
      :synonyms [],
      :antonyms [],
      :example "Schuylkill, Catskill, etc."}],
    :synonyms [],
    :antonyms []}],
  :license
  {:name "CC BY-SA 3.0",
   :url "https://creativecommons.org/licenses/by-sa/3.0"},
  :sourceUrls ["https://en.wiktionary.org/wiki/kill"]}
 {:word "kill",
  :phonetic "/kɪl/",
  :phonetics
  [{:text "/kɪl/",
    :audio
    "https://api.dictionaryapi.dev/media/pronunciations/en/kill-us.mp3",
    :sourceUrl
    "https://commons.wikimedia.org/w/index.php?curid=268482"}],
  :meanings
  [{:partOfSpeech "noun",
    :definitions [{:definition "A kiln.", :synonyms [], :antonyms []}],
    :synonyms [],
    :antonyms []}],
  :license
  {:name "CC BY-SA 3.0",
   :url "https://creativecommons.org/licenses/by-sa/3.0"},
  :sourceUrls ["https://en.wiktionary.org/wiki/kill"]}]
)

(def subset 
  {:partOfSpeech "noun",
    :definitions
    [{:definition "The act of killing.",
      :synonyms [],
      :antonyms [],
      :example
      "The assassin liked to make a clean kill, and thus favored small arms over explosives."}
     {:definition "Specifically, the death blow.",
      :synonyms [],
      :antonyms [],
      :example
      "The hunter delivered the kill with a pistol shot to the head."}
     {:definition "The result of killing; that which has been killed.",
      :synonyms [],
      :antonyms [],
      :example "The fox dragged its kill back to its den."}
     {:definition
      "The grounding of the ball on the opponent's court, winning the rally.",
      :synonyms [],
      :antonyms []}],
    :synonyms [],
    :antonyms []}
)

(deftest else-and-last
  "Separate the last term from everything else"
  (is (= ["" "world"]
         (plugin/else-and-last "world")))
  (is (= ["" "world"]
         (plugin/else-and-last " world")))
  (is (= ["hello good" "world"]
         (plugin/else-and-last "hello good world")))
  (is (= ["the new    fox is a red" "fox"]
         (plugin/else-and-last "the new    fox is a red fox  ")))
  (is (= [" The fox said:\n\r It's a new \n new" "hell!"]
         (plugin/else-and-last " The fox said:\n\r It's a new \n new hell!"))))

(deftest check-url
  (is (= true (plugin/url? "http://abc.com")))
  (is (= true (plugin/url? "https://www.abc.com/")))
  ; As of 20221020, the following case:
  ; Passed for logseq-plugin-automatic-url-title
  ; Failed for logseq-plugin-link-preview
  (is (= true (plugin/url? "https://blog.polygon.technology/nubank-taps-polygon-supernets-for-nucoin-token-launch-loyalty-program/")))
  (is (= false (plugin/url? "www.abc.com"))))

(deftest formatting
  (is (= "title:: Fox\ndescription:: The brown fox."
         (plugin/edn->logseq-attrs {:title "Fox" :description "The brown fox."}))))

(deftest definitions
       (is (= 7
              7))
       (is (= false
              (plugin/url? "dog")))
       (is (= (plugin/condensed-def procrastination)
              ["noun" "The act of postponing, delaying or putting off, especially habitually or intentionally."]))
       (is (= (plugin/condensed-def kill-word)
              ["noun" "The act of killing."]))
)

(deftest the-whole-thing
;  (is (=  (plugin/unabridged-word (get-in kill-word [0]))
;          "/kɪl/")))
    (is (=  "a" "a"))
    (is (=  (reduce str (plugin/pos-g (get subset :definitions)))
            "something"))
    )