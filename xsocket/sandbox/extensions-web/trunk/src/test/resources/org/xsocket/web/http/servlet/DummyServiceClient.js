

qx.Class.define("thinmail.DummyServiceClient", {
  
  extend : qx.core.Object,
  
    
  members: {
  
    login: function(username, password) {
		return "45645646466456";
    },


    userData : function(sessionId) {
		var dummyJsonResponse = "{\"email\" : \"testi@web.de\"}";
		var response = eval('(' + dummyJsonResponse + ')');
		return response;
    },    
    
    folders : function(sessionId) {
    	
		var dummyJsonResponse = "[{\"name\":\"inbox\",\"read\":5,\"size\":5}, {\"name\":\"spam\",\"read\":1,\"size\":33}]";
		var response = eval('(' + dummyJsonResponse + ')');
		return response;
    },
    
    
    folderMails : function(sessionId, foldername) {
    	
		var dummyJsonResponse = "[{\"from\":\"unk@erwer\",\"size\":454}, {\"from\":\"unk2@erwer\",\"size\":454}, {\"from\":\"unk3@erwer\",\"size\":454}]";
		var response = eval('(' + dummyJsonResponse + ')');
		return response;
    }
  }
});