# one-liner to examine data using bb
cat src/fixtures/dog.edn | bb -Sdeps '{:deps {djblue/portal {:mvn/version "0.34.2"}}}' -e "(require '[portal.api :as p]) (def p (p/open)) (add-tap #'p/submit) (tap> *input*) @(promise)"

# Find what languages are being used by plugins in the logseq marketplace repo: https://github.com/logseq/marketplace/
find . -name "manifest.json" \
  | xargs cat | grep \"repo \
  | bb -i '(->> *input* (map #(re-find #"\"(.*?)\".*?\"(.*?)\"" %)) (map last))'
  # ...
# Much easier by manipulating data structure with bb
find . -name "manifest.json" \
  # | head
  | xargs cat | jet -i json -k \
  | bb -I -O '(->> *input* (sort-by :repo) (map :repo)
                   (map #(str "https://api.github.com/repos/" % "/languages"))
                   (map #(assoc {:repo %} 
                     :langs (-> (curl/get % {:raw-args ["-u" "my_client_id:my_client_secret"]}) 
                                :body 
                                (json/parse-string true))))
                   )' 
# Sample output
: '
{:repo "https://api.github.com/repos/benjaffe/logseq-music-notation/languages", :langs {:TypeScript 2206, :HTML 436}}
{:repo "https://api.github.com/repos/ehudhala/logseq-plugin-daily-todo/languages", :langs {:TypeScript 10169, :HTML 330}}
{:repo "https://api.github.com/repos/hkgnp/logseq-readwise-plugin/languages", :langs {:TypeScript 40795, :HTML 368, :JavaScript 178}}
{:repo "https://api.github.com/repos/kerfufflev2/logseq-plugin-blocknav/languages", :langs {:JavaScript 3253, :Shell 507, :HTML 482}}
{:repo "https://api.github.com/repos/pengx17/logseq-plugin-heatmap/languages", :langs {:TypeScript 14328, :CSS 1818, :JavaScript 688, :HTML 368}}
...
# '

# GitHub API rate limite status
# https://docs.github.com/en/rest/overview/resources-in-the-rest-api?apiVersion=2022-11-28#rate-limiting
curl -u my_client_id:my_client_secret -I https://api.github.com/users/rlhk
# Client ID & Secret could be obtained by registering a new GitHub oauth app