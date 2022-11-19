(ns word-spec
  (:require
    [cljs.test :refer [deftest is are testing]]
    [word :as w]))

(def sample-word-1
  [{:word "dog",
    :phonetic "/dɑɡ/",
    :phonetics
    [{:text "/dɑɡ/", :audio ""}
     {:text "/dɒɡ/",
      :audio
      "https://api.dictionaryapi.dev/media/pronunciations/en/dog-uk.mp3",
      :sourceUrl
      "https://commons.wikimedia.org/w/index.php?curid=9014185",
      :license
      {:name "BY 3.0 US",
       :url "https://creativecommons.org/licenses/by/3.0/us"}}
     {:text "/dɔɡ/",
      :audio
      "https://api.dictionaryapi.dev/media/pronunciations/en/dog-us.mp3",
      :sourceUrl
      "https://commons.wikimedia.org/w/index.php?curid=631204",
      :license
      {:name "BY-SA 3.0",
       :url "https://creativecommons.org/licenses/by-sa/3.0"}}
     {:text "/dɑɡ/", :audio ""}],
    :meanings
    [{:partOfSpeech "noun",
      :definitions
      [{:definition
        "A mammal, Canis familiaris or Canis lupus familiaris, that has been domesticated for thousands of years, of highly variable appearance due to human breeding.",
        :synonyms [],
        :antonyms [],
        :example "The dog barked all night long."}
       {:definition
        "Any member of the Family Canidae, including domestic dogs, wolves, coyotes, jackals, foxes, and their relatives (extant and extinct); canid.",
        :synonyms [],
        :antonyms []}
       {:definition
        "A male dog, wolf or fox, as opposed to a bitch or vixen.",
        :synonyms [],
        :antonyms []}]
      :synonyms
      ["Canis canis"
       "Canis domesticus"
       "Canis familiaris"
       "canine"
       "domestic dog"
       "hound"
       "sire"
       "stud"
       "bloke"
       "chap"
       "dude"
       "fellow"
       "guy"
       "man"
       "click"
       "detent"
       "pawl"
       "andiron"
       "dogiron"
       "firedog"
       "blackguard"
       "bounder"
       "cad"
       "fool"
       "heel"
       "hound"
       "scoundrel"],
      :antonyms []}
     {:partOfSpeech "verb",
      :definitions
      [{:definition "To pursue with the intent to catch.",
        :synonyms [],
        :antonyms []}
       {:definition "To follow in an annoying or harassing way.",
        :synonyms [],
        :antonyms [],
        :example
        "The woman cursed him so that trouble would dog his every step."}
       {:definition "To fasten a hatch securely.",
        :synonyms [],
        :antonyms [],
        :example "It is very important to dog down these hatches..."}
       {:definition
        "To watch, or participate, in sexual activity in a public place.",
        :synonyms [],
        :antonyms [],
        :example "I admit that I like to dog at my local country park."}
       {:definition
        "To intentionally restrict one's productivity as employee; to work at the slowest rate that goes unpunished.",
        :synonyms [],
        :antonyms [],
        :example
        "A surprise inspection of the night shift found that some workers were dogging it."}
       {:definition "To criticize.", :synonyms [], :antonyms []}
       {:definition "To divide (a watch) with a comrade.",
        :synonyms [],
        :antonyms []}],
      :synonyms
      ["chase"
       "chase after"
       "go after"
       "pursue"
       "tag"
       "tail"
       "track"
       "trail"
       "goldbrick"
       "soldier"],
      :antonyms []}
     {:partOfSpeech "noun",
      :definitions
      [{:definition "Meat from a dog eaten as food.",
        :synonyms [],
        :antonyms [],
        :example
        "We visited South Korea this time around, where we ate dog meat for the first time."}
       {:definition "Meat prepared to be given to a dog as food.",
        :synonyms [],
        :antonyms []}
       {:definition
        "An insult intended to assert hyperbolically that another person has value only as a corpse to be fed to a dog.",
        :synonyms [],
        :antonyms [],
        :example
        "Did you just step on my blue suede shoes? You're dog meat now!"}],
      :synonyms ["dog" "dog food" "fragrant meat"],
      :antonyms []}],
    :license
    {:name "CC BY-SA 3.0",
     :url "https://creativecommons.org/licenses/by-sa/3.0"},
    :sourceUrls
    ["https://en.wiktionary.org/wiki/dog"
     "https://en.wiktionary.org/wiki/dog%20meat"]}])

(def sample-phonetics-1
  (->> sample-word-1
       first
       :phonetics))

(def sample-part-of-speech-1
  (->> sample-word-1
       first
       :meanings
       first))

(def sample-word-2
  [{:word "prodigy",
    :phonetic "/ˈpɹɒdɪdʒi/",
    :phonetics
    [{:text "/ˈpɹɒdɪdʒi/", :audio ""} {:text "/ˈpɹɑdɪdʒi/", :audio ""}],
    :meanings
    [{:partOfSpeech "noun",
      :definitions
      [{:definition "An extraordinary thing seen as an omen; a portent.",
        :synonyms [],
        :antonyms []}
       {:definition
        "An extraordinary occurrence or creature; an anomaly, especially a monster; a freak.",
        :synonyms [],
        :antonyms []}
       {:definition "An amazing or marvellous thing; a wonder.",
        :synonyms [],
        :antonyms []}
       {:definition "A wonderful example of something.",
        :synonyms [],
        :antonyms []}
       {:definition "An extremely talented person, especially a child.",
        :synonyms [],
        :antonyms []}],
      :synonyms ["boy wonder" "child prodigy" "wunderkind"],
      :antonyms []}],
    :license
    {:name "CC BY-SA 3.0",
     :url "https://creativecommons.org/licenses/by-sa/3.0"},
    :sourceUrls ["https://en.wiktionary.org/wiki/prodigy"]}])

(def sample-phonetics-2
  (->> sample-word-2
       first
       :phonetics))

(deftest format-phonetics
  (is (= "[/dɒɡ/](https://api.dictionaryapi.dev/media/pronunciations/en/dog-uk.mp3)"
         (w/fmt-phonetic (second sample-phonetics-1))))
  (is (= "**1.** /dɑɡ/ **2.** [/dɒɡ/](https://api.dictionaryapi.dev/media/pronunciations/en/dog-uk.mp3) **3.** [/dɔɡ/](https://api.dictionaryapi.dev/media/pronunciations/en/dog-us.mp3) **4.** /dɑɡ/"
         (w/fmt-phonetics sample-phonetics-1)))
  (is (= "**1.** /ˈpɹɒdɪdʒi/ **2.** /ˈpɹɑdɪdʒi/"
         (w/fmt-phonetics sample-phonetics-2))))

(deftest format-part-of-speech
  (is (= "【noun】 **1.** A mammal, Canis familiaris or Canis lupus familiaris, that has been domesticated for thousands of years, of highly variable appearance due to human breeding. **2.** Any member of the Family Canidae, including domestic dogs, wolves, coyotes, jackals, foxes, and their relatives (extant and extinct); canid. **3.** A male dog, wolf or fox, as opposed to a bitch or vixen."
         (w/fmt-part-of-speech sample-part-of-speech-1)))
  (is (= "**1.** /ˈpɹɒdɪdʒi/ **2.** /ˈpɹɑdɪdʒi/; 【noun】 **1.** An extraordinary thing seen as an omen; a portent. **2.** An extraordinary occurrence or creature; an anomaly, especially a monster; a freak. **3.** An amazing or marvellous thing; a wonder. **4.** A wonderful example of something. **5.** An extremely talented person, especially a child."
         (w/compact-word-def sample-word-2))))
