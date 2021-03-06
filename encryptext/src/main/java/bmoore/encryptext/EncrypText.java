package bmoore.encryptext;

import android.app.Application;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.NoSuchPaddingException;

import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.utils.InvalidKeyTypeException;
import bmoore.encryptext.utils.PRNGFixes;


/**
 * Here I will keep the to-do's for the whole app.
 * <p/>
 * Threading: attach finishedtexts arraylist to notification, so app can exit, or otherwise engineer
 * ability for service to exit while only a notification is up. Also re-engineer the closing of the
 * service in HomeActivity, so that the service is not indiscriminately killed.
 * <p/>
 * Preferred arch: hold arraylist of finishedtexts for notification, then if nothing else and
 * notification deleted, bump thread for exit check. When HomeActivity exits, bump thread as well.
 * <p/>
 * <p/>
 * Create fixed length error markers for file, so we can tell what kind of error occurred, if
 * necessary, and correct by offering tap-to-resend
 */

public class EncrypText extends Application {
    //private Files manager;
    private Cryptor cryptor;
    private String isoCountryCode;
    private PhoneNumberUtil phoneNumberUtil;
    private DBUtils dbUtils;

    public static final String THREAD_POSITION = "p";
    public static final String THREAD_ITEM = "i";
    public static final String MULTIPLE_THREAD_ITEMS = "is";
    public static final String KEY = "k";
    public static final String ERROR = "e";
    public static final String TIME = "t";
    public static final String ADDRESS = "a";
    public static final String NAME = "n";
    public static final String QUIT_FLAG = "q";
    public static final String PDUS = "pdus";
    public static final String FORMAT = "format";
    public static final String FLAGS = "f";
    public static final String DATE = "d";
    public static final int FLAG_REMOVE_PUBLIC_KEY = 117;
    public static final int FLAG_GENERATE_SECRET_KEY = 1138;
    public static final int FLAG_UPDATE_KEY_REQUESTS_ICON = 45;

    public static final int PUBLIC_KEY_PDU = 1;
    public static final int AES_ENCRYPTED_PDU = 2;

    public static final String UNRECOGNIZED_NUMBER_FORMAT = "Unrecognized format";
    public static final String USE_HEADS_UP_NOTIFICATIONS = "useHeadsUpNotifications";

    private static final String TAG = "EncrypText";


    @Override
    public void onCreate() {
        super.onCreate();

        PRNGFixes.apply();

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        isoCountryCode = tm.getSimCountryIso();

        phoneNumberUtil = PhoneNumberUtil
                .getInstance();

        //manager = new Files(this, phoneNumber);
        dbUtils = new DBUtils(this);

        try {
            cryptor = new Cryptor(dbUtils);
            cryptor.initPublicCrypto();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeyTypeException | InvalidKeySpecException e) {
            Log.e(TAG, "Error initializing public key cryptography", e);
            Toast.makeText(this, "Error initializing public key cryptography", Toast.LENGTH_SHORT).show();
        }
    }

	/*private String formatNumber(String number)
	{
		String result = number.replace("(", "").replace(")", "").replace(" ", "").replace("-", "");
		if (!result.contains("+"))
			result = "+" + result;
		return result;
	}*/
	
	/*public Files getFileManager()
	{
		return manager;
	}*/

    public Cryptor getCryptor() {
        return cryptor;
    }

    public DBUtils getDbUtils() {
        return dbUtils;
    }

    public PhoneNumberUtil getPhoneNumberUtil() {
        return phoneNumberUtil;
    }

    public String getIsoCountryCode() {
        return isoCountryCode.toUpperCase();
    }
}
