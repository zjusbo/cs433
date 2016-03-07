

qx.Class.define("thinmail.ServiceClient", {
  
  extend : qx.core.Object,
  
  
  
  members: {
  
    login: function(username, password, callback) {
        this.debug("remote call service/user/login (username=" + username + ")");
    	var req = new qx.io.remote.Request("service/user/login", "POST", qx.util.Mime.JSON);
    	req.setParameter("username", username);
    	req.setParameter("password", password);
   	
/*        req.addEventListener("completed", function(e) {
		    var response = e.getContent();

    	    if (response.status.code == "OK") {
				callback.onLoginPassed(response.login.sessionid);    	    	
	    	} else {
				callback.onFailed("login failed (" + response + ")");
	    	}
 		}, this);
*/

		req.setAsynchronous(true);
		try {
			req.send();
		} catch(e) {
			callback.onFailed("technical failure (" + e.message + ")")
		}
    },


    userData : function(sessionId) {
        this.debug("remote call service/user/data (sessionId=" + sessionId + ")");
		var req = new qx.io.remote.Request("service/user/data", "POST", qx.util.Mime.JSON);
    	req.setParameter("si", sessionId);
    	
    	var userData = null;
    	var responseMsg = "technical failure";
    	
        req.addEventListener("completed", function(e) {
		    var response = e.getContent();
		    
    	    responseMsg = response.status.code;
    	    
    	    if (responseMsg == "OK") {
				userData = response.userdata;
	    	}    	    
 		}, this);

		req.setAsynchronous(false);
		req.send();
    	
    	if (sessionId == null) {
    		throw new Error("user data request failed (" + responseMsg + ")");
    	} else {
	    	return userData;
	    }
    },    
    
    folderList : function(sessionId) {
        this.debug("remote call service/user/folders (sessionId=" + sessionId + ")");
		var req = new qx.io.remote.Request("service/user/folders", "POST", qx.util.Mime.JSON);
    	req.setParameter("si", sessionId);
    	
    	var folderList = null;
    	var responseMsg = "technical failure";
    	
        req.addEventListener("completed", function(e) {
		    var response = e.getContent();
		    
    	    responseMsg = response.status.code;
    	    
    	    if (responseMsg == "OK") {
				folderList = response.folders;
	    	}    	    
 		}, this);

		req.setAsynchronous(false);
		req.send();
    	
    	if (sessionId == null) {
    		throw new Error("folders request failed (" + responseMsg + ")");
    	} else {
	    	return folderList;
	    }
    }
  }
});