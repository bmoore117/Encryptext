package bmoore.encryptext.receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.ui.ConversationActivity;
import bmoore.encryptext.services.SenderSvc;

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
            int pos = intent.getIntExtra(EncrypText.THREAD_POSITION, -1);
            if(pos == -1)
            {
                Log.v(TAG, "Could not retrieve position to confirm");
                return;
            }

            String number = intent.getStringExtra(EncrypText.ADDRESS);

            if(number == null)
            {
                Log.v(TAG, "Could not retrieve number to confirm");
                return;
            }

            Intent in = new Intent(context, SenderSvc.class);
            in.putExtra(EncrypText.THREAD_POSITION, pos);
            in.putExtra(EncrypText.ADDRESS, number);

            context.startService(in);
        }
        else
        {
            Intent in = new Intent(context, ConversationActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("s", false);

			if(result == SmsManager.RESULT_ERROR_GENERIC_FAILURE) {
                Log.e(TAG, "Sending failed");
                Toast.makeText(context, "Sending failed", Toast.LENGTH_SHORT).show();
            }
			else if(result == SmsManager.RESULT_ERROR_NO_SERVICE) {
                Log.e(TAG, "No service");
                Toast.makeText(context, "No service", Toast.LENGTH_SHORT).show();
            }
			else if(result == SmsManager.RESULT_ERROR_NULL_PDU) {
                Log.e(TAG, "Empty message");
                Toast.makeText(context, "Empty message", Toast.LENGTH_SHORT).show();
            }
			else if(result == SmsManager.RESULT_ERROR_RADIO_OFF) {
                Log.e(TAG, "Radio off");
                Toast.makeText(context, "Radio off", Toast.LENGTH_SHORT).show();
            }
		}
	}
}
