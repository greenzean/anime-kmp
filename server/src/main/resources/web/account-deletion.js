(function () {
  var root = document.getElementById("delete-web");
  var unavailable = document.getElementById("unavailable");
  var confirmBox = document.getElementById("confirm");
  var deleteButton = document.getElementById("delete-button");
  var googleButton = document.getElementById("google-button");
  var status = document.getElementById("status");
  var clientId = root.getAttribute("data-client-id");
  var idToken = null;

  function say(text) {
    status.textContent = text;
  }

  function graphql(query, variables) {
    return fetch("/graphql", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ query: query, variables: variables || {} })
    }).then(function (response) {
      return response.json();
    });
  }

  function onCredential(response) {
    idToken = response.credential;
    confirmBox.hidden = false;
    say("");
    deleteButton.focus();
  }

  function start() {
    graphql("mutation { deleteAccountNonce }")
      .then(function (result) {
        google.accounts.id.initialize({
          client_id: clientId,
          nonce: result.data.deleteAccountNonce,
          callback: onCredential
        });
        google.accounts.id.renderButton(googleButton, { theme: "outline", size: "large", text: "signin_with" });
      })
      .catch(function () {
        say("Could not start Google sign-in. Reload the page and try again.");
      });
  }

  deleteButton.addEventListener("click", function () {
    deleteButton.disabled = true;
    say("Deleting your account...");
    graphql("mutation ($idToken: String!) { deleteAccount(idToken: $idToken) }", { idToken: idToken })
      .then(function (result) {
        if (result.data && result.data.deleteAccount === true) {
          confirmBox.hidden = true;
          googleButton.hidden = true;
          say("Your account and your library on our server were deleted. You can close this page.");
        } else {
          say("Deletion failed. Reload the page and try again.");
        }
      })
      .catch(function () {
        say("Deletion failed. Reload the page and try again.");
      });
  });

  if (!clientId) {
    unavailable.hidden = false;
    return;
  }
  root.hidden = false;
  if (window.google && window.google.accounts) {
    start();
  } else {
    window.onGoogleLibraryLoad = start;
  }
})();
