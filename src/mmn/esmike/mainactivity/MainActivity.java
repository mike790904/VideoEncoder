package mmn.esmike.mainactivity;

import mmn.esmike.videoencoder.R;
import mmn.esmike.videoencoder.VideoEncoder;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.widget.Toast;

public class MainActivity extends Activity {
	Intent myserviceintent;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		myserviceintent = new Intent(MainActivity.this,VideoEncoder.class);
		startService(myserviceintent);
		Toast.makeText(this.getApplicationContext(),"Encode Started", Toast.LENGTH_LONG).show();
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
