

qx.Class.define("thinmail.FolderOverview", {
  extend : qx.core.Object,
  

  construct : function(sessionId, service) {
  	this.sessionId = sessionId;

    this._service = service;
    
	this._timer = new qx.client.Timer(30 * 1000);
    this._timer.addEventListener("interval", this._onInterval, this);
  },
  
  
  members: {
    _mailboxTree : null,
    folders : new Array(),
    sessionId : null,
    _service : null,
    _timer : null,
    _numberOfUnreadMails : 0,
    
    
    show : function() {
    	var userData = this._service.userData(this.sessionId);
    	
		_mailboxTree = new qx.ui.tree.Tree(userData.email, "/icon/Nuvola/16/places/user-home.png", "/icon/Nuvola/16/places/user-home.png");
		_mailboxTree.set({
			    backgroundColor : "silver",
		    	border          : "inset-thin",
		        overflow        : "scrollY",
		        height          : "100%",
		        width           : 200,
		        paddingLeft     : 4,
		        paddingTop      : 4
		});
		_mailboxTree.addToDocument();
		
		this._timer.start();
    }, 
    
    refresh : function() {

        try {
            this.debug("refreshing mailbox tree");
			var folderList = this._service.folders(this.sessionId);
	    	_mailboxTree.removeAll();
	    	
	    	var unreads = 0;
	    	for (var i = 0; i < folderList.length; i++) {
	    		var folderInfo = folderList[i];
	
				var unreadMails = folderInfo.size - folderInfo.read;			
				unreads += unreadMails;
				var mailFolder = new qx.ui.tree.TreeFolder(folderInfo.name + "(" + folderInfo.size + "/" + (unreadMails) + ")");
				mailFolder.addEventListener("dblclick", this._onDblClickFolder, this);
			    _mailboxTree.add(mailFolder);	
			    
	    	}
	    	
	    	if ((unreads > 0) && (unreads > this._numberOfUnreadMails)) {
			 	this._playYouHaveGotMail();
	    	}
	    	
			this._numberOfUnreadMails = unreads
	    	
		} catch (e) {
			alert(e.message);
		}
    },
    

    
    _onDblClickFolder : function(event) {
    	var target = event.getTarget();
    	var name = target.getLabel();
		this.debug("got dbclick");
		this._reloadLoderMails();
    },
    
    
    _reloadLoderMails : function(foldername) {
		this.debug("reload mails meta info of folder " + foldername);
		var folderMails = this._service.folderMails(this.sessionId, foldername);
    },
    
 
	_onInterval : function() {
   		this.refresh();
   	},
   	
   	
   	_playYouHaveGotMail : function() {
		sound2Embed = document.createElement("embed");
        sound2Embed.setAttribute("src", "YouHaveGotMail.wav");
        sound2Embed.setAttribute("hidden", true);
        sound2Embed.setAttribute("autostart", true);
        document.body.appendChild(sound2Embed);
	}
  }
});  