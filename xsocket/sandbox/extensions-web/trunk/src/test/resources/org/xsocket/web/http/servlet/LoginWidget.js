

qx.Class.define("thinmail.LoginWidget", {
	extend : qx.ui.layout.GridLayout,


	construct : function(serviceClient, callback) {
		this.base(arguments);


		this.setDimension("auto", "auto");
		this.setColumnCount(2);
		this.setRowCount(3);
		this.setVerticalSpacing(4);
		this.setHorizontalSpacing(6);
		this.setColumnWidth(0, 70);
		this.setColumnWidth(1, 180);
		
		this.setColumnHorizontalAlignment(0, "right");
		this.setRowHeight(0, 22);
		this.setRowHeight(1, 22);
		this.setRowHeight(2, 22);

		var usernameLabel = new qx.ui.basic.Label('  username');
		usernameLabel.setWidth(80);
		this.add(usernameLabel, 0, 0);
	
		var usernameField = new qx.ui.form.TextField('testgeblubber');

		usernameField.setWidth(200);
		this.add(usernameField, 1, 0);

		var passwordLabel = new qx.ui.basic.Label('  password');

		passwordLabel.setWidth(80);
		this.add(passwordLabel, 0, 1);
	
		var passwordField = new qx.ui.form.PasswordField('');
		passwordField.setWidth(200);
		this.add(passwordField, 1, 1);


		var button1 = new qx.ui.form.Button("Login", "./button.png");
		this.add(button1, 0, 2);

     	// Add an event listener
		button1.addEventListener("execute", function(e) {
			serviceClient.login(usernameField.getValue(), passwordField.getValue(), callback);
      });
	}
});
  