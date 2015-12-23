package bmoore.encryptext.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.services.ReceiverSvc;

public class Receiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent intent) {
        Bundle b = intent.getExtras();
        if (b != null) {
            Intent localIntent = new Intent(c, ReceiverSvc.class);
            localIntent.putExtra(EncrypText.PDUS, b);
            c.startService(localIntent);
        }
    }
}