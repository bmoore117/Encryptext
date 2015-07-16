package bmoore.encryptext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class Receiver extends BroadcastReceiver
{
	public void onReceive(Context c, Intent intent)
	{
		Bundle b = intent.getExtras();
		if (b != null)
		{
			Intent localIntent = new Intent(c, ReceiverSvc.class);
			localIntent.putExtra("pdus", b);
			c.startService(localIntent);
		}
	}
}