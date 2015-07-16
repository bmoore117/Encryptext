package bmoore.encryptext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

public class SendingStatus extends BroadcastReceiver
{
    private static final String TAG = "SENDING STATUS";

	@Override
	public void onReceive(Context context, Intent intent)
	{
        //change to app calls

        if(intent == null)
        {
            Log.v(TAG, "No data provided");
            return;
        }

        int result = getResultCode();
		
		if(result == Activity.RESULT_OK)
		{
            int pos = intent.getIntExtra("p", -1);
            if(pos == -1)
            {
                Log.v(TAG, "Could not retrieve position to confirm");
                return;
            }

            String number = intent.getStringExtra("a");

            if(number == null)
            {
                Log.v(TAG, "Could not retrieve number to confirm");
                return;
            }

            Intent in = new Intent(context, SenderSvc.class);
            in.putExtra("p", pos);
            in.putExtra("a", number);

            context.startService(in);
        }
        else
        {
            Intent in = new Intent(context, Conversation.class);
            in.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("s", false);

			if(result == SmsManager.RESULT_ERROR_GENERIC_FAILURE)
				in.putExtra("e", "Sending failed");
			else if(result == SmsManager.RESULT_ERROR_NO_SERVICE)
				in.putExtra("e", "No service");
			else if(result == SmsManager.RESULT_ERROR_NULL_PDU)
				in.putExtra("e", "Empty message");
			else if(result == SmsManager.RESULT_ERROR_RADIO_OFF)
				in.putExtra("e", "Radio off");

            context.startActivity(in);
		}
	}
}
