

 qx.Class.define("thinmail.ThinMail",  {
  extend : qx.application.Gui,


  members : {
  	
  	__serviceClient : null,

  	
  	main : function(e) {

		// Call super class
		this.base(arguments);

		this.__serviceClient = new thinmail.ServiceClient;
		     
		var loginWidget = new thinmail.LoginWidget(this.__serviceClient, this);
		loginWidget.addToDocument();
  	},
  	
  	
  	_onLoginFaild : function(errormessage) {
  		
  	},
  	
  	onLoginPassed : function(sessionId) {
  		alert(sessionId);
  	},
  	
  	
  	onLoginFailed : function(message) {
  		alert(message);
  	}
  } 
});
  