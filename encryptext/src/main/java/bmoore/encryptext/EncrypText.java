package bmoore.encryptext;

import java.util.GregorianCalendar;
import java.util.Date;
import android.app.Application;
import android.content.Context;
import android.telephony.TelephonyManager;


/**
 * Here I will keep the to-do's for the whole app.
 *
 * Threading: attach finishedtexts arraylist to notification, so app can exit, or otherwise engineer
 * ability for service to exit while only a notification is up. Also re-engineer the closing of the
 * service in Main, so that the service is not indiscriminately killed.
 *
 * Preferred arch: hold arraylist of finishedtexts for notification, then if nothing else and
 * notification deleted, bump thread for exit check. When Main exits, bump thread as well.
 *
 *
 * Create fixed length error markers for file, so we can tell what kind of error occurred, if
 * necessary, and correct by offering tap-to-resend
 *
 *
 */

public class EncrypText extends Application
{
	private Files manager;
    private Cryptor cryptor;
    private GregorianCalendar cal;
	private String phoneNumber;


    public static final String THREAD_POSITION = "p";
    public static final String THREAD_ITEM = "i";
    public static final String KEY = "k";
    public static final String ERROR = "e";
    public static final String TIME = "t";
    public static final String ADDRESS = "a";
    public static final String NAME = "n";



    @Override
	public void onCreate()
	{
		super.onCreate();

		PRNGFixes.apply();

        TelephonyManager mgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneNumber = formatNumber(mgr.getLine1Number());

		manager = new Files(this, phoneNumber);
        cryptor = new Cryptor(manager);
        cryptor.initPublicCrypto();
        cal = new GregorianCalendar();
	}
	
	private String formatNumber(String number)
	{
		String result = number.replace("(", "").replace(")", "").replace(" ", "").replace("-", "");
		if (!result.contains("+"))
			result = "+" + result;
		return result;
	}

    GregorianCalendar getCal()
    {
        cal.setTime(new Date());
        return cal;
    }
	
	Files getFileManager()
	{
		return manager;
	}

    Cryptor getCryptor() { return cryptor; }

    String getPhoneNumber() { return phoneNumber; }

}
