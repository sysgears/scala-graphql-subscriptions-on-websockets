new EventSource("http://localhost:9000/graphql/subscribe?query=subscription%20Posts%7B%0A%20%20postsUpdated%7B%0A%20%20%20%20id%0A%20%20%20%20title%0A%20%20%20%20content%0A%20%20%7D%0A%7D");
source.onmessage = function(e) {
    console.log(e)
};